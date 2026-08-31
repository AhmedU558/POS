package com.pos.sales;

import com.pos.AbstractIntegrationTest;
import com.pos.auth.security.JwtTokenProvider;
import com.pos.catalog.entity.Product;
import com.pos.catalog.repository.ProductRepository;
import com.pos.inventory.domain.InventoryBalance;
import com.pos.inventory.repository.InventoryBalanceRepository;
import com.pos.organization.domain.Register;
import com.pos.organization.domain.Store;
import com.pos.organization.domain.Terminal;
import com.pos.organization.repository.RegisterRepository;
import com.pos.organization.repository.StoreRepository;
import com.pos.organization.repository.TerminalRepository;
import com.pos.users.domain.Role;
import com.pos.users.domain.RoleName;
import com.pos.users.domain.User;
import com.pos.users.repository.RoleRepository;
import com.pos.users.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class SaleReceiptApiIntegrationTests extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private StoreRepository storeRepository;
    @Autowired private TerminalRepository terminalRepository;
    @Autowired private RegisterRepository registerRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private InventoryBalanceRepository balanceRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private EntityManager entityManager;

    private String cashierToken;
    private Store store;
    private Terminal terminal;
    private Register register;
    private Product product;
    private UUID sessionId;
    private UUID cashMethodId;

    @BeforeEach
    void setUp() {
        store = storeRepository.save(new Store("REC-" + idSuffix(), "Receipt Store", "PKR", "UTC"));
        terminal = terminalRepository.save(new Terminal(store, "T1", "Front Counter", "ACTIVE"));
        register = registerRepository.save(new Register(store, terminal, "R1", "Register 1", "ACTIVE"));
        product = new Product();
        product.setSku("REC-SKU-" + idSuffix());
        product.setName("Receipt Product");
        product.setPurchasePrice(BigDecimal.TEN);
        product.setSellingPrice(new BigDecimal("100.0000"));
        product.setTaxRate(new BigDecimal("0.0000"));
        product.setMinStock(BigDecimal.ZERO);
        product.setTrackBatch(false);
        product.setTrackExpiry(false);
        product.setActive(true);
        product = productRepository.save(product);
        InventoryBalance balance = new InventoryBalance(product, store);
        balance.addQuantity(new BigDecimal("10"));
        balanceRepository.save(balance);

        User cashier = user("receipt.cash", RoleName.CASHIER);
        cashier.assignStore(store);
        cashier = userRepository.save(cashier);
        cashierToken = "Bearer " + jwtTokenProvider.generateToken(cashier.getUsername());

        entityManager.flush();

        sessionId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO register_sessions (id, register_id, cashier_id, opened_at, status) VALUES (?, ?, ?, NOW(), 'OPEN')",
                sessionId,
                register.getId(),
                cashier.getId());
        cashMethodId = methodId("CASH");
    }

    @Test
    void receiptEndpointReturnsDedicatedReceiptFieldsAndNotConfiguredFbrStatus() throws Exception {
        String saleId = mockMvc.perform(post("/api/v1/sales")
                        .header("Authorization", cashierToken)
                        .header("Idempotency-Key", "receipt-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(saleBody()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString()
                .split("\"id\":\"")[1]
                .split("\"")[0];

        mockMvc.perform(get("/api/v1/sales/{id}/receipt", saleId).header("Authorization", cashierToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.storeName").value("Receipt Store"))
                .andExpect(jsonPath("$.data.terminalName").value("Front Counter"))
                .andExpect(jsonPath("$.data.tenderedAmount").value(100.0000))
                .andExpect(jsonPath("$.data.changeAmount").value(0.0000))
                .andExpect(jsonPath("$.data.fbrStatus").value("NOT_CONFIGURED"))
                .andExpect(jsonPath("$.data.fbrStatusLabel").value("Not configured"))
                .andExpect(jsonPath("$.data.fbrInvoiceNumber").doesNotExist())
                .andExpect(jsonPath("$.data.fbrQrCode").doesNotExist());
    }

    private String saleBody() {
        return """
                {
                    "storeId": "%s",
                    "terminalId": "%s",
                    "registerId": "%s",
                    "registerSessionId": "%s",
                    "items": [{"productId": "%s", "quantity": 1}],
                    "payments": [{"paymentMethodId": "%s", "amount": 100.0000}]
                }
                """.formatted(store.getId(), terminal.getId(), register.getId(), sessionId, product.getId(), cashMethodId);
    }

    private UUID methodId(String code) {
        return jdbcTemplate.queryForObject("SELECT id FROM payment_methods WHERE code = ?", UUID.class, code);
    }

    private User user(String username, String roleName) {
        User created = new User(username, passwordEncoder.encode("Sale123456!"), "Receipt", "User");
        created.setEmail(username + "@test.com");
        Role role = roleRepository.findByName(roleName).orElseThrow();
        created.assignRole(role);
        return created;
    }

    private static String idSuffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}

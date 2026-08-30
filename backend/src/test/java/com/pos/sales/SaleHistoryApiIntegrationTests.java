package com.pos.sales;

import com.pos.AbstractIntegrationTest;
import com.pos.auth.security.JwtTokenProvider;
import com.pos.catalog.entity.Product;
import com.pos.catalog.repository.ProductRepository;
import com.pos.customers.domain.Customer;
import com.pos.customers.repository.CustomerRepository;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class SaleHistoryApiIntegrationTests extends AbstractIntegrationTest {

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
    @Autowired private CustomerRepository customerRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private EntityManager entityManager;

    private String cashierToken;
    private String accountantToken;
    private Store store;
    private Terminal terminal;
    private Register register;
    private Product product;
    private Customer customer;
    private UUID sessionId;
    private UUID cashMethodId;

    @BeforeEach
    void setUp() {
        store = storeRepository.save(new Store("HIST-" + idSuffix(), "Hist Store", "USD", "UTC"));
        terminal = terminalRepository.save(new Terminal(store, "T1", "Terminal 1", "ACTIVE"));
        register = registerRepository.save(new Register(store, terminal, "R1", "Register 1", "ACTIVE"));
        product = new Product();
        product.setSku("HIST-SKU-" + idSuffix());
        product.setName("Hist Product");
        product.setPurchasePrice(BigDecimal.TEN);
        product.setSellingPrice(new BigDecimal("20.0000"));
        product.setTaxRate(new BigDecimal("0.1000"));
        product.setMinStock(BigDecimal.ZERO);
        product.setTrackBatch(false);
        product.setTrackExpiry(false);
        product.setActive(true);
        product = productRepository.save(product);
        InventoryBalance balance = new InventoryBalance(product, store);
        balance.addQuantity(new BigDecimal("10"));
        balanceRepository.save(balance);

        customer = new Customer();
        customer.setCustomerCode("HIST-C-" + idSuffix());
        customer.setName("History Buyer");
        customer.setCreditLimit(BigDecimal.ZERO);
        customer.setActive(true);
        customer = customerRepository.save(customer);

        User cashier = user("hist.cash", RoleName.CASHIER);
        cashier.assignStore(store);
        cashier = userRepository.save(cashier);
        cashierToken = "Bearer " + jwtTokenProvider.generateToken(cashier.getUsername());
        accountantToken = bearer("hist.acct", RoleName.ACCOUNTANT);

        sessionId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO register_sessions (id, register_id, cashier_id, opened_at, status) VALUES (?, ?, ?, NOW(), 'OPEN')",
                sessionId,
                register.getId(),
                cashier.getId());
        cashMethodId = jdbcTemplate.queryForObject(
                "SELECT id FROM payment_methods WHERE code = 'CASH'",
                UUID.class);
    }

    @Test
    void listsReceiptAndReprint() throws Exception {
        String saleId = completeSale();

        mockMvc.perform(get("/api/v1/sales").header("Authorization", cashierToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].id").value(saleId))
                .andExpect(jsonPath("$.data.content[0].receiptNumber").exists());

        mockMvc.perform(get("/api/v1/sales").param("query", "R-").header("Authorization", cashierToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1));

        mockMvc.perform(get("/api/v1/sales/" + saleId + "/receipt").header("Authorization", cashierToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.saleId").value(saleId))
                .andExpect(jsonPath("$.data.receiptNumber").exists())
                .andExpect(jsonPath("$.data.storeName").value("Hist Store"))
                .andExpect(jsonPath("$.data.grandTotal").value(22.0000));

        mockMvc.perform(post("/api/v1/sales/" + saleId + "/receipt/reprint").header("Authorization", cashierToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.saleId").value(saleId));

        entityManager.flush();
        Map<String, Object> audit = jdbcTemplate.queryForMap(
                "SELECT action, entity_type FROM audit_logs WHERE action = 'RECEIPT_REPRINTED'");
        assertThat(audit.get("entity_type")).isEqualTo("Sale");

        mockMvc.perform(get("/api/v1/customers/" + customer.getId() + "/sales").header("Authorization", cashierToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].id").value(saleId));
    }

    @Test
    void accountantCannotReadReceipts() throws Exception {
        mockMvc.perform(get("/api/v1/sales").header("Authorization", accountantToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/sales/" + UUID.randomUUID() + "/receipt").header("Authorization", accountantToken))
                .andExpect(status().isForbidden());
    }

    private String completeSale() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/sales")
                        .header("Authorization", cashierToken)
                        .header("Idempotency-Key", "hist-sale-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "storeId": "%s",
                                    "terminalId": "%s",
                                    "registerId": "%s",
                                    "registerSessionId": "%s",
                                    "customerId": "%s",
                                    "items": [{"productId": "%s", "quantity": 1}],
                                    "payments": [{"paymentMethodId": "%s", "amount": 22.0000}]
                                }
                                """.formatted(
                                store.getId(),
                                terminal.getId(),
                                register.getId(),
                                sessionId,
                                customer.getId(),
                                product.getId(),
                                cashMethodId)))
                .andExpect(status().isOk())
                .andReturn();
        return created.getResponse().getContentAsString().split("\"id\":\"")[1].split("\"")[0];
    }

    private User user(String username, String roleName) {
        User created = new User(username, passwordEncoder.encode("Sale123456!"), "Sale", "User");
        created.setEmail(username + "@test.com");
        Role role = roleRepository.findByName(roleName).orElseThrow();
        created.assignRole(role);
        return created;
    }

    private String bearer(String username, String roleName) {
        if (userRepository.findByUsername(username).isEmpty()) {
            userRepository.save(user(username, roleName));
        }
        return "Bearer " + jwtTokenProvider.generateToken(username);
    }

    private static String idSuffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}

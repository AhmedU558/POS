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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import jakarta.persistence.EntityManager;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class SaleHoldApiIntegrationTests extends AbstractIntegrationTest {

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
        store = storeRepository.save(new Store("HOLD-" + idSuffix(), "Hold Store", "USD", "UTC"));
        terminal = terminalRepository.save(new Terminal(store, "T1", "Terminal 1", "ACTIVE"));
        register = registerRepository.save(new Register(store, terminal, "R1", "Register 1", "ACTIVE"));
        product = new Product();
        product.setSku("HOLD-SKU-" + idSuffix());
        product.setName("Hold Product");
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

        User cashier = user("hold.cash", RoleName.CASHIER);
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
        cashMethodId = jdbcTemplate.queryForObject(
                "SELECT id FROM payment_methods WHERE code = 'CASH'",
                UUID.class);
    }

    @Test
    void holdDoesNotDeductAndResumeCompletes() throws Exception {
        MvcResult held = mockMvc.perform(post("/api/v1/sales")
                        .header("Authorization", cashierToken)
                        .header("Idempotency-Key", "hold-key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(saleBody("[]")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("HELD"))
                .andReturn();
        String saleId = held.getResponse().getContentAsString().split("\"id\":\"")[1].split("\"")[0];

        assertThat(jdbcTemplate.queryForObject(
                "SELECT quantity FROM inventory_balances WHERE product_id = CAST(? AS uuid) AND store_id = CAST(? AS uuid)",
                BigDecimal.class,
                product.getId(),
                store.getId())).isEqualByComparingTo("10.0000");

        mockMvc.perform(post("/api/v1/sales/" + saleId + "/hold").header("Authorization", cashierToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("HELD"));

        mockMvc.perform(post("/api/v1/sales/" + saleId + "/resume")
                        .header("Authorization", cashierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"registerSessionId": "%s", "payments": [{"paymentMethodId": "%s", "amount": 22.0000}]}
                                """.formatted(sessionId, cashMethodId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.grandTotal").value(22.0000));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT quantity FROM inventory_balances WHERE product_id = CAST(? AS uuid) AND store_id = CAST(? AS uuid)",
                BigDecimal.class,
                product.getId(),
                store.getId())).isEqualByComparingTo("9.0000");
    }

    @Test
    void completedSaleCannotBeHeld() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/sales")
                        .header("Authorization", cashierToken)
                        .header("Idempotency-Key", "hold-key-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(saleBody("""
                                [{"paymentMethodId": "%s", "amount": 22.0000}]
                                """.formatted(cashMethodId))))
                .andExpect(status().isOk())
                .andReturn();
        String saleId = created.getResponse().getContentAsString().split("\"id\":\"")[1].split("\"")[0];

        mockMvc.perform(post("/api/v1/sales/" + saleId + "/hold").header("Authorization", cashierToken))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("BUSINESS_RULE_VIOLATION"));
    }

    private String saleBody(String paymentsJson) {
        return """
                {
                    "storeId": "%s",
                    "terminalId": "%s",
                    "registerId": "%s",
                    "registerSessionId": "%s",
                    "items": [{"productId": "%s", "quantity": 1}],
                    "payments": %s
                }
                """.formatted(store.getId(), terminal.getId(), register.getId(), sessionId, product.getId(), paymentsJson);
    }

    private User user(String username, String roleName) {
        User created = new User(username, passwordEncoder.encode("Sale123456!"), "Sale", "User");
        created.setEmail(username + "@test.com");
        Role role = roleRepository.findByName(roleName).orElseThrow();
        created.assignRole(role);
        return created;
    }

    private static String idSuffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}

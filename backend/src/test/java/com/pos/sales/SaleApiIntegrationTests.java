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
class SaleApiIntegrationTests extends AbstractIntegrationTest {

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
    private String accountantToken;
    private User cashier;
    private Store store;
    private Terminal terminal;
    private Register register;
    private Product product;
    private UUID sessionId;
    private UUID cashMethodId;

    @BeforeEach
    void setUp() {
        store = storeRepository.save(new Store("POS-" + idSuffix(), "POS Store", "USD", "UTC"));
        terminal = terminalRepository.save(new Terminal(store, "T1", "Terminal 1", "ACTIVE"));
        register = registerRepository.save(new Register(store, terminal, "R1", "Register 1", "ACTIVE"));
        product = new Product();
        product.setSku("POS-SKU-" + idSuffix());
        product.setName("POS Product");
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

        cashier = user("sale.cash", RoleName.CASHIER);
        cashier.assignStore(store);
        cashier = userRepository.save(cashier);
        cashierToken = "Bearer " + jwtTokenProvider.generateToken(cashier.getUsername());
        accountantToken = bearer("sale.acct", RoleName.ACCOUNTANT);

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
    void completeGetAndIdempotentRetry() throws Exception {
        String body = saleBody(product.getId(), "2", "44.0000");
        MvcResult created = mockMvc.perform(post("/api/v1/sales")
                        .header("Authorization", cashierToken)
                        .header("Idempotency-Key", "sale-key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.subtotal").value(40.0000))
                .andExpect(jsonPath("$.data.discountTotal").value(0))
                .andExpect(jsonPath("$.data.taxTotal").value(4.0000))
                .andExpect(jsonPath("$.data.grandTotal").value(44.0000))
                .andExpect(jsonPath("$.data.payments[0].paymentMethod").value("CASH"))
                .andReturn();
        String saleId = created.getResponse().getContentAsString().split("\"id\":\"")[1].split("\"")[0];

        mockMvc.perform(get("/api/v1/sales/" + saleId).header("Authorization", cashierToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(saleId))
                .andExpect(jsonPath("$.data.receiptNumber").exists());

        mockMvc.perform(post("/api/v1/sales")
                        .header("Authorization", cashierToken)
                        .header("Idempotency-Key", "sale-key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(saleId));

        entityManager.flush();
        Map<String, Object> audit = jdbcTemplate.queryForMap(
                "SELECT action, entity_type FROM audit_logs WHERE action = 'SALE_CREATED'");
        assertThat(audit.get("entity_type")).isEqualTo("Sale");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM cash_transactions WHERE reference_id = CAST(? AS uuid)",
                Long.class,
                UUID.fromString(saleId))).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT quantity FROM inventory_balances WHERE product_id = CAST(? AS uuid) AND store_id = CAST(? AS uuid)",
                BigDecimal.class,
                product.getId(),
                store.getId())).isEqualByComparingTo("8.0000");
    }

    @Test
    void reusedKeyWithDifferentBodyIsConflict() throws Exception {
        mockMvc.perform(post("/api/v1/sales")
                        .header("Authorization", cashierToken)
                        .header("Idempotency-Key", "sale-key-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(saleBody(product.getId(), "1", "22.0000")))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/sales")
                        .header("Authorization", cashierToken)
                        .header("Idempotency-Key", "sale-key-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(saleBody(product.getId(), "2", "44.0000")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("DUPLICATE_REQUEST"));
    }

    @Test
    void missingSessionIsConflict() throws Exception {
        mockMvc.perform(post("/api/v1/sales")
                        .header("Authorization", cashierToken)
                        .header("Idempotency-Key", "sale-key-3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(saleBody(product.getId(), "1", "22.0000")
                                .replace(sessionId.toString(), UUID.randomUUID().toString())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("REGISTER_SESSION_REQUIRED"));
    }

    @Test
    void insufficientStockIsConflict() throws Exception {
        mockMvc.perform(post("/api/v1/sales")
                        .header("Authorization", cashierToken)
                        .header("Idempotency-Key", "sale-key-4")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(saleBody(product.getId(), "20", "440.0000")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("INSUFFICIENT_STOCK"));
    }

    @Test
    void missingIdempotencyKeyIsValidationError() throws Exception {
        mockMvc.perform(post("/api/v1/sales")
                        .header("Authorization", cashierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(saleBody(product.getId(), "1", "22.0000")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void accountantIsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/sales/" + UUID.randomUUID()).header("Authorization", accountantToken))
                .andExpect(status().isForbidden());
    }

    private String saleBody(UUID productId, String qty, String amount) {
        return """
                {
                    "storeId": "%s",
                    "terminalId": "%s",
                    "registerId": "%s",
                    "registerSessionId": "%s",
                    "items": [{"productId": "%s", "quantity": %s}],
                    "payments": [{"paymentMethodId": "%s", "amount": %s}]
                }
                """.formatted(store.getId(), terminal.getId(), register.getId(), sessionId, productId, qty, cashMethodId, amount);
    }

    private User user(String username, String roleName) {
        User user = new User(username, passwordEncoder.encode("Sale123456!"), "Sale", "User");
        user.setEmail(username + "@test.com");
        Role role = roleRepository.findByName(roleName).orElseThrow();
        user.assignRole(role);
        return user;
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

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class SalePaymentApiIntegrationTests extends AbstractIntegrationTest {

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
    private UUID sessionId;
    private UUID cashMethodId;
    private UUID cardMethodId;
    private UUID creditMethodId;

    @BeforeEach
    void setUp() {
        store = storeRepository.save(new Store("PAY-" + idSuffix(), "Pay Store", "USD", "UTC"));
        terminal = terminalRepository.save(new Terminal(store, "T1", "Terminal 1", "ACTIVE"));
        register = registerRepository.save(new Register(store, terminal, "R1", "Register 1", "ACTIVE"));
        product = new Product();
        product.setSku("PAY-SKU-" + idSuffix());
        product.setName("Pay Product");
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

        User cashier = user("pay.cash", RoleName.CASHIER);
        cashier.assignStore(store);
        cashier = userRepository.save(cashier);
        cashierToken = "Bearer " + jwtTokenProvider.generateToken(cashier.getUsername());
        accountantToken = bearer("pay.acct", RoleName.ACCOUNTANT);

        sessionId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO register_sessions (id, register_id, cashier_id, opened_at, status) VALUES (?, ?, ?, NOW(), 'OPEN')",
                sessionId,
                register.getId(),
                cashier.getId());
        cashMethodId = methodId("CASH");
        cardMethodId = methodId("CARD");
        creditMethodId = methodId("STORE_CREDIT");
    }

    @Test
    void listsActivePaymentMethods() throws Exception {
        mockMvc.perform(get("/api/v1/payment-methods").header("Authorization", cashierToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.code=='CASH')]").exists())
                .andExpect(jsonPath("$.data[?(@.code=='CARD')]").exists())
                .andExpect(jsonPath("$.data[?(@.code=='STORE_CREDIT')]").exists())
                .andExpect(jsonPath("$.data[?(@.code=='OTHER')]").exists());
    }

    @Test
    void accountantCannotListPaymentMethods() throws Exception {
        mockMvc.perform(get("/api/v1/payment-methods").header("Authorization", accountantToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void splitCashAndCardMustEqualGrandTotal() throws Exception {
        mockMvc.perform(post("/api/v1/sales")
                        .header("Authorization", cashierToken)
                        .header("Idempotency-Key", "pay-split-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(saleBody("""
                                [{"paymentMethodId": "%s", "amount": 20.0000},
                                 {"paymentMethodId": "%s", "amount": 24.0000}]
                                """.formatted(cashMethodId, cardMethodId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.grandTotal").value(44.0000))
                .andExpect(jsonPath("$.data.payments.length()").value(2));

        mockMvc.perform(post("/api/v1/sales")
                        .header("Authorization", cashierToken)
                        .header("Idempotency-Key", "pay-split-bad")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(saleBody("""
                                [{"paymentMethodId": "%s", "amount": 10.0000},
                                 {"paymentMethodId": "%s", "amount": 10.0000}]
                                """.formatted(cashMethodId, cardMethodId))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("BUSINESS_RULE_VIOLATION"));
    }

    @Test
    void storeCreditRedeemsCustomerBalance() throws Exception {
        Customer customer = new Customer();
        customer.setCustomerCode("PAY-C-" + idSuffix());
        customer.setName("Credit Buyer");
        customer.setCreditLimit(BigDecimal.ZERO);
        customer.setActive(true);
        customer = customerRepository.save(customer);
        entityManager.flush();
        jdbcTemplate.update(
                "INSERT INTO customer_credits (id, customer_id, balance, currency_code, status) VALUES (?, ?, ?, 'USD', 'ACTIVE')",
                UUID.randomUUID(),
                customer.getId(),
                new BigDecimal("50.0000"));

        MvcResult created = mockMvc.perform(post("/api/v1/sales")
                        .header("Authorization", cashierToken)
                        .header("Idempotency-Key", "pay-credit-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(saleBody(customer.getId(), """
                                [{"paymentMethodId": "%s", "amount": 44.0000}]
                                """.formatted(creditMethodId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.payments[0].paymentMethod").value("STORE_CREDIT"))
                .andReturn();
        String saleId = created.getResponse().getContentAsString().split("\"id\":\"")[1].split("\"")[0];

        assertThat(jdbcTemplate.queryForObject(
                "SELECT balance FROM customer_credits WHERE customer_id = ?",
                BigDecimal.class,
                customer.getId())).isEqualByComparingTo("6.0000");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM cash_transactions WHERE reference_id = CAST(? AS uuid)",
                Long.class,
                UUID.fromString(saleId))).isEqualTo(0L);
    }

    @Test
    void storeCreditWithoutCustomerIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/sales")
                        .header("Authorization", cashierToken)
                        .header("Idempotency-Key", "pay-credit-none")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(saleBody("""
                                [{"paymentMethodId": "%s", "amount": 44.0000}]
                                """.formatted(creditMethodId))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("BUSINESS_RULE_VIOLATION"));
    }

    private String saleBody(String paymentsJson) {
        return saleBody(null, paymentsJson);
    }

    private String saleBody(UUID customerId, String paymentsJson) {
        String customer = customerId == null ? "" : "\"customerId\": \"" + customerId + "\",";
        return """
                {
                    "storeId": "%s",
                    "terminalId": "%s",
                    "registerId": "%s",
                    "registerSessionId": "%s",
                    %s
                    "items": [{"productId": "%s", "quantity": 2}],
                    "payments": %s
                }
                """.formatted(store.getId(), terminal.getId(), register.getId(), sessionId, customer, product.getId(), paymentsJson);
    }

    private UUID methodId(String code) {
        return jdbcTemplate.queryForObject("SELECT id FROM payment_methods WHERE code = ?", UUID.class, code);
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

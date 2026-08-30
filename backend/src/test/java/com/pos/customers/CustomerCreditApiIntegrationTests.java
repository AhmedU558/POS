package com.pos.customers;

import com.pos.AbstractIntegrationTest;
import com.pos.auth.security.JwtTokenProvider;
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

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class CustomerCreditApiIntegrationTests extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private EntityManager entityManager;

    private String adminToken;
    private String cashierToken;
    private String inventoryToken;

    @BeforeEach
    void setUp() {
        adminToken = bearer("cred.admin", RoleName.SUPER_ADMINISTRATOR);
        cashierToken = bearer("cred.cashier", RoleName.CASHIER);
        inventoryToken = bearer("cred.inv", RoleName.INVENTORY_MANAGER);
    }

    @Test
    void getWithoutAccountReturnsZeroBalance() throws Exception {
        String customerId = createCustomer("CR-0", "Zero Credit");

        mockMvc.perform(get("/api/v1/customers/" + customerId + "/credit")
                        .header("Authorization", cashierToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.customerId").value(customerId))
                .andExpect(jsonPath("$.data.balance").value(0))
                .andExpect(jsonPath("$.data.currencyCode").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.creditLimit").value(25.00))
                .andExpect(jsonPath("$.data.transactions.content.length()").value(0));
    }

    @Test
    void cashierCanIssueRedeemAndSeeLedger() throws Exception {
        String customerId = createCustomer("CR-1", "Ledger Customer");

        mockMvc.perform(post("/api/v1/customers/" + customerId + "/credit/transactions")
                        .header("Authorization", cashierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(txBody("ISSUE", "30.00", "USD")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.balance").value(30.00))
                .andExpect(jsonPath("$.data.currencyCode").value("USD"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.transactions.content.length()").value(1))
                .andExpect(jsonPath("$.data.transactions.content[0].transactionType").value("ISSUE"))
                .andExpect(jsonPath("$.data.transactions.content[0].amount").value(30.00))
                .andExpect(jsonPath("$.data.transactions.content[0].balanceAfter").value(30.00));

        mockMvc.perform(post("/api/v1/customers/" + customerId + "/credit/transactions")
                        .header("Authorization", cashierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(txBody("REDEEM", "10.00", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.balance").value(20.00))
                .andExpect(jsonPath("$.data.transactions.content.length()").value(2))
                .andExpect(jsonPath("$.data.transactions.content[0].transactionType").value("REDEEM"))
                .andExpect(jsonPath("$.data.transactions.content[0].amount").value(-10.00));

        mockMvc.perform(get("/api/v1/customers/" + customerId + "/credit")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.balance").value(20.00))
                .andExpect(jsonPath("$.data.transactions.content.length()").value(2));
    }

    @Test
    void issueWritesAudit() throws Exception {
        String customerId = createCustomer("CR-AUD", "Audit Credit");

        mockMvc.perform(post("/api/v1/customers/" + customerId + "/credit/transactions")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(txBody("ISSUE", "5.00", "USD")))
                .andExpect(status().isOk());

        entityManager.flush();
        Map<String, Object> audit = jdbcTemplate.queryForMap(
                "SELECT action, entity_type FROM audit_logs WHERE action = 'CREDIT_ISSUED'");
        assertThat(audit.get("action")).isEqualTo("CREDIT_ISSUED");
        assertThat(audit.get("entity_type")).isEqualTo("CustomerCreditTransaction");
    }

    @Test
    void redeemBeyondBalanceIsBusinessRuleViolation() throws Exception {
        String customerId = createCustomer("CR-NEG", "Overdraft");

        mockMvc.perform(post("/api/v1/customers/" + customerId + "/credit/transactions")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(txBody("ISSUE", "5.00", "USD")))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/customers/" + customerId + "/credit/transactions")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(txBody("REDEEM", "6.00", null)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("BUSINESS_RULE_VIOLATION"));
    }

    @Test
    void firstWriteWithoutCurrencyIsValidationError() throws Exception {
        String customerId = createCustomer("CR-CUR", "Currency Required");

        mockMvc.perform(post("/api/v1/customers/" + customerId + "/credit/transactions")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(txBody("ISSUE", "5.00", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void currencyMismatchIsConflict() throws Exception {
        String customerId = createCustomer("CR-FX", "Fx Customer");

        mockMvc.perform(post("/api/v1/customers/" + customerId + "/credit/transactions")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(txBody("ISSUE", "5.00", "USD")))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/customers/" + customerId + "/credit/transactions")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(txBody("ISSUE", "1.00", "PKR")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("CONFLICT"));
    }

    @Test
    void adjustCanIncreaseAndDecrease() throws Exception {
        String customerId = createCustomer("CR-ADJ", "Adjust Customer");

        mockMvc.perform(post("/api/v1/customers/" + customerId + "/credit/transactions")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(txBody("ADJUST", "8.00", "USD")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.balance").value(8.00));

        mockMvc.perform(post("/api/v1/customers/" + customerId + "/credit/transactions")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(txBody("ADJUST", "-3.00", "USD")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.balance").value(5.00));
    }

    @Test
    void inventoryManagerWithoutCreditPermissionIsForbidden() throws Exception {
        String customerId = createCustomer("CR-FORB", "Forbidden Credit");

        mockMvc.perform(get("/api/v1/customers/" + customerId + "/credit")
                        .header("Authorization", inventoryToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/customers/" + customerId + "/credit/transactions")
                        .header("Authorization", inventoryToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(txBody("ISSUE", "1.00", "USD")))
                .andExpect(status().isForbidden());
    }

    @Test
    void unknownCustomerIsNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/customers/" + UUID.randomUUID() + "/credit")
                        .header("Authorization", adminToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void inactiveCustomerCannotReceiveCredit() throws Exception {
        String customerId = createCustomer("CR-INACT", "Inactive Credit");
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch(
                                "/api/v1/customers/" + customerId)
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "customerCode": "CR-INACT",
                                    "name": "Inactive Credit",
                                    "creditLimit": 25.00,
                                    "isActive": false
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/customers/" + customerId + "/credit/transactions")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(txBody("ISSUE", "1.00", "USD")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("RESOURCE_INACTIVE"));
    }

    @Test
    void ledgerRowsAreImmutable() throws Exception {
        String customerId = createCustomer("CR-IMM", "Immutable");
        mockMvc.perform(post("/api/v1/customers/" + customerId + "/credit/transactions")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(txBody("ISSUE", "4.00", "USD")))
                .andExpect(status().isOk());

        entityManager.flush();
        UUID txId = jdbcTemplate.queryForObject(
                "SELECT id FROM customer_credit_transactions LIMIT 1", UUID.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                        jdbcTemplate.update(
                                "UPDATE customer_credit_transactions SET amount = 99 WHERE id = ?",
                                txId))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
    }

    @Test
    void issueAboveCreditLimitIsAllowed() throws Exception {
        String customerId = createCustomer("CR-LIM", "Limit Display Only");

        mockMvc.perform(post("/api/v1/customers/" + customerId + "/credit/transactions")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(txBody("ISSUE", "100.00", "USD")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.balance").value(100.00))
                .andExpect(jsonPath("$.data.creditLimit").value(25.00));
    }

    private String createCustomer(String code, String name) throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/customers")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "customerCode": "%s",
                                    "name": "%s",
                                    "creditLimit": 25.00,
                                    "isActive": true
                                }
                                """.formatted(code, name)))
                .andExpect(status().isOk())
                .andReturn();
        return created.getResponse().getContentAsString().split("\"id\":\"")[1].split("\"")[0];
    }

    private String bearer(String username, String roleName) {
        if (userRepository.findByUsername(username).isEmpty()) {
            User user = new User(username, passwordEncoder.encode("Cred123!"), "Cred", "User");
            user.setEmail(username + "@test.com");
            Role role = roleRepository.findByName(roleName).orElseThrow();
            user.assignRole(role);
            userRepository.save(user);
        }
        return "Bearer " + jwtTokenProvider.generateToken(username);
    }

    private static String txBody(String type, String amount, String currency) {
        String currencyJson = currency == null ? "null" : "\"" + currency + "\"";
        return """
                {
                    "transactionType": "%s",
                    "amount": %s,
                    "currencyCode": %s
                }
                """.formatted(type, amount, currencyJson);
    }
}

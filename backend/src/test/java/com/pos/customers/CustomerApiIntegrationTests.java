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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class CustomerApiIntegrationTests extends AbstractIntegrationTest {

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
        adminToken = bearer("cust.admin", RoleName.SUPER_ADMINISTRATOR);
        cashierToken = bearer("cust.cashier", RoleName.CASHIER);
        inventoryToken = bearer("cust.inv", RoleName.INVENTORY_MANAGER);
    }

    @Test
    void createListGetUpdateAndDeactivate() throws Exception {
        mockMvc.perform(post("/api/v1/customers")
                        .header("Authorization", cashierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("C-100", "Ada Lovelace", "555-0100")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.customerCode").value("C-100"))
                .andExpect(jsonPath("$.data.name").value("Ada Lovelace"))
                .andExpect(jsonPath("$.data.phone").value("555-0100"))
                .andExpect(jsonPath("$.data.creditLimit").value(25.00))
                .andExpect(jsonPath("$.data.active").value(true));

        mockMvc.perform(get("/api/v1/customers")
                        .header("Authorization", adminToken)
                        .param("query", "Ada"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].customerCode").value("C-100"));

        MvcResult created = mockMvc.perform(get("/api/v1/customers")
                        .header("Authorization", adminToken)
                        .param("query", "C-100"))
                .andExpect(status().isOk())
                .andReturn();
        String id = created.getResponse().getContentAsString().split("\"id\":\"")[1].split("\"")[0];

        mockMvc.perform(get("/api/v1/customers/" + id)
                        .header("Authorization", cashierToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Ada Lovelace"));

        mockMvc.perform(patch("/api/v1/customers/" + id)
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "customerCode": "C-100",
                                    "name": "Ada L.",
                                    "phone": "555-0100",
                                    "email": "ada@example.com",
                                    "address": "London",
                                    "creditLimit": 40.00,
                                    "isActive": false
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Ada L."))
                .andExpect(jsonPath("$.data.active").value(false))
                .andExpect(jsonPath("$.data.creditLimit").value(40.00));

        mockMvc.perform(get("/api/v1/customers")
                        .header("Authorization", adminToken)
                        .param("isActive", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(0));
    }

    @Test
    void createWritesAudit() throws Exception {
        mockMvc.perform(post("/api/v1/customers")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("C-AUD", "Audit Customer", null)))
                .andExpect(status().isOk());

        entityManager.flush();
        Map<String, Object> audit = jdbcTemplate.queryForMap(
                "SELECT action, entity_type FROM audit_logs WHERE action = 'CUSTOMER_CREATED'");
        assertThat(audit.get("action")).isEqualTo("CUSTOMER_CREATED");
        assertThat(audit.get("entity_type")).isEqualTo("Customer");
    }

    @Test
    void duplicateCustomerCodeIsConflict() throws Exception {
        mockMvc.perform(post("/api/v1/customers")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("C-DUP", "One", null)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/customers")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("C-DUP", "Two", null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("CONFLICT"));
    }

    @Test
    void negativeCreditLimitIsValidationError() throws Exception {
        mockMvc.perform(post("/api/v1/customers")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "customerCode": "C-NEG",
                                    "name": "Bad Limit",
                                    "creditLimit": -1,
                                    "isActive": true
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void inventoryManagerWithoutCustomerPermissionIsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/customers")
                        .header("Authorization", inventoryToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/customers")
                        .header("Authorization", inventoryToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("C-FORB", "Nope", null)))
                .andExpect(status().isForbidden());
    }

    @Test
    void unknownCustomerIsNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/customers/" + UUID.randomUUID())
                        .header("Authorization", adminToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
    }

    private String bearer(String username, String roleName) {
        if (userRepository.findByUsername(username).isEmpty()) {
            User user = new User(username, passwordEncoder.encode("Cust123!"), "Cust", "User");
            user.setEmail(username + "@test.com");
            Role role = roleRepository.findByName(roleName).orElseThrow();
            user.assignRole(role);
            userRepository.save(user);
        }
        return "Bearer " + jwtTokenProvider.generateToken(username);
    }

    private static String createBody(String code, String name, String phone) {
        String phoneJson = phone == null ? "null" : "\"" + phone + "\"";
        return """
                {
                    "customerCode": "%s",
                    "name": "%s",
                    "phone": %s,
                    "email": null,
                    "address": null,
                    "creditLimit": 25.00,
                    "isActive": true
                }
                """.formatted(code, name, phoneJson);
    }
}

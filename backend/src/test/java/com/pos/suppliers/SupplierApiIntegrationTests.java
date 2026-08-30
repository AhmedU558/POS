package com.pos.suppliers;

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
class SupplierApiIntegrationTests extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private EntityManager entityManager;

    private String adminToken;
    private String inventoryToken;
    private String cashierToken;

    @BeforeEach
    void setUp() {
        adminToken = bearer("sup.admin", RoleName.SUPER_ADMINISTRATOR);
        inventoryToken = bearer("sup.inv", RoleName.INVENTORY_MANAGER);
        cashierToken = bearer("sup.cashier", RoleName.CASHIER);
    }

    @Test
    void createListGetUpdateAndDeactivate() throws Exception {
        mockMvc.perform(post("/api/v1/suppliers")
                        .header("Authorization", inventoryToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("S-100", "Acme Supply", "555-0200")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.supplierCode").value("S-100"))
                .andExpect(jsonPath("$.data.name").value("Acme Supply"))
                .andExpect(jsonPath("$.data.phone").value("555-0200"))
                .andExpect(jsonPath("$.data.active").value(true));

        mockMvc.perform(get("/api/v1/suppliers")
                        .header("Authorization", adminToken)
                        .param("query", "Acme"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].supplierCode").value("S-100"));

        MvcResult created = mockMvc.perform(get("/api/v1/suppliers")
                        .header("Authorization", adminToken)
                        .param("query", "S-100"))
                .andExpect(status().isOk())
                .andReturn();
        String id = created.getResponse().getContentAsString().split("\"id\":\"")[1].split("\"")[0];

        mockMvc.perform(get("/api/v1/suppliers/" + id)
                        .header("Authorization", inventoryToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Acme Supply"));

        mockMvc.perform(patch("/api/v1/suppliers/" + id)
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "supplierCode": "S-100",
                                    "name": "Acme Co.",
                                    "phone": "555-0200",
                                    "email": "ap@acme.test",
                                    "address": "Karachi",
                                    "isActive": false
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Acme Co."))
                .andExpect(jsonPath("$.data.active").value(false));

        mockMvc.perform(get("/api/v1/suppliers")
                        .header("Authorization", adminToken)
                        .param("isActive", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(0));
    }

    @Test
    void createWritesAudit() throws Exception {
        mockMvc.perform(post("/api/v1/suppliers")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("S-AUD", "Audit Supplier", null)))
                .andExpect(status().isOk());

        entityManager.flush();
        Map<String, Object> audit = jdbcTemplate.queryForMap(
                "SELECT action, entity_type FROM audit_logs WHERE action = 'SUPPLIER_CREATED'");
        assertThat(audit.get("action")).isEqualTo("SUPPLIER_CREATED");
        assertThat(audit.get("entity_type")).isEqualTo("Supplier");
    }

    @Test
    void duplicateSupplierCodeIsConflict() throws Exception {
        mockMvc.perform(post("/api/v1/suppliers")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("S-DUP", "One", null)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/suppliers")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("S-DUP", "Two", null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("CONFLICT"));
    }

    @Test
    void missingCodeIsValidationError() throws Exception {
        mockMvc.perform(post("/api/v1/suppliers")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "supplierCode": "",
                                    "name": "No Code",
                                    "isActive": true
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void cashierWithoutSupplierPermissionIsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/suppliers")
                        .header("Authorization", cashierToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/suppliers")
                        .header("Authorization", cashierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("S-FORB", "Nope", null)))
                .andExpect(status().isForbidden());
    }

    @Test
    void unknownSupplierIsNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/suppliers/" + UUID.randomUUID())
                        .header("Authorization", adminToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
    }

    private String bearer(String username, String roleName) {
        if (userRepository.findByUsername(username).isEmpty()) {
            User user = new User(username, passwordEncoder.encode("Supp123!"), "Sup", "User");
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
                    "supplierCode": "%s",
                    "name": "%s",
                    "phone": %s,
                    "email": null,
                    "address": null,
                    "isActive": true
                }
                """.formatted(code, name, phoneJson);
    }
}

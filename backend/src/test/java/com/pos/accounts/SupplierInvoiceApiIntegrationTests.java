package com.pos.accounts;

import com.pos.AbstractIntegrationTest;
import com.pos.accounts.domain.SupplierInvoiceStatus;
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
class SupplierInvoiceApiIntegrationTests extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private EntityManager entityManager;

    private String adminToken;
    private String accountantToken;
    private String inventoryToken;

    @BeforeEach
    void setUp() {
        adminToken = bearer("ap.admin", RoleName.SUPER_ADMINISTRATOR);
        accountantToken = bearer("ap.acct", RoleName.ACCOUNTANT);
        inventoryToken = bearer("ap.inv", RoleName.INVENTORY_MANAGER);
    }

    @Test
    void createListGetAndPatchOpenInvoice() throws Exception {
        String supplierId = createSupplier("AP-SUP-1", "AP Supplier");

        mockMvc.perform(post("/api/v1/accounts-payable/invoices")
                        .header("Authorization", accountantToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("INV-100", supplierId, "100.00")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.invoiceNumber").value("INV-100"))
                .andExpect(jsonPath("$.data.status").value("OPEN"))
                .andExpect(jsonPath("$.data.paidAmount").value(0))
                .andExpect(jsonPath("$.data.remainingAmount").value(100.00));

        MvcResult listed = mockMvc.perform(get("/api/v1/accounts-payable/invoices")
                        .header("Authorization", adminToken)
                        .param("query", "INV-100")
                        .param("status", "OPEN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andReturn();
        String id = listed.getResponse().getContentAsString().split("\"id\":\"")[1].split("\"")[0];

        mockMvc.perform(patch("/api/v1/accounts-payable/invoices/" + id)
                        .header("Authorization", accountantToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody("INV-100B", "150.00")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.invoiceNumber").value("INV-100B"))
                .andExpect(jsonPath("$.data.remainingAmount").value(150.00));
    }

    @Test
    void createWritesAudit() throws Exception {
        String supplierId = createSupplier("AP-SUP-AUD", "Audit Supplier");
        mockMvc.perform(post("/api/v1/accounts-payable/invoices")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("INV-AUD", supplierId, "10.00")))
                .andExpect(status().isOk());
        entityManager.flush();
        Map<String, Object> audit = jdbcTemplate.queryForMap(
                "SELECT action, entity_type FROM audit_logs WHERE action = 'SUPPLIER_INVOICE_CREATED'");
        assertThat(audit.get("entity_type")).isEqualTo("SupplierInvoice");
    }

    @Test
    void duplicateInvoiceNumberIsConflict() throws Exception {
        String supplierId = createSupplier("AP-SUP-DUP", "Dup Supplier");
        mockMvc.perform(post("/api/v1/accounts-payable/invoices")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("INV-DUP", supplierId, "10.00")))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/accounts-payable/invoices")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("INV-DUP", supplierId, "20.00")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("CONFLICT"));
    }

    @Test
    void patchRejectedWhenNotOpen() throws Exception {
        String supplierId = createSupplier("AP-SUP-CL", "Closed Supplier");
        MvcResult created = mockMvc.perform(post("/api/v1/accounts-payable/invoices")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("INV-CL", supplierId, "10.00")))
                .andExpect(status().isOk())
                .andReturn();
        String id = created.getResponse().getContentAsString().split("\"id\":\"")[1].split("\"")[0];
        jdbcTemplate.update(
                "UPDATE supplier_invoices SET status = ? WHERE id = CAST(? AS uuid)",
                SupplierInvoiceStatus.CANCELLED.name(),
                id);
        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(patch("/api/v1/accounts-payable/invoices/" + id)
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody("INV-CL", "12.00")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("BUSINESS_RULE_VIOLATION"));
    }

    @Test
    void inventoryManagerIsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/accounts-payable/invoices").header("Authorization", inventoryToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void unknownSupplierIsNotFound() throws Exception {
        mockMvc.perform(post("/api/v1/accounts-payable/invoices")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("INV-MISS", UUID.randomUUID().toString(), "10.00")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
    }

    private String createSupplier(String code, String name) throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/suppliers")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"supplierCode":"%s","name":"%s","isActive":true}
                                """.formatted(code, name)))
                .andExpect(status().isOk())
                .andReturn();
        return created.getResponse().getContentAsString().split("\"id\":\"")[1].split("\"")[0];
    }

    private String bearer(String username, String roleName) {
        if (userRepository.findByUsername(username).isEmpty()) {
            User user = new User(username, passwordEncoder.encode("Ap123456!"), "Ap", "User");
            user.setEmail(username + "@test.com");
            Role role = roleRepository.findByName(roleName).orElseThrow();
            user.assignRole(role);
            userRepository.save(user);
        }
        return "Bearer " + jwtTokenProvider.generateToken(username);
    }

    private static String createBody(String number, String supplierId, String total) {
        return """
                {
                    "invoiceNumber": "%s",
                    "supplierId": "%s",
                    "invoiceDate": "2026-08-01",
                    "dueDate": "2026-08-31",
                    "totalAmount": %s
                }
                """.formatted(number, supplierId, total);
    }

    private static String updateBody(String number, String total) {
        return """
                {
                    "invoiceNumber": "%s",
                    "invoiceDate": "2026-08-02",
                    "dueDate": "2026-09-01",
                    "totalAmount": %s
                }
                """.formatted(number, total);
    }
}

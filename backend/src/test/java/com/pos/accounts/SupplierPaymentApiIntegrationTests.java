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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class SupplierPaymentApiIntegrationTests extends AbstractIntegrationTest {

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
        adminToken = bearer("pay.admin", RoleName.SUPER_ADMINISTRATOR);
        accountantToken = bearer("pay.acct", RoleName.ACCOUNTANT);
        inventoryToken = bearer("pay.inv", RoleName.INVENTORY_MANAGER);
    }

    @Test
    void partialThenFullPaymentMarksInvoicePaid() throws Exception {
        String supplierId = createSupplier("PAY-SUP-1", "Pay Supplier");
        String invoiceId = createInvoice("INV-PAY-1", supplierId, "100.00", "2026-08-01", "2026-09-30");

        mockMvc.perform(post("/api/v1/accounts-payable/payments")
                        .header("Authorization", accountantToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(paymentBody(invoiceId, "40.00", "CASH")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.amount").value(40.00))
                .andExpect(jsonPath("$.data.method").value("CASH"))
                .andExpect(jsonPath("$.data.invoiceId").value(invoiceId));

        mockMvc.perform(get("/api/v1/accounts-payable/invoices/" + invoiceId)
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("OPEN"))
                .andExpect(jsonPath("$.data.paidAmount").value(40.00))
                .andExpect(jsonPath("$.data.remainingAmount").value(60.00));

        mockMvc.perform(post("/api/v1/accounts-payable/payments")
                        .header("Authorization", accountantToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(paymentBody(invoiceId, "60.00", "BANK_TRANSFER")))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/accounts-payable/invoices/" + invoiceId)
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PAID"))
                .andExpect(jsonPath("$.data.remainingAmount").value(0));

        mockMvc.perform(get("/api/v1/accounts-payable/payments")
                        .header("Authorization", adminToken)
                        .param("invoiceId", invoiceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(2));
    }

    @Test
    void paymentWritesAudit() throws Exception {
        String supplierId = createSupplier("PAY-SUP-AUD", "Pay Audit");
        String invoiceId = createInvoice("INV-PAY-AUD", supplierId, "10.00", "2026-08-01", "2026-09-30");
        mockMvc.perform(post("/api/v1/accounts-payable/payments")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(paymentBody(invoiceId, "10.00", "CHEQUE")))
                .andExpect(status().isOk());
        entityManager.flush();
        Map<String, Object> audit = jdbcTemplate.queryForMap(
                "SELECT action, entity_type FROM audit_logs WHERE action = 'SUPPLIER_PAYMENT_CREATED'");
        assertThat(audit.get("entity_type")).isEqualTo("SupplierPayment");
    }

    @Test
    void amountOverRemainingIsRejected() throws Exception {
        String supplierId = createSupplier("PAY-SUP-OVER", "Over Supplier");
        String invoiceId = createInvoice("INV-PAY-OVER", supplierId, "10.00", "2026-08-01", "2026-09-30");
        mockMvc.perform(post("/api/v1/accounts-payable/payments")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(paymentBody(invoiceId, "10.01", "CASH")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("BUSINESS_RULE_VIOLATION"));
    }

    @Test
    void paymentRejectedWhenNotOpen() throws Exception {
        String supplierId = createSupplier("PAY-SUP-CL", "Closed Pay");
        String invoiceId = createInvoice("INV-PAY-CL", supplierId, "10.00", "2026-08-01", "2026-09-30");
        jdbcTemplate.update(
                "UPDATE supplier_invoices SET status = ? WHERE id = CAST(? AS uuid)",
                SupplierInvoiceStatus.CANCELLED.name(),
                invoiceId);
        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(post("/api/v1/accounts-payable/payments")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(paymentBody(invoiceId, "1.00", "CASH")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("BUSINESS_RULE_VIOLATION"));
    }

    @Test
    void overdueSummaryAndStatement() throws Exception {
        String supplierId = createSupplier("PAY-SUP-ST", "Statement Supplier");
        String overdueId = createInvoice("INV-PAY-OD", supplierId, "50.00", "2026-01-01", "2026-01-15");
        createInvoice("INV-PAY-OPEN", supplierId, "20.00", "2026-08-01", "2026-12-31");

        mockMvc.perform(post("/api/v1/accounts-payable/payments")
                        .header("Authorization", accountantToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(paymentBody(overdueId, "10.00", "OTHER")))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/accounts-payable/overdue").header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].invoiceNumber").value("INV-PAY-OD"));

        mockMvc.perform(get("/api/v1/accounts-payable/summary").header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalInvoiced").value(70.00))
                .andExpect(jsonPath("$.data.paid").value(10.00))
                .andExpect(jsonPath("$.data.outstanding").value(60.00))
                .andExpect(jsonPath("$.data.overdue").value(40.00));

        mockMvc.perform(get("/api/v1/suppliers/" + supplierId + "/statement")
                        .header("Authorization", accountantToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(3))
                .andExpect(jsonPath("$.data.content[0].type").value("INVOICE"))
                .andExpect(jsonPath("$.data.content[0].invoiceNumber").value("INV-PAY-OD"))
                .andExpect(jsonPath("$.data.content[0].runningBalance").value(50.00))
                .andExpect(jsonPath("$.data.content[1].type").value("INVOICE"))
                .andExpect(jsonPath("$.data.content[1].invoiceNumber").value("INV-PAY-OPEN"))
                .andExpect(jsonPath("$.data.content[1].runningBalance").value(70.00))
                .andExpect(jsonPath("$.data.content[2].type").value("PAYMENT"))
                .andExpect(jsonPath("$.data.content[2].runningBalance").value(60.00));
    }

    @Test
    void inventoryManagerIsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/accounts-payable/payments").header("Authorization", inventoryToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/accounts-payable/summary").header("Authorization", inventoryToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void unknownInvoiceIsNotFound() throws Exception {
        mockMvc.perform(post("/api/v1/accounts-payable/payments")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(paymentBody(UUID.randomUUID().toString(), "1.00", "CASH")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
    }

    private String createInvoice(
            String number,
            String supplierId,
            String total,
            String invoiceDate,
            String dueDate) throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/accounts-payable/invoices")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "invoiceNumber": "%s",
                                    "supplierId": "%s",
                                    "invoiceDate": "%s",
                                    "dueDate": "%s",
                                    "totalAmount": %s
                                }
                                """.formatted(number, supplierId, invoiceDate, dueDate, total)))
                .andExpect(status().isOk())
                .andReturn();
        return created.getResponse().getContentAsString().split("\"id\":\"")[1].split("\"")[0];
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
            User user = new User(username, passwordEncoder.encode("Pay123456!"), "Pay", "User");
            user.setEmail(username + "@test.com");
            Role role = roleRepository.findByName(roleName).orElseThrow();
            user.assignRole(role);
            userRepository.save(user);
        }
        return "Bearer " + jwtTokenProvider.generateToken(username);
    }

    private static String paymentBody(String invoiceId, String amount, String method) {
        return """
                {
                    "invoiceId": "%s",
                    "amount": %s,
                    "paymentDate": "2026-08-15",
                    "method": "%s",
                    "reference": "REF-1"
                }
                """.formatted(invoiceId, amount, method);
    }
}

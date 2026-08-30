package com.pos.purchases;

import com.pos.AbstractIntegrationTest;
import com.pos.auth.security.JwtTokenProvider;
import com.pos.catalog.entity.Product;
import com.pos.catalog.repository.ProductRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class PurchaseOrderApiIntegrationTests extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ProductRepository productRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private EntityManager entityManager;

    private String adminToken;
    private String inventoryToken;
    private String cashierToken;
    private String accountantToken;

    @BeforeEach
    void setUp() {
        adminToken = bearer("po.admin", RoleName.SUPER_ADMINISTRATOR);
        inventoryToken = bearer("po.inv", RoleName.INVENTORY_MANAGER);
        cashierToken = bearer("po.cashier", RoleName.CASHIER);
        accountantToken = bearer("po.acct", RoleName.ACCOUNTANT);
    }

    @Test
    void createListGetUpdateSubmitAndRejectNonDraftMutations() throws Exception {
        String supplierId = createSupplier("PO-SUP-1", "PO Supplier");
        Product product = persistProduct("SKU-PO-1", "PO Product");

        mockMvc.perform(post("/api/v1/purchase-orders")
                        .header("Authorization", inventoryToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(writeBody("PO-100", supplierId, product.getId(), "2")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.poNumber").value("PO-100"))
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].sku").value("SKU-PO-1"))
                .andExpect(jsonPath("$.data.items[0].quantity").value(2));

        MvcResult listed = mockMvc.perform(get("/api/v1/purchase-orders")
                        .header("Authorization", adminToken)
                        .param("query", "PO-100")
                        .param("status", "DRAFT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andReturn();
        String id = listed.getResponse().getContentAsString().split("\"id\":\"")[1].split("\"")[0];

        mockMvc.perform(get("/api/v1/purchase-orders/" + id)
                        .header("Authorization", inventoryToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.poNumber").value("PO-100"));

        mockMvc.perform(patch("/api/v1/purchase-orders/" + id)
                        .header("Authorization", inventoryToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(writeBody("PO-100B", supplierId, product.getId(), "3")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.poNumber").value("PO-100B"))
                .andExpect(jsonPath("$.data.items[0].quantity").value(3));

        mockMvc.perform(post("/api/v1/purchase-orders/" + id + "/submit")
                        .header("Authorization", inventoryToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUBMITTED"));

        mockMvc.perform(patch("/api/v1/purchase-orders/" + id)
                        .header("Authorization", inventoryToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(writeBody("PO-100B", supplierId, product.getId(), "4")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("BUSINESS_RULE_VIOLATION"));

        mockMvc.perform(post("/api/v1/purchase-orders/" + id + "/cancel")
                        .header("Authorization", inventoryToken))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("BUSINESS_RULE_VIOLATION"));
    }

    @Test
    void cancelDraftAndWriteAudit() throws Exception {
        String supplierId = createSupplier("PO-SUP-AUD", "Audit Supplier");
        Product product = persistProduct("SKU-PO-AUD", "Audit Product");

        String id = createOrder("PO-AUD", supplierId, product.getId());

        mockMvc.perform(post("/api/v1/purchase-orders/" + id + "/cancel")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));

        entityManager.flush();
        Map<String, Object> created = jdbcTemplate.queryForMap(
                "SELECT action, entity_type FROM audit_logs WHERE action = 'PURCHASE_ORDER_CREATED'");
        assertThat(created.get("entity_type")).isEqualTo("PurchaseOrder");
        Map<String, Object> cancelled = jdbcTemplate.queryForMap(
                "SELECT action FROM audit_logs WHERE action = 'PURCHASE_ORDER_CANCELLED'");
        assertThat(cancelled.get("action")).isEqualTo("PURCHASE_ORDER_CANCELLED");
    }

    @Test
    void duplicatePoNumberIsConflict() throws Exception {
        String supplierId = createSupplier("PO-SUP-DUP", "Dup Supplier");
        Product product = persistProduct("SKU-PO-DUP", "Dup Product");
        createOrder("PO-DUP", supplierId, product.getId());

        mockMvc.perform(post("/api/v1/purchase-orders")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(writeBody("PO-DUP", supplierId, product.getId(), "1")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("CONFLICT"));
    }

    @Test
    void unknownSupplierOrProductIsNotFound() throws Exception {
        String supplierId = createSupplier("PO-SUP-MISS", "Missing Refs");
        Product product = persistProduct("SKU-PO-MISS", "Present Product");

        mockMvc.perform(post("/api/v1/purchase-orders")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(writeBody("PO-MISS-S", UUID.randomUUID().toString(), product.getId(), "1")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));

        mockMvc.perform(post("/api/v1/purchase-orders")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(writeBody("PO-MISS-P", supplierId, UUID.randomUUID(), "1")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void rolesWithoutPurchasePermissionAreForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/purchase-orders").header("Authorization", cashierToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/purchase-orders").header("Authorization", accountantToken))
                .andExpect(status().isForbidden());
    }

    private String createOrder(String poNumber, String supplierId, UUID productId) throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/purchase-orders")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(writeBody(poNumber, supplierId, productId, "1")))
                .andExpect(status().isOk())
                .andReturn();
        return created.getResponse().getContentAsString().split("\"id\":\"")[1].split("\"")[0];
    }

    private String createSupplier(String code, String name) throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/suppliers")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "supplierCode": "%s",
                                    "name": "%s",
                                    "isActive": true
                                }
                                """.formatted(code, name)))
                .andExpect(status().isOk())
                .andReturn();
        return created.getResponse().getContentAsString().split("\"id\":\"")[1].split("\"")[0];
    }

    private Product persistProduct(String sku, String name) {
        Product product = new Product();
        product.setSku(sku);
        product.setName(name);
        product.setPurchasePrice(BigDecimal.TEN);
        product.setSellingPrice(BigDecimal.valueOf(20));
        product.setTaxRate(BigDecimal.ZERO);
        product.setMinStock(BigDecimal.ZERO);
        product.setTrackBatch(false);
        product.setTrackExpiry(false);
        product.setActive(true);
        return productRepository.save(product);
    }

    private String bearer(String username, String roleName) {
        if (userRepository.findByUsername(username).isEmpty()) {
            User user = new User(username, passwordEncoder.encode("Po123456!"), "Po", "User");
            user.setEmail(username + "@test.com");
            Role role = roleRepository.findByName(roleName).orElseThrow();
            user.assignRole(role);
            userRepository.save(user);
        }
        return "Bearer " + jwtTokenProvider.generateToken(username);
    }

    private static String writeBody(String poNumber, String supplierId, UUID productId, String quantity) {
        return """
                {
                    "poNumber": "%s",
                    "supplierId": "%s",
                    "notes": null,
                    "items": [{"productId": "%s", "quantity": %s}]
                }
                """.formatted(poNumber, supplierId, productId, quantity);
    }
}

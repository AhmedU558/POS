package com.pos.suppliers;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class SupplierProductApiIntegrationTests extends AbstractIntegrationTest {

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

    @BeforeEach
    void setUp() {
        adminToken = bearer("sp.admin", RoleName.SUPER_ADMINISTRATOR);
        inventoryToken = bearer("sp.inv", RoleName.INVENTORY_MANAGER);
        cashierToken = bearer("sp.cashier", RoleName.CASHIER);
    }

    @Test
    void replaceListsAndClearsAssociations() throws Exception {
        String supplierId = createSupplier("SP-1", "Assoc Supplier");
        Product first = persistProduct("SKU-A", "Alpha");
        Product second = persistProduct("SKU-B", "Beta");

        mockMvc.perform(put("/api/v1/suppliers/" + supplierId + "/products")
                        .header("Authorization", inventoryToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(idsBody(first.getId(), second.getId(), first.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].sku").value("SKU-A"))
                .andExpect(jsonPath("$.data[0].name").value("Alpha"))
                .andExpect(jsonPath("$.data[0].active").value(true))
                .andExpect(jsonPath("$.data[1].sku").value("SKU-B"));

        mockMvc.perform(get("/api/v1/suppliers/" + supplierId + "/products")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));

        mockMvc.perform(put("/api/v1/suppliers/" + supplierId + "/products")
                        .header("Authorization", inventoryToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(idsBody(second.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].sku").value("SKU-B"));

        mockMvc.perform(put("/api/v1/suppliers/" + supplierId + "/products")
                        .header("Authorization", inventoryToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productIds\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void replaceWritesAudit() throws Exception {
        String supplierId = createSupplier("SP-AUD", "Audit Assoc");
        Product product = persistProduct("SKU-AUD", "Audit Product");

        mockMvc.perform(put("/api/v1/suppliers/" + supplierId + "/products")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(idsBody(product.getId())))
                .andExpect(status().isOk());

        entityManager.flush();
        Map<String, Object> audit = jdbcTemplate.queryForMap(
                "SELECT action, entity_type FROM audit_logs WHERE action = 'SUPPLIER_PRODUCTS_UPDATED'");
        assertThat(audit.get("action")).isEqualTo("SUPPLIER_PRODUCTS_UPDATED");
        assertThat(audit.get("entity_type")).isEqualTo("Supplier");
    }

    @Test
    void unknownProductIsNotFound() throws Exception {
        String supplierId = createSupplier("SP-MISS", "Missing Product");

        mockMvc.perform(put("/api/v1/suppliers/" + supplierId + "/products")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(idsBody(UUID.randomUUID())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void unknownSupplierIsNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/suppliers/" + UUID.randomUUID() + "/products")
                        .header("Authorization", adminToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void cashierWithoutSupplierPermissionIsForbidden() throws Exception {
        String supplierId = createSupplier("SP-FORB", "Forbidden Assoc");

        mockMvc.perform(get("/api/v1/suppliers/" + supplierId + "/products")
                        .header("Authorization", cashierToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/v1/suppliers/" + supplierId + "/products")
                        .header("Authorization", cashierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productIds\":[]}"))
                .andExpect(status().isForbidden());
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
            User user = new User(username, passwordEncoder.encode("Supp123!"), "Sup", "User");
            user.setEmail(username + "@test.com");
            Role role = roleRepository.findByName(roleName).orElseThrow();
            user.assignRole(role);
            userRepository.save(user);
        }
        return "Bearer " + jwtTokenProvider.generateToken(username);
    }

    private static String idsBody(UUID... ids) {
        StringBuilder json = new StringBuilder("{\"productIds\":[");
        for (int i = 0; i < ids.length; i++) {
            if (i > 0) {
                json.append(',');
            }
            json.append('"').append(ids[i]).append('"');
        }
        return json.append("]}").toString();
    }
}

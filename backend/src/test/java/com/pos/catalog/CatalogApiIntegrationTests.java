package com.pos.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pos.AbstractIntegrationTest;
import com.pos.catalog.dto.BrandRequest;
import com.pos.catalog.dto.CategoryRequest;
import com.pos.catalog.dto.UnitRequest;
import com.pos.users.domain.Role;
import com.pos.users.domain.User;
import com.pos.users.repository.RoleRepository;
import com.pos.users.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class CatalogApiIntegrationTests extends AbstractIntegrationTest {

    @Autowired private org.springframework.test.web.servlet.MockMvc mockMvc;
    @Autowired private com.pos.auth.security.JwtTokenProvider jwtTokenProvider;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ObjectMapper objectMapper;

    private String adminToken;

    @BeforeEach
    void setUp() {
        if (userRepository.findByUsername("admin").isEmpty()) {
            User admin = new User("admin", passwordEncoder.encode("Admin123!"), "Admin", "User");
            admin.setEmail("admin@test.com");
            Role superAdmin = roleRepository.findByName("Super Administrator").orElseThrow();
            admin.assignRole(superAdmin);
            userRepository.save(admin);
        }
        adminToken = "Bearer " + jwtTokenProvider.generateToken("admin");
    }

    // ---- Categories ----

    @Test
    void createCategory_returnsCreated() throws Exception {
        CategoryRequest req = new CategoryRequest("Electronics", "Electronic devices", null, null);
        mockMvc.perform(post("/api/v1/categories")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("Electronics"))
                .andExpect(jsonPath("$.data.parentId").isEmpty())
                .andExpect(jsonPath("$.data.active").value(true));
    }

    @Test
    void createChildCategory_respectsDepthLimit() throws Exception {
        // Level 1 (root)
        CategoryRequest root = new CategoryRequest("L1", null, null, null);
        String rootRes = mockMvc.perform(post("/api/v1/categories")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(root)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID rootId = UUID.fromString(objectMapper.readTree(rootRes).get("data").get("id").asText());

        // Level 2
        CategoryRequest l2 = new CategoryRequest("L2", null, rootId, null);
        String l2Res = mockMvc.perform(post("/api/v1/categories")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(l2)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID l2Id = UUID.fromString(objectMapper.readTree(l2Res).get("data").get("id").asText());

        // Level 3
        CategoryRequest l3 = new CategoryRequest("L3", null, l2Id, null);
        String l3Res = mockMvc.perform(post("/api/v1/categories")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(l3)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID l3Id = UUID.fromString(objectMapper.readTree(l3Res).get("data").get("id").asText());

        // Level 4 - should fail
        CategoryRequest l4 = new CategoryRequest("L4", null, l3Id, null);
        mockMvc.perform(post("/api/v1/categories")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(l4)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void createDuplicateRootCategory_returnsConflict() throws Exception {
        CategoryRequest req = new CategoryRequest("Food", null, null, null);
        mockMvc.perform(post("/api/v1/categories")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/categories")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict());
    }

    @Test
    void updateCategory_selfParent_rejected() throws Exception {
        CategoryRequest req = new CategoryRequest("Widgets", null, null, null);
        String res = mockMvc.perform(post("/api/v1/categories")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID id = UUID.fromString(objectMapper.readTree(res).get("data").get("id").asText());

        CategoryRequest update = new CategoryRequest("Widgets", null, id, null);
        mockMvc.perform(patch("/api/v1/categories/" + id)
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void listCategories_requiresProductRead() throws Exception {
        // Create a cashier without PRODUCT_READ
        User cashier = new User("cashier_noread", passwordEncoder.encode("Pass123!"), "Cash", "Ier");
        cashier.setEmail("cashier_noread@test.com");
        // Assign no roles (so no permissions at all)
        userRepository.save(cashier);
        String cashierToken = "Bearer " + jwtTokenProvider.generateToken("cashier_noread");

        mockMvc.perform(get("/api/v1/categories")
                        .header("Authorization", cashierToken))
                .andExpect(status().isForbidden());
    }

    // ---- Brands ----

    @Test
    void createAndListBrand() throws Exception {
        BrandRequest req = new BrandRequest("Samsung", "Consumer electronics", null);
        mockMvc.perform(post("/api/v1/brands")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("Samsung"))
                .andExpect(jsonPath("$.data.active").value(true));

        mockMvc.perform(get("/api/v1/brands")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.name=='Samsung')]").exists());
    }

    @Test
    void createDuplicateBrand_returnsConflict() throws Exception {
        BrandRequest req = new BrandRequest("Apple", null, null);
        mockMvc.perform(post("/api/v1/brands")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/brands")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict());
    }

    @Test
    void updateBrand_deactivate() throws Exception {
        BrandRequest req = new BrandRequest("Deprecated", null, null);
        String res = mockMvc.perform(post("/api/v1/brands")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID id = UUID.fromString(objectMapper.readTree(res).get("data").get("id").asText());

        BrandRequest update = new BrandRequest("Deprecated", null, false);
        mockMvc.perform(patch("/api/v1/brands/" + id)
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active").value(false));
    }

    // ---- Units ----

    @Test
    void createAndListUnit() throws Exception {
        UnitRequest req = new UnitRequest("PCS", "Pieces", null);
        mockMvc.perform(post("/api/v1/units")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.code").value("PCS"))
                .andExpect(jsonPath("$.data.name").value("Pieces"));

        mockMvc.perform(get("/api/v1/units")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.code=='PCS')]").exists());
    }

    @Test
    void createDuplicateUnitCode_returnsConflict() throws Exception {
        UnitRequest req1 = new UnitRequest("KG", "Kilograms", null);
        mockMvc.perform(post("/api/v1/units")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req1)))
                .andExpect(status().isCreated());

        UnitRequest req2 = new UnitRequest("KG", "Kilogram", null);
        mockMvc.perform(post("/api/v1/units")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req2)))
                .andExpect(status().isConflict());
    }

    @Test
    void updateUnit_changeName() throws Exception {
        UnitRequest req = new UnitRequest("LTR", "Litre", null);
        String res = mockMvc.perform(post("/api/v1/units")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID id = UUID.fromString(objectMapper.readTree(res).get("data").get("id").asText());

        UnitRequest update = new UnitRequest("LTR", "Liter", null);
        mockMvc.perform(patch("/api/v1/units/" + id)
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Liter"));
    }

    @Test
    void createBrand_withoutPermission_isForbidden() throws Exception {
        User noPerms = new User("noPerms", passwordEncoder.encode("Pass123!"), "No", "Perms");
        noPerms.setEmail("noperms@test.com");
        userRepository.save(noPerms);
        String noPermsToken = "Bearer " + jwtTokenProvider.generateToken("noPerms");

        BrandRequest req = new BrandRequest("TestBrand", null, null);
        mockMvc.perform(post("/api/v1/brands")
                        .header("Authorization", noPermsToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    void createUnit_withoutPermission_isForbidden() throws Exception {
        User noPerms = new User("noPermsUnit", passwordEncoder.encode("Pass123!"), "No", "Perms");
        noPerms.setEmail("nopermsunit@test.com");
        userRepository.save(noPerms);
        String noPermsToken = "Bearer " + jwtTokenProvider.generateToken("noPermsUnit");

        UnitRequest req = new UnitRequest("INVALID", "Test", null);
        mockMvc.perform(post("/api/v1/units")
                        .header("Authorization", noPermsToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }
}

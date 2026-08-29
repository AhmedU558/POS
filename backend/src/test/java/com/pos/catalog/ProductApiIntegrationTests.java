package com.pos.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pos.AbstractIntegrationTest;
import com.pos.users.domain.Role;
import com.pos.users.domain.User;
import com.pos.users.repository.RoleRepository;
import com.pos.users.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Transactional
class ProductApiIntegrationTests extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private com.pos.auth.security.JwtTokenProvider jwtTokenProvider;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private String adminToken;
    private String cashierToken;

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

        if (userRepository.findByUsername("cashier").isEmpty()) {
            User cashier = new User("cashier", passwordEncoder.encode("Cashier123!"), "Cashier", "User");
            cashier.setEmail("cashier@test.com");
            Role cashierRole = roleRepository.findByName("Cashier").orElseThrow();
            cashier.assignRole(cashierRole);
            userRepository.save(cashier);
        }
        cashierToken = "Bearer " + jwtTokenProvider.generateToken("cashier");
    }

    @Test
    void productLifecycle_success() throws Exception {
        // 1. Create Product
        String createJson = """
            {
                "sku": "SKU-001",
                "name": "Test Product",
                "purchasePrice": 10.0,
                "sellingPrice": 20.0,
                "taxRate": 0.1,
                "minStock": 5.0,
                "trackBatch": false,
                "trackExpiry": false,
                "isActive": true
            }
            """;

        MvcResult createResult = mockMvc.perform(post("/api/v1/products")
                .header("Authorization", adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sku").value("SKU-001"))
                .andExpect(jsonPath("$.data.id").exists())
                .andReturn();

        String responseBody = createResult.getResponse().getContentAsString();
        String productId = responseBody.split("\"id\":\"")[1].split("\"")[0];

        // 2. Add Barcode
        String barcodeJson = """
            {"barcode": "1234567890", "isPrimary": true}
            """;
        mockMvc.perform(post("/api/v1/products/" + productId + "/barcodes")
                .header("Authorization", adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(barcodeJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.barcode").value("1234567890"));

        // 3. Add Price
        String priceJson = """
            {"priceType": "REGULAR", "amount": 25.0, "effectiveFrom": "2025-01-01T00:00:00Z"}
            """;
        mockMvc.perform(post("/api/v1/products/" + productId + "/prices")
                .header("Authorization", adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(priceJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.amount").value(25.0));
    }

    @Test
    void missingPermission_isForbidden() throws Exception {
        String createJson = """
            {"sku": "SKU-002", "name": "Test", "purchasePrice": 10, "sellingPrice": 20, "taxRate": 0.1, "minStock": 5, "trackBatch": false, "trackExpiry": false, "isActive": true}
            """;

        mockMvc.perform(post("/api/v1/products")
                .header("Authorization", cashierToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createJson))
                .andExpect(status().isForbidden());
    }
}

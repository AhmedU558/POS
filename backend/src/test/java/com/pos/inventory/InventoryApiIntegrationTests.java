package com.pos.inventory;

import com.pos.AbstractIntegrationTest;
import com.pos.catalog.entity.Product;
import com.pos.catalog.repository.ProductRepository;
import com.pos.inventory.domain.InventoryBalance;
import com.pos.inventory.domain.InventoryTransaction;
import com.pos.inventory.repository.InventoryBalanceRepository;
import com.pos.inventory.repository.InventoryTransactionRepository;
import com.pos.organization.domain.Store;
import com.pos.organization.repository.StoreRepository;
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
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@org.springframework.transaction.annotation.Transactional
public class InventoryApiIntegrationTests extends AbstractIntegrationTest {

    @Autowired private StoreRepository storeRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private InventoryBalanceRepository balanceRepository;
    @Autowired private InventoryTransactionRepository transactionRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private MockMvc mockMvc;
    @Autowired private com.pos.auth.security.JwtTokenProvider jwtTokenProvider;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private Store testStore;
    private Product testProduct;
    private User admin;
    private String adminToken;
    private String noAuthToken;

    @BeforeEach
    void setup() {
        if (storeRepository.count() == 0) {
            Store store = new Store("TEST-STORE", "Test Store", "USD", "UTC");
            testStore = storeRepository.save(store);
        } else {
            testStore = storeRepository.findAll().get(0);
        }

        if (productRepository.count() == 0) {
            Product product = new Product();
            product.setSku("TEST-SKU");
            product.setName("Test Product");
            product.setPurchasePrice(BigDecimal.TEN);
            product.setSellingPrice(BigDecimal.valueOf(20));
            product.setTaxRate(BigDecimal.ZERO);
            product.setMinStock(BigDecimal.ZERO);
            product.setMaxStock(BigDecimal.valueOf(100));
            product.setTrackBatch(false);
            product.setTrackExpiry(false);
            product.setActive(true);
            testProduct = productRepository.save(product);
        } else {
            testProduct = productRepository.findAll().get(0);
        }

        if (userRepository.findByUsername("admin").isEmpty()) {
            admin = new User("admin", passwordEncoder.encode("Admin123!"), "Admin", "User");
            admin.setEmail("admin@test.com");
            Role superAdmin = roleRepository.findByName("Super Administrator").orElseThrow();
            admin.assignRole(superAdmin);
            admin.assignStore(testStore);
            admin = userRepository.save(admin);
        } else {
            admin = userRepository.findByUsername("admin").get();
            if (!userRepository.hasStoreAccess(admin.getId(), testStore.getId())) {
                admin.assignStore(testStore);
                admin = userRepository.save(admin);
            }
        }
        adminToken = "Bearer " + jwtTokenProvider.generateToken("admin");

        if (userRepository.findByUsername("cashier").isEmpty()) {
            User cashier = new User("cashier", passwordEncoder.encode("Cashier123!"), "Cashier", "User");
            cashier.setEmail("cashier@test.com");
            Role cashierRole = roleRepository.findByName("Cashier").orElseThrow();
            cashier.assignRole(cashierRole);
            userRepository.save(cashier);
        }
        noAuthToken = "Bearer " + jwtTokenProvider.generateToken("cashier");
    }

    @Test
    void shouldAdjustStockSuccessfully() throws Exception {
        String payload = String.format("{\"storeId\":\"%s\", \"productId\":\"%s\", \"quantity\":10.5, \"reason\":\"Initial Stock\"}", testStore.getId(), testProduct.getId());

        mockMvc.perform(post("/api/v1/inventory/adjustments")
                .header("Authorization", adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.quantity").value(10.5));

        InventoryBalance balance = balanceRepository.findAll().stream()
            .filter(b -> b.getProduct().getId().equals(testProduct.getId()) && b.getStore().getId().equals(testStore.getId()))
            .findFirst().orElseThrow();
        assertThat(balance.getQuantity().compareTo(BigDecimal.valueOf(10.5))).isEqualTo(0);
    }

    @Test
    void shouldFailAdjustmentWithoutPermission() throws Exception {
        String payload = String.format("{\"storeId\":\"%s\", \"productId\":\"%s\", \"quantity\":10.5, \"reason\":\"Initial Stock\"}", testStore.getId(), testProduct.getId());

        mockMvc.perform(post("/api/v1/inventory/adjustments")
                .header("Authorization", noAuthToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldFailAdjustmentResultingInNegativeStock() throws Exception {
        InventoryBalance b = new InventoryBalance(testProduct, testStore);
        b.addQuantity(BigDecimal.valueOf(5.0));
        balanceRepository.save(b);
        String payload = String.format("{\"storeId\":\"%s\", \"productId\":\"%s\", \"quantity\":-10.0, \"reason\":\"Too much reduction\"}", testStore.getId(), testProduct.getId());

        mockMvc.perform(post("/api/v1/inventory/adjustments")
                .header("Authorization", adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isUnprocessableEntity());
    }
    
    @Test
    void testInventoryTransactionImmutability() {
        InventoryTransaction tx = new InventoryTransaction(testProduct, testStore, com.pos.inventory.domain.TransactionType.ADJUSTMENT, BigDecimal.ONE, "Test", admin);
        transactionRepository.saveAndFlush(tx);
        
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> {
            jdbcTemplate.execute("DELETE FROM inventory_transactions WHERE id = '" + tx.getId() + "'");
        }).hasMessageContaining("inventory_transactions rows are immutable");
    }
}
package com.pos.inventory;

import com.pos.AbstractIntegrationTest;
import com.pos.catalog.entity.Product;
import com.pos.catalog.repository.ProductRepository;
import com.pos.inventory.domain.InventoryBalance;
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

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class InventoryConcurrencyTests extends AbstractIntegrationTest {

    @Autowired private StoreRepository storeRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private InventoryBalanceRepository balanceRepository;
    @Autowired private InventoryTransactionRepository transactionRepository;
    @Autowired private MockMvc mockMvc;
    @Autowired private com.pos.auth.security.JwtTokenProvider jwtTokenProvider;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private Store testStore;
    private Product testProduct;
    private String adminToken;

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
            User admin = new User("admin", passwordEncoder.encode("Admin123!"), "Admin", "User");
            admin.setEmail("admin@test.com");
            Role superAdmin = roleRepository.findByName("Super Administrator").orElseThrow();
            admin.assignRole(superAdmin);
            admin.assignStore(testStore);
            userRepository.save(admin);
        } else {
            User admin = userRepository.findByUsername("admin").get();
            if (!userRepository.hasStoreAccess(admin.getId(), testStore.getId())) {
                admin.assignStore(testStore);
                userRepository.save(admin);
            }
        }
        adminToken = "Bearer " + jwtTokenProvider.generateToken("admin");
    }

    @Test
    void testConcurrentInventoryAdjustments() throws InterruptedException {
        InventoryBalance initialBalance = balanceRepository.findAll().stream()
            .filter(b -> b.getProduct().getId().equals(testProduct.getId()) && b.getStore().getId().equals(testStore.getId()))
            .findFirst()
            .orElseGet(() -> balanceRepository.save(new InventoryBalance(testProduct, testStore)));
        
        BigDecimal initialQty = initialBalance.getQuantity();

        int numThreads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch latch = new CountDownLatch(numThreads);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < numThreads; i++) {
            executor.submit(() -> {
                try {
                    String payload = String.format("{\"storeId\":\"%s\", \"productId\":\"%s\", \"quantity\":5.0, \"reason\":\"Concurrency Test\"}", testStore.getId(), testProduct.getId());
                    mockMvc.perform(post("/api/v1/inventory/adjustments")
                            .header("Authorization", adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payload))
                            .andExpect(status().isOk());
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(successCount.get()).isEqualTo(numThreads);

        InventoryBalance finalBalance = balanceRepository.findAll().stream()
            .filter(b -> b.getProduct().getId().equals(testProduct.getId()) && b.getStore().getId().equals(testStore.getId()))
            .findFirst().orElseThrow();
            
        BigDecimal expectedQty = initialQty.add(BigDecimal.valueOf(numThreads * 5.0));
        assertThat(finalBalance.getQuantity().compareTo(expectedQty)).isEqualTo(0);
    }

    @Test
    void testConcurrentInventoryCreation() throws InterruptedException {
        Product newProduct = new Product();
        newProduct.setSku("TEST-SKU-NEW-" + UUID.randomUUID().toString());
        newProduct.setName("New Product");
        newProduct.setPurchasePrice(BigDecimal.TEN);
        newProduct.setSellingPrice(BigDecimal.valueOf(20));
        newProduct.setTaxRate(BigDecimal.ZERO);
        newProduct.setMinStock(BigDecimal.ZERO);
        newProduct.setMaxStock(BigDecimal.valueOf(100));
        newProduct.setTrackBatch(false);
        newProduct.setTrackExpiry(false);
        newProduct.setActive(true);
        Product savedProduct = productRepository.save(newProduct);

        int numThreads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch latch = new CountDownLatch(numThreads);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < numThreads; i++) {
            executor.submit(() -> {
                try {
                    String payload = String.format("{\"storeId\":\"%s\", \"productId\":\"%s\", \"quantity\":5.0, \"reason\":\"Concurrent Creation\"}", testStore.getId(), savedProduct.getId());
                    var result = mockMvc.perform(post("/api/v1/inventory/adjustments")
                            .header("Authorization", adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payload))
                            .andReturn();
                    if (result.getResponse().getStatus() == 200) {
                        successCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        InventoryBalance finalBalance = balanceRepository.findAll().stream()
            .filter(b -> b.getProduct().getId().equals(savedProduct.getId()) && b.getStore().getId().equals(testStore.getId()))
            .findFirst().orElseThrow();
            
        BigDecimal expectedQty = BigDecimal.valueOf(successCount.get() * 5.0);
        assertThat(finalBalance.getQuantity().compareTo(expectedQty)).isEqualTo(0);
        
        long ledgerCount = transactionRepository.findAll().stream()
            .filter(t -> t.getProduct().getId().equals(savedProduct.getId()) && t.getStore().getId().equals(testStore.getId()))
            .count();
        assertThat(ledgerCount).isEqualTo(successCount.get());
    }
}
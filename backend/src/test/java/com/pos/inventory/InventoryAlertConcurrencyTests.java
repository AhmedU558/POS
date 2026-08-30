package com.pos.inventory;

import com.pos.AbstractIntegrationTest;
import com.pos.auth.security.JwtTokenProvider;
import com.pos.catalog.entity.Product;
import com.pos.catalog.repository.ProductRepository;
import com.pos.organization.domain.Store;
import com.pos.organization.repository.StoreRepository;
import com.pos.users.domain.RoleName;
import com.pos.users.domain.User;
import com.pos.users.repository.RoleRepository;
import com.pos.users.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
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

class InventoryAlertConcurrencyTests extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private StoreRepository storeRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void concurrentAdjustmentsBelowMinimumCreateOneLowStockAlert() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Store store = storeRepository.save(new Store("CALT-" + suffix, "Concurrent Alert " + suffix, "USD", "UTC"));
        Product product = new Product();
        product.setSku("CALT-SKU-" + suffix);
        product.setName("Concurrent Alert " + suffix);
        product.setPurchasePrice(BigDecimal.TEN);
        product.setSellingPrice(BigDecimal.valueOf(20));
        product.setTaxRate(BigDecimal.ZERO);
        product.setMinStock(new BigDecimal("8"));
        product.setMaxStock(BigDecimal.valueOf(100));
        product.setTrackBatch(false);
        product.setTrackExpiry(false);
        product.setActive(true);
        product = productRepository.save(product);

        String username = "calt." + suffix;
        User user = new User(username, passwordEncoder.encode("Alert123!"), "Concurrent", "Alert");
        user.setEmail(username + "@test.com");
        user.assignRole(roleRepository.findByName(RoleName.INVENTORY_MANAGER).orElseThrow());
        user.assignStore(store);
        userRepository.save(user);
        String token = "Bearer " + jwtTokenProvider.generateToken(username);

        mockMvc.perform(post("/api/v1/inventory/adjustments")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"storeId\":\"%s\",\"productId\":\"%s\",\"quantity\":10,\"reason\":\"SEED\"}",
                                store.getId(), product.getId())))
                .andReturn();

        int threads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger successes = new AtomicInteger();
        UUID storeId = store.getId();
        UUID productId = product.getId();

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                try {
                    start.await();
                    String payload = String.format(
                            "{\"storeId\":\"%s\",\"productId\":\"%s\",\"quantity\":-1,\"reason\":\"CONCURRENT\"}",
                            storeId, productId);
                    var result = mockMvc.perform(post("/api/v1/inventory/adjustments")
                                    .header("Authorization", token)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(payload))
                            .andReturn();
                    if (result.getResponse().getStatus() == 200) {
                        successes.incrementAndGet();
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown();
        assertThat(done.await(45, TimeUnit.SECONDS)).isTrue();
        executor.shutdown();

        assertThat(successes.get()).isEqualTo(threads);

        Long alertCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM stock_alerts WHERE store_id = ? AND product_id = ? AND alert_type = 'LOW_STOCK'",
                Long.class,
                storeId,
                productId);
        assertThat(alertCount).isEqualTo(1L);
    }
}

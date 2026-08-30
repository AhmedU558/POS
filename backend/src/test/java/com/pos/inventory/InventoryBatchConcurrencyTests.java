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
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

class InventoryBatchConcurrencyTests extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private StoreRepository storeRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void concurrentReceiptsOnTheSameLotSumExactly() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Store store = storeRepository.save(new Store("CBAT-" + suffix, "Concurrent Batch " + suffix, "USD", "UTC"));
        Product product = new Product();
        product.setSku("CBAT-SKU-" + suffix);
        product.setName("Concurrent Batch " + suffix);
        product.setPurchasePrice(BigDecimal.TEN);
        product.setSellingPrice(BigDecimal.valueOf(20));
        product.setTaxRate(BigDecimal.ZERO);
        product.setMinStock(BigDecimal.ZERO);
        product.setMaxStock(BigDecimal.valueOf(100));
        product.setTrackBatch(true);
        product.setTrackExpiry(true);
        product.setActive(true);
        product = productRepository.save(product);

        String username = "cbat." + suffix;
        User user = new User(username, passwordEncoder.encode("Batch123!"), "Concurrent", "Batch");
        user.setEmail(username + "@test.com");
        user.assignRole(roleRepository.findByName(RoleName.INVENTORY_MANAGER).orElseThrow());
        user.assignStore(store);
        userRepository.save(user);
        String token = "Bearer " + jwtTokenProvider.generateToken(username);

        int threads = 10;
        BigDecimal each = new BigDecimal("2.0000");
        String expiry = LocalDate.now().plusDays(60).toString();
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
                            "{\"storeId\":\"%s\",\"productId\":\"%s\",\"quantity\":%s,\"batchNumber\":\"LOT-CON\",\"expirationDate\":\"%s\"}",
                            storeId,
                            productId,
                            each.toPlainString(),
                            expiry);
                    var result = mockMvc.perform(post("/api/v1/inventory/receipts")
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

        BigDecimal expected = each.multiply(BigDecimal.valueOf(threads));
        BigDecimal storeQty = jdbcTemplate.queryForObject(
                "SELECT quantity FROM inventory_balances WHERE product_id = ? AND store_id = ?",
                BigDecimal.class,
                productId,
                storeId);
        BigDecimal batchQty = jdbcTemplate.queryForObject(
                "SELECT quantity FROM inventory_batches WHERE product_id = ? AND store_id = ? AND batch_number = 'LOT-CON'",
                BigDecimal.class,
                productId,
                storeId);
        assertThat(storeQty).isEqualByComparingTo(expected);
        assertThat(batchQty).isEqualByComparingTo(expected);

        Long ledgerCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM inventory_transactions WHERE product_id = ? AND store_id = ? AND transaction_type = 'RECEIPT' AND batch_id IS NOT NULL",
                Long.class,
                productId,
                storeId);
        assertThat(ledgerCount).isEqualTo((long) threads);
    }
}

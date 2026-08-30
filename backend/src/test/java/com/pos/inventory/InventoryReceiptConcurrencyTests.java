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

class InventoryReceiptConcurrencyTests extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private StoreRepository storeRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JdbcTemplate jdbcTemplate;

    private Store store;
    private Product product;

    @Test
    void concurrentReceiptsOnTheSameProductAndStoreSumExactly() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        store = storeRepository.save(new Store("CRCV-" + suffix, "Concurrent Receive " + suffix, "USD", "UTC"));
        product = newProduct("CRCV-SKU-" + suffix);
        String username = "crcv." + suffix;
        User user = new User(username, passwordEncoder.encode("Receive123!"), "Concurrent", "Receive");
        user.setEmail(username + "@test.com");
        user.assignRole(roleRepository.findByName(RoleName.INVENTORY_MANAGER).orElseThrow());
        user.assignStore(store);
        userRepository.save(user);
        String token = "Bearer " + jwtTokenProvider.generateToken(username);

        int threads = 10;
        BigDecimal each = new BigDecimal("2.0000");
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger successes = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                try {
                    start.await();
                    String payload = String.format(
                            "{\"storeId\":\"%s\",\"productId\":\"%s\",\"quantity\":%s}",
                            store.getId(),
                            product.getId(),
                            each.toPlainString());
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
        BigDecimal actual = jdbcTemplate.queryForObject(
                "SELECT quantity FROM inventory_balances WHERE product_id = ? AND store_id = ?",
                BigDecimal.class,
                product.getId(),
                store.getId());
        assertThat(actual).isEqualByComparingTo(expected);

        Long ledgerCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM inventory_transactions WHERE product_id = ? AND store_id = ? AND transaction_type = 'RECEIPT'",
                Long.class,
                product.getId(),
                store.getId());
        assertThat(ledgerCount).isEqualTo((long) threads);
    }

    private Product newProduct(String sku) {
        Product p = new Product();
        p.setSku(sku);
        p.setName("Concurrent " + sku);
        p.setPurchasePrice(BigDecimal.TEN);
        p.setSellingPrice(BigDecimal.valueOf(20));
        p.setTaxRate(BigDecimal.ZERO);
        p.setMinStock(BigDecimal.ZERO);
        p.setMaxStock(BigDecimal.valueOf(100));
        p.setTrackBatch(false);
        p.setTrackExpiry(false);
        p.setActive(true);
        return productRepository.save(p);
    }
}

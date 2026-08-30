package com.pos.inventory;

import com.pos.AbstractIntegrationTest;
import com.pos.audit.domain.AuditEvent;
import com.pos.audit.repository.AuditLogRepository;
import com.pos.audit.service.AuditRecorder;
import com.pos.auth.security.JwtTokenProvider;
import com.pos.catalog.entity.Product;
import com.pos.catalog.repository.ProductRepository;
import com.pos.organization.domain.Store;
import com.pos.organization.repository.StoreRepository;
import com.pos.users.domain.RoleName;
import com.pos.users.domain.User;
import com.pos.users.repository.RoleRepository;
import com.pos.users.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * If the audit write fails after the batch, balance, and ledger inserts, the whole receipt must roll back.
 */
@Import(InventoryBatchAtomicityTests.FailingAuditConfiguration.class)
class InventoryBatchAtomicityTests extends AbstractIntegrationTest {

    @TestConfiguration
    static class FailingAuditConfiguration {
        @Bean
        @Primary
        AuditRecorder failingAuditRecorder(AuditLogRepository auditLogRepository) {
            return new AuditRecorder(auditLogRepository) {
                @Override
                public com.pos.audit.domain.AuditLog record(AuditEvent event) {
                    throw new IllegalStateException("audit sink unavailable");
                }
            };
        }
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private StoreRepository storeRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JdbcTemplate jdbcTemplate;

    private String username;

    @AfterEach
    void cleanupUser() {
        if (username != null) {
            userRepository.findByUsername(username).ifPresent(userRepository::delete);
        }
    }

    @Test
    void auditFailureRollsBackBalanceLedgerAndBatch() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Store store = storeRepository.save(new Store("ABAT-" + suffix, "Atomic Batch " + suffix, "USD", "UTC"));
        Product product = new Product();
        product.setSku("ABAT-SKU-" + suffix);
        product.setName("Atomic Batch " + suffix);
        product.setPurchasePrice(BigDecimal.TEN);
        product.setSellingPrice(BigDecimal.valueOf(20));
        product.setTaxRate(BigDecimal.ZERO);
        product.setMinStock(BigDecimal.ZERO);
        product.setMaxStock(BigDecimal.valueOf(100));
        product.setTrackBatch(true);
        product.setTrackExpiry(true);
        product.setActive(true);
        product = productRepository.save(product);

        username = "abat." + suffix;
        User user = new User(username, passwordEncoder.encode("Batch123!"), "Atomic", "Batch");
        user.setEmail(username + "@test.com");
        user.assignRole(roleRepository.findByName(RoleName.INVENTORY_MANAGER).orElseThrow());
        user.assignStore(store);
        userRepository.save(user);

        String payload = String.format(
                "{\"storeId\":\"%s\",\"productId\":\"%s\",\"quantity\":5,\"batchNumber\":\"LOT-ATOM\",\"expirationDate\":\"%s\"}",
                store.getId(),
                product.getId(),
                LocalDate.now().plusDays(10));

        mockMvc.perform(post("/api/v1/inventory/receipts")
                        .header("Authorization", "Bearer " + jwtTokenProvider.generateToken(username))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isInternalServerError());

        Long balances = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM inventory_balances WHERE product_id = ? AND store_id = ?",
                Long.class,
                product.getId(),
                store.getId());
        Long ledger = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM inventory_transactions WHERE product_id = ? AND store_id = ?",
                Long.class,
                product.getId(),
                store.getId());
        Long batches = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM inventory_batches WHERE product_id = ? AND store_id = ?",
                Long.class,
                product.getId(),
                store.getId());
        assertThat(balances).isZero();
        assertThat(ledger).isZero();
        assertThat(batches).isZero();
    }
}

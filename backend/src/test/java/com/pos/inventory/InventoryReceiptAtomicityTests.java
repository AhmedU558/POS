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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * If the audit write fails after the balance and ledger inserts, the whole receipt must roll back.
 */
@Import(InventoryReceiptAtomicityTests.FailingAuditConfiguration.class)
class InventoryReceiptAtomicityTests extends AbstractIntegrationTest {

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
    private Product product;
    private Store store;

    @AfterEach
    void cleanupUser() {
        if (username != null) {
            userRepository.findByUsername(username).ifPresent(userRepository::delete);
        }
    }

    @Test
    void auditFailureRollsBackBalanceAndLedger() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        store = storeRepository.save(new Store("ARCV-" + suffix, "Atomic Receive " + suffix, "USD", "UTC"));
        product = new Product();
        product.setSku("ARCV-SKU-" + suffix);
        product.setName("Atomic " + suffix);
        product.setPurchasePrice(BigDecimal.TEN);
        product.setSellingPrice(BigDecimal.valueOf(20));
        product.setTaxRate(BigDecimal.ZERO);
        product.setMinStock(BigDecimal.ZERO);
        product.setMaxStock(BigDecimal.valueOf(100));
        product.setTrackBatch(false);
        product.setTrackExpiry(false);
        product.setActive(true);
        product = productRepository.save(product);

        username = "arcv." + suffix;
        User user = new User(username, passwordEncoder.encode("Receive123!"), "Atomic", "Receive");
        user.setEmail(username + "@test.com");
        user.assignRole(roleRepository.findByName(RoleName.INVENTORY_MANAGER).orElseThrow());
        user.assignStore(store);
        userRepository.save(user);

        String payload = String.format(
                "{\"storeId\":\"%s\",\"productId\":\"%s\",\"quantity\":5}",
                store.getId(),
                product.getId());

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
        assertThat(balances).isZero();
        assertThat(ledger).isZero();
    }
}

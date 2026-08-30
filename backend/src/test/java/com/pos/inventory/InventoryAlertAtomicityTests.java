package com.pos.inventory;

import com.pos.AbstractIntegrationTest;
import com.pos.audit.domain.AuditEvent;
import com.pos.audit.repository.AuditLogRepository;
import com.pos.audit.service.AuditRecorder;
import com.pos.auth.security.JwtTokenProvider;
import com.pos.catalog.entity.Product;
import com.pos.catalog.repository.ProductRepository;
import com.pos.inventory.domain.StockAlert;
import com.pos.inventory.repository.StockAlertRepository;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(InventoryAlertAtomicityTests.FailingAuditConfiguration.class)
class InventoryAlertAtomicityTests extends AbstractIntegrationTest {

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
    @Autowired private StockAlertRepository alertRepository;
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
    void auditFailureRollsBackAcknowledge() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Store store = storeRepository.save(new Store("AALT-" + suffix, "Atomic Alert " + suffix, "USD", "UTC"));
        Product product = new Product();
        product.setSku("AALT-SKU-" + suffix);
        product.setName("Atomic Alert " + suffix);
        product.setPurchasePrice(BigDecimal.TEN);
        product.setSellingPrice(BigDecimal.valueOf(20));
        product.setTaxRate(BigDecimal.ZERO);
        product.setMinStock(new BigDecimal("5"));
        product.setMaxStock(BigDecimal.valueOf(100));
        product.setTrackBatch(false);
        product.setTrackExpiry(false);
        product.setActive(true);
        product = productRepository.save(product);

        StockAlert alert = alertRepository.save(StockAlert.lowStock(store, product, BigDecimal.ONE, new BigDecimal("5")));

        username = "aalt." + suffix;
        User user = new User(username, passwordEncoder.encode("Alert123!"), "Atomic", "Alert");
        user.setEmail(username + "@test.com");
        user.assignRole(roleRepository.findByName(RoleName.INVENTORY_MANAGER).orElseThrow());
        user.assignStore(store);
        userRepository.save(user);

        mockMvc.perform(patch("/api/v1/inventory/alerts/" + alert.getId() + "/acknowledge")
                        .header("Authorization", "Bearer " + jwtTokenProvider.generateToken(username)))
                .andExpect(status().isInternalServerError());

        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM stock_alerts WHERE id = ?",
                String.class,
                alert.getId());
        assertThat(status).isEqualTo("OPEN");
    }
}

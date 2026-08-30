package com.pos.inventory;

import com.pos.AbstractIntegrationTest;
import com.pos.auth.security.JwtTokenProvider;
import com.pos.catalog.entity.Product;
import com.pos.catalog.repository.ProductRepository;
import com.pos.inventory.domain.InventoryBalance;
import com.pos.inventory.domain.TransactionType;
import com.pos.inventory.repository.InventoryBalanceRepository;
import com.pos.inventory.repository.InventoryTransactionRepository;
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
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class InventoryReceiptApiIntegrationTests extends AbstractIntegrationTest {

    private static final String ENDPOINT = "/api/v1/inventory/receipts";

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private StoreRepository storeRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private InventoryBalanceRepository balanceRepository;
    @Autowired private InventoryTransactionRepository transactionRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void receivesStockAndWritesLedgerAndAudit() throws Exception {
        Fixture fx = fixture(RoleName.INVENTORY_MANAGER);

        mockMvc.perform(post(ENDPOINT)
                        .header("Authorization", fx.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(fx.store.getId(), fx.product.getId(), "12.5000")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.quantity").value(12.5))
                .andExpect(jsonPath("$.data.productId").value(fx.product.getId().toString()))
                .andExpect(jsonPath("$.data.storeId").value(fx.store.getId().toString()));

        InventoryBalance balance = balanceRepository
                .findByProductIdAndStoreIdForUpdate(fx.product.getId(), fx.store.getId())
                .orElseThrow();
        assertThat(balance.getQuantity()).isEqualByComparingTo("12.5000");

        var ledger = transactionRepository.findAll().stream()
                .filter(t -> t.getProduct().getId().equals(fx.product.getId()))
                .toList();
        assertThat(ledger).hasSize(1);
        assertThat(ledger.get(0).getTransactionType()).isEqualTo(TransactionType.RECEIPT);
        assertThat(ledger.get(0).getQuantity()).isEqualByComparingTo("12.5000");
        assertThat(ledger.get(0).getReferenceType()).isNull();
        assertThat(ledger.get(0).getReferenceId()).isNull();

        Map<String, Object> audit = jdbcTemplate.queryForMap(
                "SELECT action, entity_type, entity_id, actor_user_id, old_values::text AS old_values, new_values::text AS new_values"
                        + " FROM audit_logs WHERE action = 'STOCK_RECEIPT' AND entity_id = ?",
                ledger.get(0).getId());
        assertThat(audit.get("entity_type")).isEqualTo("InventoryTransaction");
        assertThat(audit.get("actor_user_id")).isEqualTo(fx.user.getId());
        assertThat((String) audit.get("old_values")).contains("0.0000");
        assertThat((String) audit.get("new_values")).contains("12.5");
    }

    @Test
    void firstReceiptCreatesMissingBalance() throws Exception {
        Fixture fx = fixture(RoleName.STORE_MANAGER);
        assertThat(balanceRepository.findByProductIdAndStoreIdForUpdate(fx.product.getId(), fx.store.getId()))
                .isEmpty();

        mockMvc.perform(post(ENDPOINT)
                        .header("Authorization", fx.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(fx.store.getId(), fx.product.getId(), "3")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.quantity").value(3));

        assertThat(balanceRepository.findByProductIdAndStoreIdForUpdate(fx.product.getId(), fx.store.getId()))
                .isPresent();
    }

    @Test
    void subsequentReceiptAddsToExistingBalance() throws Exception {
        Fixture fx = fixture(RoleName.SUPER_ADMINISTRATOR);
        InventoryBalance existing = new InventoryBalance(fx.product, fx.store);
        existing.addQuantity(new BigDecimal("4.0000"));
        balanceRepository.saveAndFlush(existing);

        mockMvc.perform(post(ENDPOINT)
                        .header("Authorization", fx.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(fx.store.getId(), fx.product.getId(), "1.2500")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.quantity").value(5.25));
    }

    @Test
    void zeroQuantityIsValidationError() throws Exception {
        Fixture fx = fixture(RoleName.INVENTORY_MANAGER);

        mockMvc.perform(post(ENDPOINT)
                        .header("Authorization", fx.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(fx.store.getId(), fx.product.getId(), "0")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        assertNoLedger(fx);
    }

    @Test
    void negativeQuantityIsValidationError() throws Exception {
        Fixture fx = fixture(RoleName.INVENTORY_MANAGER);

        mockMvc.perform(post(ENDPOINT)
                        .header("Authorization", fx.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(fx.store.getId(), fx.product.getId(), "-2")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        assertNoLedger(fx);
    }

    @Test
    void missingFieldsAreValidationError() throws Exception {
        Fixture fx = fixture(RoleName.INVENTORY_MANAGER);

        mockMvc.perform(post(ENDPOINT)
                        .header("Authorization", fx.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"storeId\":\"" + fx.store.getId() + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void unauthenticatedRequestIsRejected() throws Exception {
        Fixture fx = fixture(RoleName.INVENTORY_MANAGER);

        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(fx.store.getId(), fx.product.getId(), "1")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void cashierWithoutReceivePermissionIsForbidden() throws Exception {
        Fixture fx = fixture(RoleName.CASHIER);

        mockMvc.perform(post(ENDPOINT)
                        .header("Authorization", fx.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(fx.store.getId(), fx.product.getId(), "1")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ACCESS_DENIED"));

        assertNoLedger(fx);
    }

    @Test
    void wrongStoreScopeIsForbidden() throws Exception {
        Fixture fx = fixture(RoleName.INVENTORY_MANAGER);
        Store other = storeRepository.save(new Store("OTH-" + idSuffix(), "Other Store", "USD", "UTC"));

        mockMvc.perform(post(ENDPOINT)
                        .header("Authorization", fx.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(other.getId(), fx.product.getId(), "1")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ACCESS_DENIED"));

        assertNoLedger(fx);
    }

    @Test
    void unknownProductIsNotFound() throws Exception {
        Fixture fx = fixture(RoleName.INVENTORY_MANAGER);

        mockMvc.perform(post(ENDPOINT)
                        .header("Authorization", fx.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(fx.store.getId(), UUID.randomUUID(), "1")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void unknownStoreIsNotFound() throws Exception {
        Fixture fx = fixture(RoleName.INVENTORY_MANAGER);

        mockMvc.perform(post(ENDPOINT)
                        .header("Authorization", fx.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(UUID.randomUUID(), fx.product.getId(), "1")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void inactiveProductIsConflict() throws Exception {
        Fixture fx = fixture(RoleName.INVENTORY_MANAGER);
        fx.product.setActive(false);
        productRepository.saveAndFlush(fx.product);

        mockMvc.perform(post(ENDPOINT)
                        .header("Authorization", fx.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(fx.store.getId(), fx.product.getId(), "1")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("RESOURCE_INACTIVE"));

        assertNoLedger(fx);
    }

    @Test
    void inactiveStoreIsConflict() throws Exception {
        Fixture fx = fixture(RoleName.INVENTORY_MANAGER);
        fx.store.setActive(false);
        storeRepository.saveAndFlush(fx.store);

        mockMvc.perform(post(ENDPOINT)
                        .header("Authorization", fx.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(fx.store.getId(), fx.product.getId(), "1")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("RESOURCE_INACTIVE"));

        assertNoLedger(fx);
    }

    @Test
    void adjustmentBehaviourIsUnchangedForInactiveProduct() throws Exception {
        Fixture fx = fixture(RoleName.INVENTORY_MANAGER);
        fx.product.setActive(false);
        productRepository.saveAndFlush(fx.product);

        String payload = String.format(
                "{\"storeId\":\"%s\",\"productId\":\"%s\",\"quantity\":2,\"reason\":\"3.1 unchanged\"}",
                fx.store.getId(),
                fx.product.getId());

        mockMvc.perform(post("/api/v1/inventory/adjustments")
                        .header("Authorization", fx.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.quantity").value(2));
    }

    private void assertNoLedger(Fixture fx) {
        long count = transactionRepository.findAll().stream()
                .filter(t -> t.getProduct().getId().equals(fx.product.getId()))
                .count();
        assertThat(count).isZero();
        assertThat(balanceRepository.findByProductIdAndStoreIdForUpdate(fx.product.getId(), fx.store.getId()))
                .isEmpty();
    }

    private Fixture fixture(String roleName) {
        String suffix = idSuffix();
        Store store = storeRepository.save(new Store("RCV-" + suffix, "Receive Store " + suffix, "USD", "UTC"));
        Product product = new Product();
        product.setSku("RCV-SKU-" + suffix);
        product.setName("Receive Product " + suffix);
        product.setPurchasePrice(BigDecimal.TEN);
        product.setSellingPrice(BigDecimal.valueOf(20));
        product.setTaxRate(BigDecimal.ZERO);
        product.setMinStock(BigDecimal.ZERO);
        product.setMaxStock(BigDecimal.valueOf(100));
        product.setTrackBatch(false);
        product.setTrackExpiry(false);
        product.setActive(true);
        product = productRepository.save(product);

        User user = new User("rcv." + suffix, passwordEncoder.encode("Receive123!"), "Receive", "User");
        user.setEmail("rcv." + suffix + "@test.com");
        user.assignRole(roleRepository.findByName(roleName).orElseThrow());
        user.assignStore(store);
        user = userRepository.save(user);

        return new Fixture(store, product, user);
    }

    private String body(UUID storeId, UUID productId, String quantity) {
        return String.format(
                "{\"storeId\":\"%s\",\"productId\":\"%s\",\"quantity\":%s}",
                storeId,
                productId,
                quantity);
    }

    private static String idSuffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private final class Fixture {
        final Store store;
        final Product product;
        final User user;

        Fixture(Store store, Product product, User user) {
            this.store = store;
            this.product = product;
            this.user = user;
        }

        String bearer() {
            return "Bearer " + jwtTokenProvider.generateToken(user.getUsername());
        }
    }
}

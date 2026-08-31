package com.pos.inventory;

import com.pos.AbstractIntegrationTest;
import com.pos.auth.security.JwtTokenProvider;
import com.pos.catalog.entity.Product;
import com.pos.catalog.repository.ProductRepository;
import com.pos.inventory.domain.InventoryBatch;
import com.pos.inventory.domain.TransactionType;
import com.pos.inventory.repository.InventoryBatchRepository;
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
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class InventoryBatchApiIntegrationTests extends AbstractIntegrationTest {

    private static final String RECEIPTS = "/api/v1/inventory/receipts";
    private static final String BATCHES = "/api/v1/inventory/batches";
    private static final String EXPIRY = "/api/v1/inventory/expiry";

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private StoreRepository storeRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private InventoryBatchRepository batchRepository;
    @Autowired private InventoryTransactionRepository transactionRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void receiveWithLotCreatesBatchLedgerAndListsIt() throws Exception {
        Fixture fx = trackingFixture(true, true);
        LocalDate expiry = LocalDate.now().plusDays(20);

        mockMvc.perform(post(RECEIPTS)
                        .header("Authorization", fx.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(receiptBody(fx, "4.0000", "LOT-A", expiry.toString(), "2026-01-15")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.quantity").value(4.0));

        InventoryBatch batch = batchRepository
                .findByProductIdAndStoreIdAndBatchNumberForUpdate(fx.product.getId(), fx.store.getId(), "LOT-A")
                .orElseThrow();
        assertThat(batch.getQuantity()).isEqualByComparingTo("4.0000");
        assertThat(batch.getExpirationDate()).isEqualTo(expiry);
        assertThat(batch.getManufacturingDate()).isEqualTo(LocalDate.of(2026, 1, 15));

        var ledger = transactionRepository.findAll().stream()
                .filter(t -> t.getProduct().getId().equals(fx.product.getId()))
                .toList();
        assertThat(ledger).hasSize(1);
        assertThat(ledger.get(0).getTransactionType()).isEqualTo(TransactionType.RECEIPT);
        assertThat(ledger.get(0).getBatchId()).isEqualTo(batch.getId());

        Map<String, Object> audit = jdbcTemplate.queryForMap(
                "SELECT action, old_values::text AS old_values, new_values::text AS new_values"
                        + " FROM audit_logs WHERE action = 'STOCK_RECEIPT' AND entity_id = ?",
                ledger.get(0).getId());
        assertThat((String) audit.get("old_values")).contains("0.0000");
        assertThat((String) audit.get("new_values")).contains("4");

        mockMvc.perform(get(BATCHES)
                        .header("Authorization", fx.bearer())
                        .param("storeId", fx.store.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].batchNumber").value("LOT-A"))
                .andExpect(jsonPath("$.data.content[0].productId").value(fx.product.getId().toString()))
                .andExpect(jsonPath("$.data.content[0].storeId").value(fx.store.getId().toString()))
                .andExpect(jsonPath("$.data.content[0].quantity").value(4.0))
                .andExpect(jsonPath("$.data.content[0].expirationDate").value(expiry.toString()))
                .andExpect(jsonPath("$.data.content[0].status").value("OK"));
    }

    @Test
    void sameLotReceiptAddsQuantity() throws Exception {
        Fixture fx = trackingFixture(true, true);
        LocalDate expiry = LocalDate.now().plusDays(40);

        mockMvc.perform(post(RECEIPTS)
                        .header("Authorization", fx.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(receiptBody(fx, "2", "LOT-B", expiry.toString(), null)))
                .andExpect(status().isOk());
        mockMvc.perform(post(RECEIPTS)
                        .header("Authorization", fx.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(receiptBody(fx, "3", "LOT-B", expiry.toString(), null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.quantity").value(5));

        InventoryBatch batch = batchRepository
                .findByProductIdAndStoreIdAndBatchNumberForUpdate(fx.product.getId(), fx.store.getId(), "LOT-B")
                .orElseThrow();
        assertThat(batch.getQuantity()).isEqualByComparingTo("5.0000");
    }

    @Test
    void conflictingExpiryOnSameLotIsBusinessRuleViolation() throws Exception {
        Fixture fx = trackingFixture(true, true);
        LocalDate expiry = LocalDate.now().plusDays(15);

        mockMvc.perform(post(RECEIPTS)
                        .header("Authorization", fx.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(receiptBody(fx, "1", "LOT-C", expiry.toString(), null)))
                .andExpect(status().isOk());

        mockMvc.perform(post(RECEIPTS)
                        .header("Authorization", fx.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(receiptBody(fx, "1", "LOT-C", expiry.plusDays(1).toString(), null)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("BUSINESS_RULE_VIOLATION"));
    }

    @Test
    void trackingProductWithoutBatchNumberIsBusinessRuleViolation() throws Exception {
        Fixture fx = trackingFixture(true, false);

        mockMvc.perform(post(RECEIPTS)
                        .header("Authorization", fx.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(receiptBody(fx, "1", null, null, null)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("BUSINESS_RULE_VIOLATION"));

        assertThat(batchCountFor(fx)).isZero();
    }

    @Test
    void expiryTrackingProductWithoutExpirationDateIsBusinessRuleViolation() throws Exception {
        Fixture fx = trackingFixture(false, true);

        mockMvc.perform(post(RECEIPTS)
                        .header("Authorization", fx.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(receiptBody(fx, "1", "LOT-D", null, null)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("BUSINESS_RULE_VIOLATION"));

        assertThat(batchCountFor(fx)).isZero();
    }

    @Test
    void nonTrackingReceiptStillCreatesNoBatch() throws Exception {
        Fixture fx = trackingFixture(false, false);

        mockMvc.perform(post(RECEIPTS)
                        .header("Authorization", fx.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(receiptBody(fx, "6", null, null, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.quantity").value(6));

        assertThat(batchCountFor(fx)).isZero();
        var ledger = transactionRepository.findAll().stream()
                .filter(t -> t.getProduct().getId().equals(fx.product.getId()))
                .toList();
        assertThat(ledger.get(0).getBatchId()).isNull();
    }

    @Test
    void listBatchesWithoutPermissionIsForbidden() throws Exception {
        Fixture fx = cashierFixture();

        mockMvc.perform(get(BATCHES)
                        .header("Authorization", fx.bearer())
                        .param("storeId", fx.store.getId().toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ACCESS_DENIED"));
    }

    @Test
    void listBatchesUnauthenticatedIsRejected() throws Exception {
        Fixture fx = trackingFixture(true, true);

        mockMvc.perform(get(BATCHES).param("storeId", fx.store.getId().toString()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void listBatchesWrongStoreIsForbidden() throws Exception {
        Fixture fx = trackingFixture(true, true);
        Store other = storeRepository.save(new Store("BTH-" + idSuffix(), "Other Batch Store", "USD", "UTC"));

        mockMvc.perform(get(BATCHES)
                        .header("Authorization", fx.bearer())
                        .param("storeId", other.getId().toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ACCESS_DENIED"));
    }

    @Test
    void listExpiryWrongStoreIsForbidden() throws Exception {
        Fixture fx = trackingFixture(true, true);
        Store other = storeRepository.save(new Store("EXP-" + idSuffix(), "Other Expiry Store", "USD", "UTC"));

        mockMvc.perform(get(EXPIRY)
                        .header("Authorization", fx.bearer())
                        .param("storeId", other.getId().toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ACCESS_DENIED"));
    }

    @Test
    void negativeDaysIsValidationError() throws Exception {
        Fixture fx = trackingFixture(true, true);

        mockMvc.perform(get(EXPIRY)
                        .header("Authorization", fx.bearer())
                        .param("storeId", fx.store.getId().toString())
                        .param("days", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void adjustmentBehaviourIsUnchangedWhenProductTracksBatch() throws Exception {
        Fixture fx = trackingFixture(true, true);

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

        assertThat(batchCountFor(fx)).isZero();
    }

    private Fixture trackingFixture(boolean trackBatch, boolean trackExpiry) {
        return fixture(RoleName.INVENTORY_MANAGER, trackBatch, trackExpiry);
    }

    private Fixture cashierFixture() {
        return fixture(RoleName.CASHIER, false, false);
    }

    private Fixture fixture(String roleName, boolean trackBatch, boolean trackExpiry) {
        String suffix = idSuffix();
        Store store = storeRepository.save(new Store("BAT-" + suffix, "Batch Store " + suffix, "USD", "UTC"));
        Product product = new Product();
        product.setSku("BAT-SKU-" + suffix);
        product.setName("Batch Product " + suffix);
        product.setPurchasePrice(BigDecimal.TEN);
        product.setSellingPrice(BigDecimal.valueOf(20));
        product.setTaxRate(BigDecimal.ZERO);
        product.setMinStock(BigDecimal.ZERO);
        product.setMaxStock(BigDecimal.valueOf(100));
        product.setTrackBatch(trackBatch);
        product.setTrackExpiry(trackExpiry);
        product.setActive(true);
        product = productRepository.save(product);

        User user = new User("bat." + suffix, passwordEncoder.encode("Batch123!"), "Batch", "User");
        user.setEmail("bat." + suffix + "@test.com");
        user.assignRole(roleRepository.findByName(roleName).orElseThrow());
        user.assignStore(store);
        user = userRepository.save(user);

        return new Fixture(store, product, user);
    }

    private String receiptBody(Fixture fx, String quantity, String batchNumber, String expirationDate, String manufacturingDate) {
        StringBuilder json = new StringBuilder();
        json.append("{\"storeId\":\"").append(fx.store.getId()).append("\",");
        json.append("\"productId\":\"").append(fx.product.getId()).append("\",");
        json.append("\"quantity\":").append(quantity);
        if (batchNumber != null) {
            json.append(",\"batchNumber\":\"").append(batchNumber).append("\"");
        }
        if (expirationDate != null) {
            json.append(",\"expirationDate\":\"").append(expirationDate).append("\"");
        }
        if (manufacturingDate != null) {
            json.append(",\"manufacturingDate\":\"").append(manufacturingDate).append("\"");
        }
        json.append("}");
        return json.toString();
    }

    /**
     * Counts lots for this fixture only. The suite shares one PostgreSQL container, and
     * non-transactional concurrency tests leave committed rows, so a global {@code findAll()}
     * emptiness check is not a property of the behaviour under test.
     */
    private long batchCountFor(Fixture fx) {
        Long count =
                jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM inventory_batches WHERE product_id = ? AND store_id = ?",
                        Long.class,
                        fx.product.getId(),
                        fx.store.getId());
        return count == null ? 0L : count;
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

package com.pos.inventory;

import com.pos.AbstractIntegrationTest;
import com.pos.auth.security.JwtTokenProvider;
import com.pos.catalog.entity.Product;
import com.pos.catalog.repository.ProductRepository;
import com.pos.inventory.domain.InventoryBalance;
import com.pos.inventory.domain.InventoryBatch;
import com.pos.inventory.domain.StockAlert;
import com.pos.inventory.repository.InventoryBalanceRepository;
import com.pos.inventory.repository.InventoryBatchRepository;
import com.pos.inventory.repository.StockAlertRepository;
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
import jakarta.persistence.EntityManager;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class InventoryAlertApiIntegrationTests extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private StoreRepository storeRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private InventoryBalanceRepository balanceRepository;
    @Autowired private InventoryBatchRepository batchRepository;
    @Autowired private StockAlertRepository alertRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private EntityManager entityManager;

    @Test
    void adjustmentAtOrBelowMinStockCreatesLowStockAlert() throws Exception {
        Fixture fx = fixture(RoleName.INVENTORY_MANAGER, new BigDecimal("5"));

        mockMvc.perform(post("/api/v1/inventory/adjustments")
                        .header("Authorization", fx.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(adjustBody(fx, "4", "BELOW_MIN")))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/inventory/alerts")
                        .header("Authorization", fx.bearer())
                        .param("storeId", fx.store.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].alertType").value("LOW_STOCK"))
                .andExpect(jsonPath("$.data.content[0].status").value("OPEN"))
                .andExpect(jsonPath("$.data.content[0].quantity").value(4.0))
                .andExpect(jsonPath("$.data.content[0].minimumLevel").value(5.0))
                .andExpect(jsonPath("$.data.content[0].suggestedAction").value("Reorder"));
    }

    @Test
    void receiptAboveMinStockClearsLowStockAlert() throws Exception {
        Fixture fx = fixture(RoleName.INVENTORY_MANAGER, new BigDecimal("5"));
        seedBalance(fx, new BigDecimal("3"));
        alertRepository.save(StockAlert.lowStock(fx.store, fx.product, new BigDecimal("3"), new BigDecimal("5")));

        mockMvc.perform(post("/api/v1/inventory/receipts")
                        .header("Authorization", fx.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(receiptBody(fx, "4")))
                .andExpect(status().isOk());

        assertThat(alertRepository.findByStoreIdAndAlertType(fx.store.getId(), StockAlert.TYPE_LOW_STOCK)).isEmpty();
    }

    @Test
    void expiryRefreshCreatesAlertIdentifyingProductBatchDateAndQuantity() throws Exception {
        Fixture fx = fixture(RoleName.INVENTORY_MANAGER, BigDecimal.ZERO);
        LocalDate expired = LocalDate.now().minusDays(1);
        InventoryBatch batch = new InventoryBatch(fx.product, fx.store, "LOT-EXP");
        batch.setExpirationDate(expired);
        batch.addQuantity(new BigDecimal("8"));
        batchRepository.save(batch);

        mockMvc.perform(get("/api/v1/inventory/alerts")
                        .header("Authorization", fx.bearer())
                        .param("storeId", fx.store.getId().toString())
                        .param("days", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[?(@.alertType=='EXPIRY')].batchNumber").value(hasItem("LOT-EXP")))
                .andExpect(jsonPath("$.data.content[?(@.alertType=='EXPIRY')].expirationDate").value(hasItem(expired.toString())))
                .andExpect(jsonPath("$.data.content[?(@.alertType=='EXPIRY')].quantity").value(hasItem(8)))
                .andExpect(jsonPath("$.data.content[?(@.alertType=='EXPIRY')].status").value(hasItem("OPEN")));
    }

    @Test
    void acknowledgeSetsStatusAndWritesAudit() throws Exception {
        Fixture fx = fixture(RoleName.STORE_MANAGER, new BigDecimal("2"));
        StockAlert alert = alertRepository.save(
                StockAlert.lowStock(fx.store, fx.product, new BigDecimal("1"), new BigDecimal("2")));

        mockMvc.perform(patch("/api/v1/inventory/alerts/" + alert.getId() + "/acknowledge")
                        .header("Authorization", fx.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACKNOWLEDGED"));

        entityManager.flush();
        Map<String, Object> audit = jdbcTemplate.queryForMap(
                "SELECT action, entity_type, actor_user_id FROM audit_logs WHERE action = 'ALERT_ACKNOWLEDGE' AND entity_id = ?",
                alert.getId());
        assertThat(audit.get("entity_type")).isEqualTo("StockAlert");
        assertThat(audit.get("actor_user_id")).isEqualTo(fx.user.getId());
    }

    @Test
    void cashierWithoutInventoryReadIsForbidden() throws Exception {
        Fixture fx = fixture(RoleName.CASHIER, BigDecimal.ZERO);

        mockMvc.perform(get("/api/v1/inventory/alerts")
                        .header("Authorization", fx.bearer())
                        .param("storeId", fx.store.getId().toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ACCESS_DENIED"));
    }

    @Test
    void listAlertsWrongStoreIsForbidden() throws Exception {
        Fixture fx = fixture(RoleName.INVENTORY_MANAGER, BigDecimal.ZERO);
        Store other = storeRepository.save(new Store("ALT-" + idSuffix(), "Other Alert Store", "USD", "UTC"));

        mockMvc.perform(get("/api/v1/inventory/alerts")
                        .header("Authorization", fx.bearer())
                        .param("storeId", other.getId().toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ACCESS_DENIED"));
    }

    @Test
    void acknowledgeWrongStoreIsForbidden() throws Exception {
        Fixture fx = fixture(RoleName.INVENTORY_MANAGER, BigDecimal.ZERO);
        Store other = storeRepository.save(new Store("AAO-" + idSuffix(), "Ack Other", "USD", "UTC"));
        Product otherProduct = newProduct("AAO-SKU-" + idSuffix(), BigDecimal.ONE);
        StockAlert alert = alertRepository.save(StockAlert.lowStock(other, otherProduct, BigDecimal.ZERO, BigDecimal.ONE));

        mockMvc.perform(patch("/api/v1/inventory/alerts/" + alert.getId() + "/acknowledge")
                        .header("Authorization", fx.bearer()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ACCESS_DENIED"));
    }

    @Test
    void acknowledgeUnknownIdIsNotFound() throws Exception {
        Fixture fx = fixture(RoleName.INVENTORY_MANAGER, BigDecimal.ZERO);

        mockMvc.perform(patch("/api/v1/inventory/alerts/" + UUID.randomUUID() + "/acknowledge")
                        .header("Authorization", fx.bearer()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void unauthenticatedAlertListIsRejected() throws Exception {
        Fixture fx = fixture(RoleName.INVENTORY_MANAGER, BigDecimal.ZERO);

        mockMvc.perform(get("/api/v1/inventory/alerts").param("storeId", fx.store.getId().toString()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adjustmentBehaviourStillAllowsInactiveProduct() throws Exception {
        Fixture fx = fixture(RoleName.INVENTORY_MANAGER, new BigDecimal("10"));
        fx.product.setActive(false);
        productRepository.saveAndFlush(fx.product);

        mockMvc.perform(post("/api/v1/inventory/adjustments")
                        .header("Authorization", fx.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(adjustBody(fx, "1", "3.1 unchanged")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.quantity").value(1));
    }

    private void seedBalance(Fixture fx, BigDecimal qty) {
        InventoryBalance balance = new InventoryBalance(fx.product, fx.store);
        balance.addQuantity(qty);
        balanceRepository.save(balance);
    }

    private Fixture fixture(String roleName, BigDecimal minStock) {
        String suffix = idSuffix();
        Store store = storeRepository.save(new Store("ALT-" + suffix, "Alert Store " + suffix, "USD", "UTC"));
        Product product = newProduct("ALT-SKU-" + suffix, minStock);
        User user = new User("alt." + suffix, passwordEncoder.encode("Alert123!"), "Alert", "User");
        user.setEmail("alt." + suffix + "@test.com");
        user.assignRole(roleRepository.findByName(roleName).orElseThrow());
        user.assignStore(store);
        user = userRepository.save(user);
        return new Fixture(store, product, user);
    }

    private Product newProduct(String sku, BigDecimal minStock) {
        Product product = new Product();
        product.setSku(sku);
        product.setName("Alert " + sku);
        product.setPurchasePrice(BigDecimal.TEN);
        product.setSellingPrice(BigDecimal.valueOf(20));
        product.setTaxRate(BigDecimal.ZERO);
        product.setMinStock(minStock);
        product.setMaxStock(BigDecimal.valueOf(100));
        product.setTrackBatch(false);
        product.setTrackExpiry(false);
        product.setActive(true);
        return productRepository.save(product);
    }

    private String adjustBody(Fixture fx, String qty, String reason) {
        return String.format(
                "{\"storeId\":\"%s\",\"productId\":\"%s\",\"quantity\":%s,\"reason\":\"%s\"}",
                fx.store.getId(), fx.product.getId(), qty, reason);
    }

    private String receiptBody(Fixture fx, String qty) {
        return String.format(
                "{\"storeId\":\"%s\",\"productId\":\"%s\",\"quantity\":%s}",
                fx.store.getId(), fx.product.getId(), qty);
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

package com.pos.inventory;

import com.pos.AbstractIntegrationTest;
import com.pos.auth.security.JwtTokenProvider;
import com.pos.catalog.entity.Product;
import com.pos.catalog.repository.ProductRepository;
import com.pos.inventory.domain.InventoryBalance;
import com.pos.inventory.domain.InventoryBatch;
import com.pos.inventory.repository.InventoryBalanceRepository;
import com.pos.inventory.repository.InventoryBatchRepository;
import com.pos.organization.domain.Store;
import com.pos.organization.repository.StoreRepository;
import com.pos.users.domain.RoleName;
import com.pos.users.domain.User;
import com.pos.users.repository.RoleRepository;
import com.pos.users.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class InventoryReportApiIntegrationTests extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private StoreRepository storeRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private InventoryBalanceRepository balanceRepository;
    @Autowired private InventoryBatchRepository batchRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @Test
    void inventoryReportIncludesMinStockAndLowStockFilter() throws Exception {
        Fixture fx = fixture(RoleName.INVENTORY_MANAGER);
        seedBalance(fx, new BigDecimal("2"));

        mockMvc.perform(get("/api/v1/reports/inventory")
                        .header("Authorization", fx.bearer())
                        .param("storeId", fx.store.getId().toString())
                        .param("lowStockOnly", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].minStock").value(5.0))
                .andExpect(jsonPath("$.data.content[0].belowMinimum").value(true))
                .andExpect(jsonPath("$.data.content[0].quantity").value(2.0));
    }

    @Test
    void movementReportReturnsStoreLedger() throws Exception {
        Fixture fx = fixture(RoleName.STORE_MANAGER);

        mockMvc.perform(post("/api/v1/inventory/receipts")
                        .header("Authorization", fx.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"storeId\":\"%s\",\"productId\":\"%s\",\"quantity\":3}",
                                fx.store.getId(), fx.product.getId())))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/reports/inventory/movements")
                        .header("Authorization", fx.bearer())
                        .param("storeId", fx.store.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].transactionType").value("RECEIPT"))
                .andExpect(jsonPath("$.data.content[0].quantity").value(3.0));
    }

    @Test
    void expiryReportReturnsExpiringBatches() throws Exception {
        Fixture fx = fixture(RoleName.ACCOUNTANT);
        InventoryBatch batch = new InventoryBatch(fx.product, fx.store, "LOT-RPT");
        batch.setExpirationDate(LocalDate.now().plusDays(3));
        batch.addQuantity(BigDecimal.TEN);
        batchRepository.save(batch);

        mockMvc.perform(get("/api/v1/reports/inventory/expiry")
                        .header("Authorization", fx.bearer())
                        .param("storeId", fx.store.getId().toString())
                        .param("days", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].batchNumber").value("LOT-RPT"));
    }

    @Test
    void cashierWithoutReportPermissionIsForbidden() throws Exception {
        Fixture fx = fixture(RoleName.CASHIER);

        mockMvc.perform(get("/api/v1/reports/inventory")
                        .header("Authorization", fx.bearer())
                        .param("storeId", fx.store.getId().toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ACCESS_DENIED"));
    }

    @Test
    void reportWrongStoreIsForbidden() throws Exception {
        Fixture fx = fixture(RoleName.INVENTORY_MANAGER);
        Store other = storeRepository.save(new Store("RPT-" + idSuffix(), "Other Report", "USD", "UTC"));

        mockMvc.perform(get("/api/v1/reports/inventory")
                        .header("Authorization", fx.bearer())
                        .param("storeId", other.getId().toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ACCESS_DENIED"));
    }

    private void seedBalance(Fixture fx, BigDecimal qty) {
        InventoryBalance balance = new InventoryBalance(fx.product, fx.store);
        balance.addQuantity(qty);
        balanceRepository.save(balance);
    }

    private Fixture fixture(String roleName) {
        String suffix = idSuffix();
        Store store = storeRepository.save(new Store("RPT-" + suffix, "Report Store " + suffix, "USD", "UTC"));
        Product product = new Product();
        product.setSku("RPT-SKU-" + suffix);
        product.setName("Report " + suffix);
        product.setPurchasePrice(BigDecimal.TEN);
        product.setSellingPrice(BigDecimal.valueOf(20));
        product.setTaxRate(BigDecimal.ZERO);
        product.setMinStock(new BigDecimal("5"));
        product.setMaxStock(BigDecimal.valueOf(100));
        product.setTrackBatch(false);
        product.setTrackExpiry(false);
        product.setActive(true);
        product = productRepository.save(product);

        User user = new User("rpt." + suffix, passwordEncoder.encode("Report123!"), "Report", "User");
        user.setEmail("rpt." + suffix + "@test.com");
        user.assignRole(roleRepository.findByName(roleName).orElseThrow());
        user.assignStore(store);
        user = userRepository.save(user);
        return new Fixture(store, product, user);
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

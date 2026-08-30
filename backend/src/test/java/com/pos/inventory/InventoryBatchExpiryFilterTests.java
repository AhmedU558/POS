package com.pos.inventory;

import com.pos.AbstractIntegrationTest;
import com.pos.auth.security.JwtTokenProvider;
import com.pos.catalog.entity.Product;
import com.pos.catalog.repository.ProductRepository;
import com.pos.inventory.domain.InventoryBatch;
import com.pos.inventory.repository.InventoryBatchRepository;
import com.pos.organization.domain.Store;
import com.pos.organization.repository.StoreRepository;
import com.pos.users.domain.RoleName;
import com.pos.users.domain.User;
import com.pos.users.repository.RoleRepository;
import com.pos.users.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Implementation Plan Phase 3 critical scenario: expiry filters return correct batches.
 */
@Transactional
class InventoryBatchExpiryFilterTests extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private StoreRepository storeRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private InventoryBatchRepository batchRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @Test
    void expiryFilterReturnsExpiredTodayAndApproachingOnly() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Store store = storeRepository.save(new Store("EFL-" + suffix, "Expiry Filter " + suffix, "USD", "UTC"));
        Store other = storeRepository.save(new Store("EFO-" + suffix, "Other Filter " + suffix, "USD", "UTC"));
        Product product = newProduct("EFL-SKU-" + suffix, true, true);
        Product otherProduct = newProduct("EFO-SKU-" + suffix, true, false);

        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        saveBatch(product, store, "EXPIRED", today.minusDays(2), new BigDecimal("1"));
        saveBatch(product, store, "TODAY", today, new BigDecimal("2"));
        saveBatch(product, store, "SOON", today.plusDays(5), new BigDecimal("3"));
        saveBatch(product, store, "LATER", today.plusDays(10), new BigDecimal("4"));
        InventoryBatch noDate = new InventoryBatch(otherProduct, store, "NODATE");
        noDate.addQuantity(new BigDecimal("5"));
        batchRepository.save(noDate);
        saveBatch(product, other, "OTHER-STORE", today.minusDays(1), new BigDecimal("6"));

        User user = new User("efl." + suffix, passwordEncoder.encode("Expiry123!"), "Expiry", "Filter");
        user.setEmail("efl." + suffix + "@test.com");
        user.assignRole(roleRepository.findByName(RoleName.INVENTORY_MANAGER).orElseThrow());
        user.assignStore(store);
        userRepository.save(user);
        String token = "Bearer " + jwtTokenProvider.generateToken(user.getUsername());

        mockMvc.perform(get("/api/v1/inventory/expiry")
                        .header("Authorization", token)
                        .param("storeId", store.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(3))
                .andExpect(jsonPath("$.data.content[?(@.batchNumber=='EXPIRED')].status").value(hasItem("EXPIRED")))
                .andExpect(jsonPath("$.data.content[?(@.batchNumber=='TODAY')].status").value(hasItem("EXPIRING_TODAY")))
                .andExpect(jsonPath("$.data.content[?(@.batchNumber=='SOON')].status").value(hasItem("APPROACHING")))
                .andExpect(jsonPath("$.data.content[?(@.batchNumber=='LATER')]").isEmpty())
                .andExpect(jsonPath("$.data.content[?(@.batchNumber=='NODATE')]").isEmpty())
                .andExpect(jsonPath("$.data.content[?(@.batchNumber=='OTHER-STORE')]").isEmpty());

        mockMvc.perform(get("/api/v1/inventory/expiry")
                        .header("Authorization", token)
                        .param("storeId", store.getId().toString())
                        .param("days", "30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(4))
                .andExpect(jsonPath("$.data.content[?(@.batchNumber=='LATER')].status").value(hasItem("APPROACHING")));

        mockMvc.perform(get("/api/v1/inventory/expiry")
                        .header("Authorization", token)
                        .param("storeId", store.getId().toString())
                        .param("days", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(2))
                .andExpect(jsonPath("$.data.content[?(@.batchNumber=='EXPIRED')]").exists())
                .andExpect(jsonPath("$.data.content[?(@.batchNumber=='TODAY')]").exists())
                .andExpect(jsonPath("$.data.content[?(@.batchNumber=='SOON')]").isEmpty());

        mockMvc.perform(get("/api/v1/inventory/batches")
                        .header("Authorization", token)
                        .param("storeId", store.getId().toString())
                        .param("days", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(5))
                .andExpect(jsonPath("$.data.content[?(@.batchNumber=='LATER')].status").value(hasItem("OK")))
                .andExpect(jsonPath("$.data.content[?(@.batchNumber=='NODATE')].status").value(hasItem("OK")));
    }

    private InventoryBatch saveBatch(Product product, Store store, String lot, LocalDate expiry, BigDecimal qty) {
        InventoryBatch batch = new InventoryBatch(product, store, lot);
        batch.setExpirationDate(expiry);
        batch.addQuantity(qty);
        return batchRepository.save(batch);
    }

    private Product newProduct(String sku, boolean trackBatch, boolean trackExpiry) {
        Product product = new Product();
        product.setSku(sku);
        product.setName("Expiry " + sku);
        product.setPurchasePrice(BigDecimal.TEN);
        product.setSellingPrice(BigDecimal.valueOf(20));
        product.setTaxRate(BigDecimal.ZERO);
        product.setMinStock(BigDecimal.ZERO);
        product.setMaxStock(BigDecimal.valueOf(100));
        product.setTrackBatch(trackBatch);
        product.setTrackExpiry(trackExpiry);
        product.setActive(true);
        return productRepository.save(product);
    }
}

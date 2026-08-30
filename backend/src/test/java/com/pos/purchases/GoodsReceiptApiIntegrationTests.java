package com.pos.purchases;

import com.pos.AbstractIntegrationTest;
import com.pos.auth.security.JwtTokenProvider;
import com.pos.catalog.entity.Product;
import com.pos.catalog.repository.ProductRepository;
import com.pos.inventory.repository.InventoryBalanceRepository;
import com.pos.organization.domain.Store;
import com.pos.organization.repository.StoreRepository;
import com.pos.users.domain.Role;
import com.pos.users.domain.RoleName;
import com.pos.users.domain.User;
import com.pos.users.repository.RoleRepository;
import com.pos.users.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class GoodsReceiptApiIntegrationTests extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ProductRepository productRepository;
    @Autowired private StoreRepository storeRepository;
    @Autowired private InventoryBalanceRepository balanceRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private EntityManager entityManager;

    @Test
    void receiveAgainstSubmittedPoUpdatesInventoryAndIsReadable() throws Exception {
        Fixture fx = fixture(RoleName.INVENTORY_MANAGER);
        String poId = createSubmittedPo(fx, "GR-PO-1");

        MvcResult created = mockMvc.perform(post("/api/v1/goods-receipts")
                        .header("Authorization", fx.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(receiptBody(poId, fx.store.getId(), fx.product.getId(), "4")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.purchaseOrderId").value(poId))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].quantity").value(4))
                .andExpect(jsonPath("$.data.items[0].sku").value(fx.product.getSku()))
                .andReturn();
        String receiptId = created.getResponse().getContentAsString().split("\"id\":\"")[1].split("\"")[0];

        mockMvc.perform(get("/api/v1/goods-receipts/" + receiptId)
                        .header("Authorization", fx.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(receiptId));

        assertThat(balanceRepository.findByProductIdAndStoreIdForUpdate(fx.product.getId(), fx.store.getId()))
                .isPresent()
                .get()
                .extracting(balance -> balance.getQuantity().intValue())
                .isEqualTo(4);
    }

    @Test
    void createWritesAudit() throws Exception {
        Fixture fx = fixture(RoleName.SUPER_ADMINISTRATOR);
        String poId = createSubmittedPo(fx, "GR-PO-AUD");

        mockMvc.perform(post("/api/v1/goods-receipts")
                        .header("Authorization", fx.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(receiptBody(poId, fx.store.getId(), fx.product.getId(), "1")))
                .andExpect(status().isOk());

        entityManager.flush();
        Map<String, Object> audit = jdbcTemplate.queryForMap(
                "SELECT action, entity_type FROM audit_logs WHERE action = 'GOODS_RECEIPT_CREATED'");
        assertThat(audit.get("entity_type")).isEqualTo("GoodsReceipt");
    }

    @Test
    void draftPoCannotBeReceived() throws Exception {
        Fixture fx = fixture(RoleName.INVENTORY_MANAGER);
        String poId = createDraftPo(fx, "GR-PO-DRAFT");

        mockMvc.perform(post("/api/v1/goods-receipts")
                        .header("Authorization", fx.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(receiptBody(poId, fx.store.getId(), fx.product.getId(), "1")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("BUSINESS_RULE_VIOLATION"));
    }

    @Test
    void productNotOnPoIsRejected() throws Exception {
        Fixture fx = fixture(RoleName.INVENTORY_MANAGER);
        String poId = createSubmittedPo(fx, "GR-PO-MISS");
        Product other = persistProduct("SKU-GR-OTHER");

        mockMvc.perform(post("/api/v1/goods-receipts")
                        .header("Authorization", fx.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(receiptBody(poId, fx.store.getId(), other.getId(), "1")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("BUSINESS_RULE_VIOLATION"));
    }

    @Test
    void cashierIsForbidden() throws Exception {
        Fixture fx = fixture(RoleName.CASHIER);

        mockMvc.perform(post("/api/v1/goods-receipts")
                        .header("Authorization", fx.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(receiptBody(UUID.randomUUID().toString(), fx.store.getId(), fx.product.getId(), "1")))
                .andExpect(status().isForbidden());
    }

    private String createSubmittedPo(Fixture fx, String poNumber) throws Exception {
        String id = createDraftPo(fx, poNumber);
        mockMvc.perform(post("/api/v1/purchase-orders/" + id + "/submit")
                        .header("Authorization", fx.bearer()))
                .andExpect(status().isOk());
        return id;
    }

    private String createDraftPo(Fixture fx, String poNumber) throws Exception {
        String supplierId = createSupplier(fx, poNumber + "-SUP");
        MvcResult created = mockMvc.perform(post("/api/v1/purchase-orders")
                        .header("Authorization", fx.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "poNumber": "%s",
                                    "supplierId": "%s",
                                    "items": [{"productId": "%s", "quantity": 5}]
                                }
                                """.formatted(poNumber, supplierId, fx.product.getId())))
                .andExpect(status().isOk())
                .andReturn();
        return created.getResponse().getContentAsString().split("\"id\":\"")[1].split("\"")[0];
    }

    private String createSupplier(Fixture fx, String code) throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/suppliers")
                        .header("Authorization", fx.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"supplierCode":"%s","name":"%s","isActive":true}
                                """.formatted(code, code)))
                .andExpect(status().isOk())
                .andReturn();
        return created.getResponse().getContentAsString().split("\"id\":\"")[1].split("\"")[0];
    }

    private Fixture fixture(String roleName) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Store store = storeRepository.save(new Store("GR-" + suffix, "GR Store " + suffix, "USD", "UTC"));
        Product product = persistProduct("SKU-GR-" + suffix);
        User user = new User("gr." + suffix, passwordEncoder.encode("Gr123456!"), "Gr", "User");
        user.setEmail("gr." + suffix + "@test.com");
        Role role = roleRepository.findByName(roleName).orElseThrow();
        user.assignRole(role);
        user.assignStore(store);
        user = userRepository.save(user);
        return new Fixture(store, product, user);
    }

    private Product persistProduct(String sku) {
        Product product = new Product();
        product.setSku(sku);
        product.setName(sku);
        product.setPurchasePrice(BigDecimal.TEN);
        product.setSellingPrice(BigDecimal.valueOf(20));
        product.setTaxRate(BigDecimal.ZERO);
        product.setMinStock(BigDecimal.ZERO);
        product.setTrackBatch(false);
        product.setTrackExpiry(false);
        product.setActive(true);
        return productRepository.save(product);
    }

    private static String receiptBody(String poId, UUID storeId, UUID productId, String quantity) {
        return """
                {
                    "purchaseOrderId": "%s",
                    "storeId": "%s",
                    "items": [{"productId": "%s", "quantity": %s}]
                }
                """.formatted(poId, storeId, productId, quantity);
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

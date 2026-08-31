package com.pos.demo;

import com.pos.AbstractIntegrationTest;
import com.pos.catalog.entity.Product;
import com.pos.catalog.repository.ProductRepository;
import com.pos.demo.service.DemoDataSeeder;
import com.pos.inventory.repository.InventoryBalanceRepository;
import com.pos.organization.domain.Store;
import com.pos.organization.repository.RegisterRepository;
import com.pos.organization.repository.StoreRepository;
import com.pos.organization.repository.TerminalRepository;
import com.pos.users.domain.RoleName;
import com.pos.users.domain.User;
import com.pos.users.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers the demonstration seeder and the runner that drives it.
 *
 * <p>The properties are set here rather than in the shared test configuration so that no other
 * test in the suite runs against a seeded database.
 *
 * <p>Enabling the property also means {@link DemoSeedRunner} seeds once while this class's
 * application context starts, outside any test transaction. That is the behaviour under test, so
 * the assertions describe the state the seeder guarantees rather than counting what one call
 * happened to create.
 */
@Transactional
@TestPropertySource(
        properties = {
            "app.demo.enabled=true",
            "app.demo.admin-username=seed.test.admin",
            "app.demo.cashier-username=seed.test.cashier",
            "app.demo.admin-password=demo-admin-password",
            "app.demo.cashier-password=demo-cashier-password"
        })
class DemoDataSeederIntegrationTests extends AbstractIntegrationTest {

    private static final List<String> DEMO_SKUS =
            List.of("DEMO-COLA", "DEMO-WATER", "DEMO-COFFEE", "DEMO-CRISPS", "DEMO-CHOC");

    @Autowired private DemoDataSeeder seeder;
    @Autowired private UserRepository userRepository;
    @Autowired private StoreRepository storeRepository;
    @Autowired private TerminalRepository terminalRepository;
    @Autowired private RegisterRepository registerRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private InventoryBalanceRepository balanceRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @Test
    void theRunnerSeedsAtStartupWhenEnabled() {
        // Nothing in this test has called the seeder: the accounts are here because the runner
        // executed while the context started, which is how an operator gets them.
        assertThat(userRepository.findByUsername("seed.test.admin")).isPresent();
        assertThat(userRepository.findByUsername("seed.test.cashier")).isPresent();
    }

    @Test
    void createsBothAccountsWithTheRightRolesAndStoreAccess() {
        User admin = userRepository.findByUsername("seed.test.admin").orElseThrow();
        assertThat(admin.getRoles()).extracting("name").containsExactly(RoleName.SUPER_ADMINISTRATOR);
        assertThat(admin.getStores()).isNotEmpty();

        User cashier = userRepository.findByUsername("seed.test.cashier").orElseThrow();
        assertThat(cashier.getRoles()).extracting("name").containsExactly(RoleName.CASHIER);
        assertThat(cashier.getStores()).isNotEmpty();
    }

    @Test
    void accountsCanSignInWithTheConfiguredPassword() {
        User admin = userRepository.findByUsername("seed.test.admin").orElseThrow();
        assertThat(passwordEncoder.matches("demo-admin-password", admin.getPasswordHash())).isTrue();
        // Unlike bootstrap, a demo account is usable immediately: the operator chose the password
        // themselves, so a rotation prompt would only stand between them and the demonstration.
        assertThat(admin.isPasswordChangeRequired()).isFalse();
    }

    @Test
    void buildsTheChainASaleNeeds() {
        Store store = demoStore();
        assertThat(terminalRepository.findAll())
                .anyMatch(terminal -> terminal.getStore().getId().equals(store.getId()));
        assertThat(registerRepository.findAll())
                .anyMatch(
                        register ->
                                register.getStore().getId().equals(store.getId())
                                        && "ACTIVE".equals(register.getStatus()));
    }

    @Test
    void givesEveryDemoProductStockSoTheTillCanSellIt() {
        Store store = demoStore();

        for (String sku : DEMO_SKUS) {
            Product product = productRepository.findBySku(sku).orElseThrow(() -> missing(sku));
            BigDecimal quantity =
                    balanceRepository
                            .findByProductIdAndStoreIdForUpdate(product.getId(), store.getId())
                            .orElseThrow(() -> missing(sku + " balance"))
                            .getQuantity();
            // Without a positive balance, InventoryService.deductForSale rejects the sale outright
            // and the demonstration till cannot complete a single transaction.
            assertThat(quantity).as(sku).isGreaterThan(BigDecimal.ZERO);
        }
    }

    @Test
    void isIdempotentAndDoesNotDuplicateAnything() {
        long usersBefore = userRepository.count();
        long productsBefore = productRepository.count();

        DemoDataSeeder.DemoSeedSummary again = seeder.seed();

        assertThat(userRepository.count()).isEqualTo(usersBefore);
        assertThat(productRepository.count()).isEqualTo(productsBefore);
        // Everything was already stocked by the startup run, so this call added none.
        assertThat(again.productsStocked()).isZero();
        assertThat(again.productCount()).isEqualTo(DEMO_SKUS.size());
    }

    @Test
    void doesNotResetThePasswordOfAnAccountThatAlreadyExists() {
        User admin = userRepository.findByUsername("seed.test.admin").orElseThrow();
        admin.changePassword(passwordEncoder.encode("a-password-the-user-chose-later"));
        userRepository.saveAndFlush(admin);

        seeder.seed();

        User reloaded = userRepository.findByUsername("seed.test.admin").orElseThrow();
        assertThat(passwordEncoder.matches("a-password-the-user-chose-later", reloaded.getPasswordHash()))
                .isTrue();
        assertThat(passwordEncoder.matches("demo-admin-password", reloaded.getPasswordHash())).isFalse();
    }

    private Store demoStore() {
        return storeRepository.findAll().stream()
                .filter(candidate -> "DEMO".equals(candidate.getCode()))
                .findFirst()
                .orElseThrow(() -> missing("DEMO store"));
    }

    private static AssertionError missing(String what) {
        return new AssertionError(what + " was not seeded");
    }
}

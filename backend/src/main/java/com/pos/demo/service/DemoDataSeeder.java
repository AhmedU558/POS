package com.pos.demo.service;

import com.pos.catalog.entity.Category;
import com.pos.catalog.entity.Product;
import com.pos.catalog.entity.ProductBarcode;
import com.pos.catalog.entity.Unit;
import com.pos.catalog.repository.CategoryRepository;
import com.pos.catalog.repository.ProductBarcodeRepository;
import com.pos.catalog.repository.ProductRepository;
import com.pos.catalog.repository.UnitRepository;
import com.pos.customers.domain.Customer;
import com.pos.customers.repository.CustomerRepository;
import com.pos.demo.config.DemoSeedProperties;
import com.pos.inventory.domain.InventoryBalance;
import com.pos.inventory.domain.InventoryTransaction;
import com.pos.inventory.domain.TransactionType;
import com.pos.inventory.repository.InventoryBalanceRepository;
import com.pos.inventory.repository.InventoryTransactionRepository;
import com.pos.organization.domain.Register;
import com.pos.organization.domain.Store;
import com.pos.organization.domain.Terminal;
import com.pos.organization.repository.RegisterRepository;
import com.pos.organization.repository.StoreRepository;
import com.pos.organization.repository.TerminalRepository;
import com.pos.suppliers.domain.Supplier;
import com.pos.suppliers.repository.SupplierRepository;
import com.pos.users.domain.Role;
import com.pos.users.domain.RoleName;
import com.pos.users.domain.User;
import com.pos.users.repository.RoleRepository;
import com.pos.users.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Builds a demonstration store that can take a sale the moment it exists.
 *
 * <p>Everything here is idempotent and additive: each item is created only when nothing of that
 * name already exists, and nothing is ever updated or deleted. Running the seeder twice is a
 * no-op, and it will not disturb data an evaluator has entered by hand.
 *
 * <p>The dataset is chosen to make the whole loop reachable rather than to look impressive. A
 * sale needs an open register session, which needs a register, a terminal and a store; and it
 * needs stock, because {@code InventoryService.deductForSale} rejects a sale with no balance
 * rather than letting it go negative. Products alone would produce a till that cannot sell.
 */
@Service
public class DemoDataSeeder {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private static final String STORE_CODE = "DEMO";
    private static final String TERMINAL_CODE = "T1";
    private static final String REGISTER_CODE = "R1";
    private static final BigDecimal OPENING_STOCK = new BigDecimal("40.0000");

    private final DemoSeedProperties properties;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final StoreRepository storeRepository;
    private final TerminalRepository terminalRepository;
    private final RegisterRepository registerRepository;
    private final CategoryRepository categoryRepository;
    private final UnitRepository unitRepository;
    private final ProductRepository productRepository;
    private final ProductBarcodeRepository barcodeRepository;
    private final InventoryBalanceRepository balanceRepository;
    private final InventoryTransactionRepository transactionRepository;
    private final CustomerRepository customerRepository;
    private final SupplierRepository supplierRepository;

    public DemoDataSeeder(
            DemoSeedProperties properties,
            UserRepository userRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder,
            StoreRepository storeRepository,
            TerminalRepository terminalRepository,
            RegisterRepository registerRepository,
            CategoryRepository categoryRepository,
            UnitRepository unitRepository,
            ProductRepository productRepository,
            ProductBarcodeRepository barcodeRepository,
            InventoryBalanceRepository balanceRepository,
            InventoryTransactionRepository transactionRepository,
            CustomerRepository customerRepository,
            SupplierRepository supplierRepository) {
        this.properties = properties;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.storeRepository = storeRepository;
        this.terminalRepository = terminalRepository;
        this.registerRepository = registerRepository;
        this.categoryRepository = categoryRepository;
        this.unitRepository = unitRepository;
        this.productRepository = productRepository;
        this.barcodeRepository = barcodeRepository;
        this.balanceRepository = balanceRepository;
        this.transactionRepository = transactionRepository;
        this.customerRepository = customerRepository;
        this.supplierRepository = supplierRepository;
    }

    /**
     * Seeds the demonstration dataset.
     *
     * @return a summary of what this call created, for the runner to log
     */
    @Transactional
    public DemoSeedSummary seed() {
        Store store = demoStore();
        Terminal terminal = demoTerminal(store);
        Register register = demoRegister(store, terminal);

        User admin =
                demoUser(
                        properties.getAdminUsername(),
                        properties.getAdminPassword(),
                        "Demo",
                        "Administrator",
                        RoleName.SUPER_ADMINISTRATOR,
                        store);
        User cashier =
                demoUser(
                        properties.getCashierUsername(),
                        properties.getCashierPassword(),
                        "Demo",
                        "Cashier",
                        RoleName.CASHIER,
                        store);

        Unit each = demoUnit("EA", "Each");
        Category drinks = demoCategory("Demo — Drinks");
        Category snacks = demoCategory("Demo — Snacks");

        List<Product> products =
                List.of(
                        demoProduct("DEMO-COLA", "Cola 330ml", drinks, each, "0.55", "1.20", "5000112637922"),
                        demoProduct("DEMO-WATER", "Still Water 500ml", drinks, each, "0.25", "0.80", "5000112637939"),
                        demoProduct("DEMO-COFFEE", "Ground Coffee 250g", drinks, each, "3.10", "5.99", "5000112637946"),
                        demoProduct("DEMO-CRISPS", "Salted Crisps 40g", snacks, each, "0.35", "0.95", "5000112637953"),
                        demoProduct("DEMO-CHOC", "Chocolate Bar 45g", snacks, each, "0.40", "1.10", "5000112637960"));

        int stocked = 0;
        for (Product product : products) {
            if (openingStock(store, product, admin)) {
                stocked++;
            }
        }

        demoCustomer();
        demoSupplier();

        return new DemoSeedSummary(
                admin.getUsername(),
                cashier.getUsername(),
                store.getName(),
                register.getName(),
                products.size(),
                stocked);
    }

    private Store demoStore() {
        return findStoreByCode()
                .orElseGet(
                        () -> {
                            log.info("Demo seed: creating store {}", STORE_CODE);
                            return storeRepository.save(
                                    new Store(STORE_CODE, "Demo Store", "PKR", "UTC"));
                        });
    }

    /*
     * The organisation repositories carry no code-based finder, and adding one to reach a
     * five-row demo table would change a module this seeder has no business touching. Scanning
     * is correct here: it happens once, at startup, over a handful of rows.
     */
    private Optional<Store> findStoreByCode() {
        return storeRepository.findAll().stream()
                .filter(candidate -> STORE_CODE.equals(candidate.getCode()))
                .findFirst();
    }

    private Terminal demoTerminal(Store store) {
        return terminalRepository.findAll().stream()
                .filter(candidate -> candidate.getStore().getId().equals(store.getId()))
                .filter(candidate -> TERMINAL_CODE.equals(candidate.getCode()))
                .findFirst()
                .orElseGet(
                        () -> terminalRepository.save(
                                new Terminal(store, TERMINAL_CODE, "Front Counter", "ACTIVE")));
    }

    private Register demoRegister(Store store, Terminal terminal) {
        return registerRepository.findAll().stream()
                .filter(candidate -> candidate.getStore().getId().equals(store.getId()))
                .filter(candidate -> REGISTER_CODE.equals(candidate.getCode()))
                .findFirst()
                .orElseGet(
                        () -> registerRepository.save(
                                new Register(store, terminal, REGISTER_CODE, "Register 1", "ACTIVE")));
    }

    /**
     * Creates a demo account, or returns the existing one untouched.
     *
     * <p>An existing account is never re-hashed with the configured password. Otherwise restarting
     * with the property still set would silently reset a password somebody had since changed.
     */
    private User demoUser(
            String username,
            String password,
            String firstName,
            String lastName,
            String roleName,
            Store store) {
        Optional<User> existing = userRepository.findByUsername(username);
        if (existing.isPresent()) {
            User user = existing.get();
            // Store assignment is additive and safe to re-apply: without it the account can see
            // nothing, and the set is keyed by identity.
            user.assignStore(store);
            return userRepository.save(user);
        }

        Role role =
                roleRepository
                        .findByName(roleName)
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "Role '"
                                                        + roleName
                                                        + "' is missing; reference data has not"
                                                        + " been seeded."));

        User user = new User(username, passwordEncoder.encode(password), firstName, lastName);
        user.setEmail(username + "@demo.local");
        user.assignRole(role);
        user.assignStore(store);
        /*
         * Deliberately no forced rotation, unlike bootstrap. The operator picked this password
         * moments ago for their own evaluation, so it has not passed through a pipeline or a third
         * party — and a rotation prompt would sit between them and the thing they are trying to
         * look at. The account cannot exist in production, which is what makes that acceptable.
         */
        return userRepository.save(user);
    }

    private Unit demoUnit(String code, String name) {
        if (unitRepository.existsByCode(code)) {
            return unitRepository.findAll().stream()
                    .filter(candidate -> code.equals(candidate.getCode()))
                    .findFirst()
                    .orElseThrow();
        }
        Unit unit = new Unit();
        unit.setCode(code);
        unit.setName(name);
        unit.setActive(true);
        return unitRepository.save(unit);
    }

    private Category demoCategory(String name) {
        if (categoryRepository.existsByNameAndParentIsNull(name)) {
            return categoryRepository.findAll().stream()
                    .filter(candidate -> name.equals(candidate.getName()))
                    .findFirst()
                    .orElseThrow();
        }
        Category category = new Category();
        category.setName(name);
        category.setActive(true);
        return categoryRepository.save(category);
    }

    private Product demoProduct(
            String sku,
            String name,
            Category category,
            Unit unit,
            String cost,
            String price,
            String barcode) {
        Optional<Product> existing = productRepository.findBySku(sku);
        if (existing.isPresent()) {
            return existing.get();
        }

        Product product = new Product();
        product.setSku(sku);
        product.setName(name);
        product.setCategory(category);
        product.setUnit(unit);
        product.setPurchasePrice(new BigDecimal(cost));
        product.setSellingPrice(new BigDecimal(price));
        product.setTaxRate(new BigDecimal("0.0000"));
        product.setMinStock(new BigDecimal("10.0000"));
        product.setTrackBatch(false);
        product.setTrackExpiry(false);
        product.setActive(true);
        Product saved = productRepository.save(product);

        // A product with no barcode can be searched but not scanned, which is half the till.
        if (!barcodeRepository.existsByBarcode(barcode)) {
            ProductBarcode code = new ProductBarcode();
            code.setProduct(saved);
            code.setBarcode(barcode);
            code.setPrimary(true);
            barcodeRepository.save(code);
        }
        return saved;
    }

    /**
     * Gives a product opening stock, recorded as a receipt so the movement history is honest
     * about where the quantity came from.
     *
     * @return true when stock was added by this call
     */
    private boolean openingStock(Store store, Product product, User actor) {
        // Only the locking finder exists; inside this transaction that is the right one anyway.
        if (balanceRepository
                .findByProductIdAndStoreIdForUpdate(product.getId(), store.getId())
                .isPresent()) {
            return false;
        }
        InventoryBalance balance = new InventoryBalance(product, store);
        balance.addQuantity(OPENING_STOCK);
        balanceRepository.save(balance);

        transactionRepository.save(
                new InventoryTransaction(
                        product,
                        store,
                        TransactionType.RECEIPT,
                        OPENING_STOCK,
                        "Demo opening stock",
                        actor));
        return true;
    }

    private void demoCustomer() {
        String code = "DEMO-CUST-1";
        boolean exists =
                customerRepository.findAll().stream()
                        .anyMatch(candidate -> code.equals(candidate.getCustomerCode()));
        if (exists) {
            return;
        }
        Customer customer = new Customer();
        customer.setCustomerCode(code);
        customer.setName("Demo Customer");
        customer.setPhone("+10000000000");
        customer.setCreditLimit(new BigDecimal("100.0000"));
        customer.setActive(true);
        customerRepository.save(customer);
    }

    private void demoSupplier() {
        String code = "DEMO-SUPP-1";
        boolean exists =
                supplierRepository.findAll().stream()
                        .anyMatch(candidate -> code.equals(candidate.getSupplierCode()));
        if (exists) {
            return;
        }
        Supplier supplier = new Supplier();
        supplier.setSupplierCode(code);
        supplier.setName("Demo Wholesale Ltd");
        supplier.setEmail("orders@demo-wholesale.local");
        supplier.setActive(true);
        supplierRepository.save(supplier);
    }

    /** What a seeding run produced, so the runner can say so without re-querying. */
    public record DemoSeedSummary(
            String adminUsername,
            String cashierUsername,
            String storeName,
            String registerName,
            int productCount,
            int productsStocked) {}
}

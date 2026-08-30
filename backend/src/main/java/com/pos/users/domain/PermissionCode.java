package com.pos.users.domain;

import java.util.Set;

public final class PermissionCode {

    public static final String USER_READ = "USER_READ";
    public static final String USER_WRITE = "USER_WRITE";
    public static final String USER_ADMIN = "USER_ADMIN";
    public static final String ROLE_READ = "ROLE_READ";
    public static final String ROLE_WRITE = "ROLE_WRITE";

    public static final String STORE_READ = "STORE_READ";
    public static final String STORE_WRITE = "STORE_WRITE";
    public static final String TERMINAL_READ = "TERMINAL_READ";
    public static final String TERMINAL_WRITE = "TERMINAL_WRITE";
    public static final String REGISTER_READ = "REGISTER_READ";
    public static final String REGISTER_WRITE = "REGISTER_WRITE";

    public static final String PRODUCT_READ = "PRODUCT_READ";
    public static final String PRODUCT_WRITE = "PRODUCT_WRITE";

    public static final String CUSTOMER_READ = "CUSTOMER_READ";
    public static final String CUSTOMER_WRITE = "CUSTOMER_WRITE";
    public static final String CREDIT_READ = "CREDIT_READ";
    public static final String CREDIT_WRITE = "CREDIT_WRITE";
    public static final String SUPPLIER_READ = "SUPPLIER_READ";
    public static final String SUPPLIER_WRITE = "SUPPLIER_WRITE";
    public static final String PURCHASE_READ = "PURCHASE_READ";
    public static final String PURCHASE_WRITE = "PURCHASE_WRITE";
    public static final String PURCHASE_APPROVE = "PURCHASE_APPROVE";
    public static final String AP_READ = "AP_READ";
    public static final String AP_WRITE = "AP_WRITE";
    public static final String AP_PAYMENT_CREATE = "AP_PAYMENT_CREATE";

    public static final Set<String> IDENTITY =
            Set.of(USER_READ, USER_WRITE, USER_ADMIN, ROLE_READ, ROLE_WRITE);

    public static final Set<String> ORGANIZATION =
            Set.of(STORE_READ, STORE_WRITE, TERMINAL_READ, TERMINAL_WRITE, REGISTER_READ, REGISTER_WRITE);

    public static final Set<String> CATALOG =
            Set.of(PRODUCT_READ, PRODUCT_WRITE);

    private PermissionCode() {}
}
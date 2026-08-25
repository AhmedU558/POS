package com.pos.users.domain;

import java.util.Set;

/**
 * The six roles defined by the approved roles-and-permissions.md, seeded as reference data.
 *
 * <p>Names are the business key for {@link Role}, so they are declared once here rather than
 * repeated as literals wherever a role is looked up.
 */
public final class RoleName {

    public static final String SUPER_ADMINISTRATOR = "Super Administrator";
    public static final String STORE_MANAGER = "Store Manager";
    public static final String CASHIER = "Cashier";
    public static final String INVENTORY_MANAGER = "Inventory Manager";
    public static final String ACCOUNTANT = "Accountant";
    public static final String ONLINE_ORDER_STAFF = "Online Order Staff";

    public static final Set<String> ALL =
            Set.of(
                    SUPER_ADMINISTRATOR,
                    STORE_MANAGER,
                    CASHIER,
                    INVENTORY_MANAGER,
                    ACCOUNTANT,
                    ONLINE_ORDER_STAFF);

    private RoleName() {}
}

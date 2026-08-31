import { IconName } from '@/components/ui/Icon';
import { P } from '@/lib/permissions';

/*
 * Navigation model.
 *
 * Structured around what a store does rather than around the API surface: a manager looks for
 * "Purchases", not for "goods receipts" and "purchase orders" as separate top-level ideas. The
 * hierarchy follows UI/UX Specification section 5, restructured for a professional POS layout.
 *
 * `permissions` lists the codes that make an item worth showing. An item is shown when the user
 * holds any one of them, so a cashier who can sell but not report still sees a coherent menu
 * rather than a menu with holes in it.
 */

export interface NavItem {
  label: string;
  href: string;
  icon: IconName;
  permissions: readonly string[];
  /** Sub-paths that should still light up this item, e.g. /products/new under /products. */
  match?: readonly string[];
}

export interface NavSection {
  label: string;
  items: readonly NavItem[];
}

export const NAV_SECTIONS: readonly NavSection[] = [
  {
    label: 'POS',
    items: [
      { label: 'New Sale', href: '/pos', icon: 'pos', permissions: [P.SALE_CREATE] },
      {
        label: 'Held Sales',
        href: '/sales/held',
        icon: 'reports',
        permissions: [P.SALE_READ],
      },
      {
        label: 'Sales History',
        href: '/sales/history',
        icon: 'reports',
        permissions: [P.SALE_READ],
        match: ['/sales/history', '/sales'],
      },
    ],
  },
  {
    label: 'Inventory',
    items: [
      {
        label: 'Products',
        href: '/products',
        icon: 'products',
        permissions: [P.PRODUCT_READ],
        match: ['/products', '/categories', '/brands', '/units'],
      },
      {
        label: 'Stock',
        href: '/inventory',
        icon: 'inventory',
        permissions: [P.INVENTORY_READ],
      },
      {
        label: 'Receive Stock',
        href: '/inventory/receive',
        icon: 'inventory',
        permissions: [P.INVENTORY_RECEIVE],
      },
      {
        label: 'Batches & Expiry',
        href: '/inventory/batches',
        icon: 'inventory',
        permissions: [P.INVENTORY_READ],
      },
      {
        label: 'Alerts',
        href: '/inventory/alerts',
        icon: 'inventory',
        permissions: [P.INVENTORY_READ],
      },
    ],
  },
  {
    label: 'Customers',
    items: [
      {
        label: 'Customers',
        href: '/customers',
        icon: 'customers',
        permissions: [P.CUSTOMER_READ],
        match: ['/customers'],
      },
    ],
  },
  {
    label: 'Suppliers',
    items: [
      {
        label: 'Suppliers',
        href: '/suppliers',
        icon: 'suppliers',
        permissions: [P.SUPPLIER_READ],
        match: ['/suppliers'],
      },
    ],
  },
  {
    label: 'Purchasing',
    items: [
      {
        label: 'Purchase Orders',
        href: '/purchase-orders',
        icon: 'purchases',
        permissions: [P.PURCHASE_READ],
        match: ['/purchase-orders'],
      },
      {
        label: 'Bills to Pay',
        href: '/accounts-payable',
        icon: 'payables',
        permissions: [P.AP_READ],
        match: ['/accounts-payable'],
      },
    ],
  },
  {
    label: 'Accounts',
    items: [
      {
        label: 'Expenses',
        href: '/expenses',
        icon: 'expenses',
        permissions: [P.EXPENSE_READ],
        match: ['/expenses'],
      },
    ],
  },
  {
    label: 'Register',
    items: [
      {
        label: 'Register',
        href: '/register',
        icon: 'register',
        permissions: [P.REGISTER_OPEN, P.REGISTER_READ, P.REGISTER_CLOSE, P.REGISTER_CASH],
      },
    ],
  },
  {
    label: 'Reports',
    items: [
      {
        label: 'Reports',
        href: '/reports',
        icon: 'reports',
        permissions: [P.REPORT_SALES, P.REPORT_INVENTORY, P.REPORT_FINANCE, P.REPORT_CASH],
        match: ['/reports'],
      },
    ],
  },
  {
    label: 'Settings',
    items: [
      {
        label: 'Store Setup',
        href: '/setup',
        icon: 'settings',
        permissions: [P.STORE_READ, P.STORE_WRITE, P.TERMINAL_READ, P.REGISTER_READ],
        match: ['/setup'],
      },
      {
        label: 'FBR Integration',
        href: '/settings/fbr',
        icon: 'settings',
        permissions: [P.STORE_WRITE],
        match: ['/settings/fbr'],
      },
      { label: 'Dashboard', href: '/', icon: 'dashboard', permissions: [] },
    ],
  },
];

/** True when `pathname` belongs to `item`, treating /products/new as part of Products. */
export function isNavItemActive(item: NavItem, pathname: string): boolean {
  if (item.href === '/') {
    return pathname === '/';
  }
  const prefixes = item.match ?? [item.href];
  return prefixes.some((prefix) => pathname === prefix || pathname.startsWith(`${prefix}/`));
}

/** Routes that render their own full-screen layout and must not be wrapped in the shell. */
export const BARE_ROUTES = ['/login', '/forced-rotation', '/pos'] as const;

export function isBareRoute(pathname: string): boolean {
  return BARE_ROUTES.some((route) => pathname === route || pathname.startsWith(`${route}/`));
}

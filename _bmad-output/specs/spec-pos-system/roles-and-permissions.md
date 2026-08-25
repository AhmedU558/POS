# Roles and Permissions

The authorization layer shall use least-privilege access. Sensitive operations must be controlled by explicit permissions rather than role names alone.

## Core Roles

- **Super Administrator**: Full system, organization, stores, users, roles, configuration, and reports.
- **Store Manager**: Store operations, inventory, suppliers, reports, discounts, registers, and approvals.
- **Cashier**: POS sales, customer registration, authorized discounts, returns, receipts, and register closing.
- **Inventory Manager**: Products, receiving, adjustments, transfers, batches, expiry, and stock reports.
- **Accountant**: Purchasing/AP, supplier payments, budgets, expenses, and financial reports.
- **Online Order Staff**: Online order review, fulfillment, cancellation, and refund workflows.

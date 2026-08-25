# Data Entities

## Core Entities
- Users, Roles, Permissions
- Stores, Terminals, Register Sessions
- Products, Categories, Brands, Units, Product Prices, Barcodes, Batches
- Customers, Store Credits, Customer Transactions
- Suppliers, Purchase Orders, Purchase Items, Supplier Invoices, Supplier Payments
- Sales, Sale Items, Sale Payments, Returns/Refunds
- Inventory, Inventory Transactions, Stock Transfers
- Quotations, Quotation Items
- Discounts, Promotions, Promotion Rules
- Budgets, Expenses
- Cash Transactions
- Receipts and Invoices
- Notifications and Audit Logs

## Data Integrity Requirements
- Primary and foreign-key relationships shall enforce referential integrity.
- Unique identifiers shall be enforced where required.
- Financial and inventory operations shall use database transactions where atomicity is required.
- Deleted business records should generally use controlled deactivation/voiding rather than destructive deletion when historical reporting depends on them.

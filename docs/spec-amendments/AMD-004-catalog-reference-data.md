# AMD-004: Catalog Reference Data Refinements

## 1. Context
During the Story 2.1 Specification Audit, several ambiguities were identified regarding the exact capabilities and constraints of Categories, Brands, and Units.

## 2. Approved Changes

### 2.1 REST API Specification
The following endpoints are formally added to the API specification to support full lifecycle management:
- PATCH /brands/{id} - Update brand details and active status. Requires PRODUCT_WRITE.
- PATCH /units/{id} - Update unit details and active status. Requires PRODUCT_WRITE.

### 2.2 Database Design & ERD
The following constraints and columns are added to ensure data integrity:
- **units table**: Add is_active BOOLEAN NOT NULL DEFAULT true to allow deactivation instead of hard deletion.
- **units table**: Add UNIQUE(code) and UNIQUE(name).
- **rands table**: Add UNIQUE(name).
- **categories table**: Add a composite unique constraint on (parent_id, name) (treating NULL parent_id as root).

### 2.3 Business Logic (Category Depth)
- Category hierarchies are restricted to a maximum depth of 3 levels to simplify reporting and UI traversal.
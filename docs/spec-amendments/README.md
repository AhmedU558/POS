# Specification Amendments

The approved specifications in `Documents/` are the source of truth and are **read-only outside
change control**. When implementation reveals that an approved specification is incomplete or
blocks an approved requirement, the change is proposed here first and only applied to
`Documents/` once the product owner approves it.

An amendment is grouped by the approved document it touches, so one proposal never edits two
specifications at once.

| Amendment | Target document | Status |
|-----------|-----------------|--------|
| [AMD-001](AMD-001-database-design-rotation-and-bootstrap.md) | POS Database Design & ERD Specification | **Approved** 2026-08-25 |
| [AMD-002](AMD-002-rest-api-self-service-password-change.md) | POS REST API Specification | **Approved** 2026-08-25 |
| [AMD-003](AMD-003-ui-ux-forced-password-rotation-screen.md) | POS UI/UX and Screen Architecture Specification | **Approved** 2026-08-27 |
| [AMD-004](AMD-004-catalog-reference-data.md) | REST API + Database Design (catalog reference data) | **Approved** (Story 2.1) |
| [AMD-005](AMD-005-rest-api-inventory-receipts.md) | POS REST API Specification | **Approved** 2026-08-30 |
| [AMD-006](AMD-006-ui-ux-phase-3-stock-receiving.md) | POS UI/UX and Screen Architecture Specification | **Approved** 2026-08-30 |
| [AMD-007](AMD-007-rest-api-inventory-batches.md) | POS REST API Specification | **Approved** 2026-08-30 (Story 3.3 / D5) |
| [AMD-008](AMD-008-ui-ux-batches-expiry.md) | POS UI/UX and Screen Architecture Specification | **Approved** 2026-08-30 (Story 3.3 / D5) |
| [AMD-009](AMD-009-rest-api-inventory-alerts-reports.md) | POS REST API Specification | **Approved** 2026-08-30 (Story 3.4) |
| [AMD-010](AMD-010-ui-ux-stock-alerts-reports.md) | POS UI/UX and Screen Architecture Specification | **Approved** 2026-08-30 (Story 3.4) |
| [AMD-011](AMD-011-rest-api-customer-profiles.md) | POS REST API Specification | **Approved** 2026-08-31 (Story 4.1) |
| [AMD-012](AMD-012-ui-ux-customer-profiles.md) | POS UI/UX and Screen Architecture Specification | **Approved** 2026-08-31 (Story 4.1) |

## Status values

- **Proposed** — drafted, awaiting product-owner approval. No code or migration may rely on it.
- **Approved** — accepted; `Documents/` updated and implementation may proceed.
- **Rejected** — declined; the record is kept so the question is not reopened without new information.
- **Superseded** — replaced by a later amendment, named in this file.

## Note on the source documents

Approved amendments are binding addenda. The originals in `Documents/` exist as paired `.docx` and
`.txt` files; editing only the `.txt` would leave the two out of sync, so the `.docx` masters have
**not** been altered. Whoever owns those documents should fold approved amendments into them in a
controlled update. Until then, an approved amendment here carries the same authority as the section
it amends.

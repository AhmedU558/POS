# AMD-016 — Supplier list and profile screens

**Target document:** POS UI/UX and Screen Architecture Specification v1.0
**Sections affected:** §19, screen inventory SCR-018, §36
**Status:** **Approved** — Story 4.3 / D1–D6
**Date:** 2026-08-31
**Companion:** [ADR-023](../adr/ADR-023-story-4-3-supplier-profiles.md),
[AMD-015](AMD-015-rest-api-supplier-profiles.md)

## 1. The gap

SCR-018 is MUST. §19 is a heading only. The suppliers UX table also names an
outstanding-balance shortcut, which needs payables (Phase 5).

## 2. Approved Phase 4 slice

| Screen | Route | Permission |
|--------|-------|------------|
| SCR-018 Suppliers | `/suppliers` | `SUPPLIER_READ`; create control needs `SUPPLIER_WRITE` |
| Profile / create | `/suppliers/[id]`, `/suppliers/new` | `SUPPLIER_READ` / `SUPPLIER_WRITE` |

SCR-018: search (`query`), status filter (`isActive`), paginated table of
code, name, phone, email, labelled status (not colour alone).

Profile: contact fields and status from AMD-015. No product associations,
statements, or outstanding balance.

Navigation to Suppliers is shown when the user has `SUPPLIER_READ`.

## 3. Out of this amendment

SUP-002 associations, purchase orders, goods receipts, invoices, AP,
statements, outstanding-balance shortcut.

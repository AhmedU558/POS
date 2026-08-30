# AMD-018 — Supplier-product associations on SCR-018

**Target document:** POS UI/UX and Screen Architecture Specification v1.0
**Sections affected:** §19, SCR-018
**Status:** **Approved** — Story 4.4 / D1–D5
**Date:** 2026-08-31
**Companion:** [ADR-024](../adr/ADR-024-story-4-4-supplier-products.md),
[AMD-017](AMD-017-rest-api-supplier-products.md)

## 1. The gap

SUP-002 has no dedicated screen. AMD-016 left associations off the 4.3 profile.

## 2. Approved change

SCR-018 profile (`/suppliers/[id]`) shows an **Associated products** section:
SKU, name, labelled active/inactive status.

`SUPPLIER_WRITE` can replace the set. A catalog picker is shown when the user
also has `PRODUCT_READ`. No purchase-order, invoice, statement, or outstanding
balance UI.

## 3. Out of this amendment

SUP-002 commercial fields (none specified), Phase 5 purchasing/AP.

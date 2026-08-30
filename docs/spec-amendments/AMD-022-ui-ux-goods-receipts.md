# AMD-022 — Receive against a submitted PO

**Target document:** POS UI/UX and Screen Architecture Specification v1.0
**Sections affected:** §16.3, §32.3
**Status:** **Approved** — Story 5.2
**Date:** 2026-08-31

Submitted PO detail shows Receive. `/purchase-orders/[id]/receive` captures quantities (ordered shown for comparison) and confirms via `POST /goods-receipts`. No invoice/AP UI. Phase 3 standalone receiving is unchanged.

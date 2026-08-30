# AMD-008 — Phase 3 Batches & Expiry screen and receiving lot capture

**Target document:** POS UI/UX and Screen Architecture Specification v1.0
**Sections affected:** §16.3 / SCR-013, §17 (Expiration), §36 (Batches & Expiry),
Phase 3 SCR-012 (AMD-006)
**Status:** **Approved** — implements ADR-018 D5 and AMD-006 Story 3.3 reservation
**Raised by:** Phase 3 Story 3.3
**Date:** 2026-08-30
**Companion:** [ADR-019](../adr/ADR-019-story-3-3-batches-expiry.md),
[AMD-007](AMD-007-rest-api-inventory-batches.md)

---

## 1. The gap

SCR-013 (SHOULD) and §36 require Batches & Expiry to show product, batch,
quantity, expiry date, store, and status. §17 requires a strong accessible
warning for expired stock and days remaining for approaching expiry. AMD-006
deferred lot/expiry capture on Phase 3 SCR-012 to Story 3.3.

Alert acknowledge, stock-alert screens, and dashboard expiry widgets are
separate surfaces (`stock_alerts`, SCR-014, reports) and stay Story 3.4.

---

## 2. Approved change — SCR-013 Batches & Expiry (Phase 3)

- Route: `/inventory/batches`
- Permission: `INVENTORY_READ`. Store is the caller’s authorized store.
- Columns: product, batch, quantity, expiry date, store, status.
- Status is the API-derived value, shown as a **label** (colour is not the only
  signal): Expired, Expiring today, Approaching (with days remaining), OK.
- Users can switch between all batches and expiring/expired stock, and choose
  the 7- or 30-day window (PRD examples). The UI does not invent a settings
  store; it passes `days` to the API.
- SCR-010 offers a link to this screen.

Phase 3 SCR-013 does **not** include acknowledging alerts or generating
`stock_alerts` rows.

---

## 3. Approved change — Phase 3 SCR-012 lot capture

When the selected product has `trackBatch` and/or `trackExpiry`, Phase 3
SCR-012 additionally collects:

- Batch / lot number (required if either flag is on)
- Expiration date (required if `trackExpiry`)
- Manufacturing date (optional)

Confirmation still displays the API-returned on-hand quantity. Products with
both flags off keep the AMD-006 three-field flow.

---

## 4. Out of scope for this amendment

- Expiry Alerts / Stock Alerts screens and acknowledge actions (Story 3.4)
- Dashboard expiry widget
- Computing days remaining or status on the client as authority — display the
  API fields
- Transfer UI

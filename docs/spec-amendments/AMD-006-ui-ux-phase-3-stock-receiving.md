# AMD-006 — Phase 3 Stock Receiving screen (SCR-012)

**Target document:** POS UI/UX and Screen Architecture Specification v1.0
**Sections affected:** §16.3 (Receiving — SCR-012), §32.3 (Stock Receiving workflow), §36 (Receiving UX elements)
**Status:** **Approved** — 2026-08-30 by product owner
**Raised by:** Phase 3 Story 3.2 discovery
**Date:** 2026-08-30
**Companion:** [ADR-018](../adr/ADR-018-story-3-2-stock-receiving-scope.md),
[AMD-005](AMD-005-rest-api-inventory-receipts.md)

---

## 1. The gap

§16.3 SCR-012 (MUST) and workflow 32.3 describe receiving as:

- Select purchase order or supplier
- Enter received quantities
- Capture batch/lot and expiry where required
- Show discrepancies (received vs ordered)
- Confirm receipt and display resulting stock

§36 “Receiving” UX elements require PO comparison and discrepancy indicators.
§34 maps Purchasing APIs to `/purchase-orders` and `/goods-receipts`.

Those steps depend on Phase 4 suppliers, Phase 5 purchase orders / goods receipts,
and Story 3.3 batches. They cannot be built in Story 3.2. INV-003 and the
Implementation Plan Phase 3 “stock receiving” item still require a receiving
workflow in this phase.

This amendment defines the Phase 3 SCR-012 surface. It does not delete the
PO-based workflow; that remains the Phase 5 path.

---

## 2. Approved change — §16.3 for Phase 3

**SCR-012 Stock Receiving (Phase 3):**

- Store is the caller’s authorized store (same scope rule as SCR-011).
- Product selection.
- Received quantity, greater than zero.
- Confirmation.
- Display the resulting on-hand quantity returned by the API.

Phase 3 SCR-012 does **not** include:

- Purchase-order or supplier selection
- Received-vs-ordered comparison or discrepancy indicators
- Batch, lot, or expiry capture (Story 3.3)
- Transfer create/receive UI (deferred; no screen id)

SCR-010 continues to offer quick access to adjustment **and** receiving.

---

## 3. Approved change — workflow 32.3

Keep the existing PO workflow as the **Phase 5** purchasing receive path:

Purchasing → Purchase Order → Receive → Enter Quantities/Batch/Expiry → Validate → Confirm → Inventory Updated

Add the **Phase 3** inventory receive path:

Inventory Overview → Receive Stock → Select Product → Enter Quantity → Confirm → Inventory Updated

---

## 4. Approved change — §36 Receiving UX

The “PO comparison, received vs ordered, discrepancy indicators” row applies to
**Phase 5** goods receiving.

Phase 3 receiving UX is: product, quantity, confirm, resulting stock, and
permission-aware access (`INVENTORY_RECEIVE`).

---

## 5. Out of scope for this amendment

- A stock-transfer screen (none is approved; transfers are deferred).
- Changes to SCR-011, SCR-013, SCR-014, SCR-018, or SCR-019.
- Computing stock on the client. The UI displays the quantity the API returns.

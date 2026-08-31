# Product UX and operational completion pass

**Date:** 2026-08-31
**Scope:** frontend product/UX completion, plus one contract-consistent backend addition
**Not in scope:** new feature phases, schema changes, refactoring completed backend modules

The backend was feature-complete through Phases 0–12. The frontend was a set of thirty screens
that each exercised an endpoint and nothing more: no navigation between them, no shared design
language, and several that could not work at all. This pass turned it into something a store
employee can operate.

---

## 1. What was broken

### Blocking defects

| Defect | Effect | Fix |
|---|---|---|
| `--font-inter` was set on `<body>` while `--font-sans` is declared on `:root` | The `var()` substitution was guaranteed-invalid, so `font-family` fell back to the browser default. Every screen rendered in Times New Roman. | Variable moved to `<html>` |
| `GET /products` returns `Page<ProductResponse>`; the client typed it as `Product[]` | `products.map` ran against the page envelope and the product list threw on render | Typed as `Page<Product>` |
| `ProductResponse` serialises its active flag as `isActive`; the client read `active` | Every product displayed as inactive, and saving the edit form sent `isActive: undefined`, deactivating it | Type corrected; activation moved to its own explicit control |
| `UnitResponse` has `code` and `name`; the client expected `abbreviation` and `allowFractions` | The units screen rendered `undefined` | Type corrected against the DTO |
| The POS asked the cashier to type `storeId`, `terminalId`, `registerId` and `registerSessionId` as raw UUIDs | No cashier could complete a sale | All four resolved from the open register session |
| No UI existed for stores, terminals or registers | A fresh install had no register, so no till could be opened and no sale recorded — at any point in the product's life | `/setup` added |
| Held-sale resume sent `amount: 1` regardless of the balance | Any non-cash tender was under-recorded | Resume routed through the payment dialog |
| `/inventory` was styled with Tailwind class names | Tailwind is not installed and is prohibited by ADR-011, so the page rendered unstyled | Rebuilt on the design system |
| `/login` linked "Forgot password?" to `#` | Dead control on the first screen a user sees | Replaced with what to actually do |

### Structural problems

- **No app shell.** The home page was a stack of permission-gated link cards, and every other
  route was an orphan with no way back.
- **No component layer.** Every screen carried its own inline style objects, which cannot express
  hover, focus, active or disabled states — so none of the controls had any.
- **Raw enum names on screen.** Status columns showed `PARTIALLY_RECEIVED` and `STORE_CREDIT`.
- **No pagination, confirmation, or success feedback** anywhere.

---

## 2. Backend change

One addition, documented as [AMD-043](spec-amendments/AMD-043-rest-api-current-register-session.md):

`GET /api/v1/register-sessions/current` — the caller's open register session, or a null payload.

A sale requires `registerSessionId`, which was only ever issued in the response to opening the
session. Reloading the till lost it, and the register could not be reopened because only one
`OPEN` session is permitted per register. The cashier was left with an open drawer they could not
sell from. Covered by `CurrentRegisterSessionApiIntegrationTests`.

Nothing else in the backend was changed: no schema, no existing endpoint, no permission, no
removed test.

---

## 3. Known gaps in the backend, surfaced not papered over

These are real limitations found while building the screens. No UI pretends they work.

### 3.1 The finance report endpoints are stubs

`ReportController` exposes nine endpoints. Seven return `ResponseEntity.ok(List.of())` with no
implementation behind them, and one more is implemented but returns an empty list:

| Endpoint | State |
|---|---|
| `GET /reports/sales` | `ReportService.getSales` returns `List.of()` — "minimal implementation" |
| `GET /reports/sales/by-product` | Returns `List.of()` in the controller |
| `GET /reports/sales/by-category` | Returns `List.of()` in the controller |
| `GET /reports/sales/by-cashier` | Returns `List.of()` in the controller |
| `GET /reports/payables` | Returns `List.of()` in the controller |
| `GET /reports/cash-registers` | Returns `List.of()` in the controller |
| `GET /reports/profit-loss` | Returns `List.of()` in the controller |
| `GET /reports/cash-flow` | Returns `List.of()` in the controller |
| `GET /reports/budget-variance` | **Implemented** |

They also return bare lists rather than the `ApiResponse` envelope that §5.1 requires of every
other endpoint.

The Reports screen therefore covers only the inventory reports (which are implemented and
enveloped) plus a sales summary built from `GET /sales`, which holds real data. Building screens
over the stubs would have produced empty tables labelled as reports.

**Recommended follow-up:** implement `ReportService` and bring the controller onto `ApiResponse`.
That is a backend work item, not a UX one.

### 3.2 A purchase order has no received state

`PurchaseOrderStatus` is `DRAFT | SUBMITTED | CANCELLED`. Receiving goods creates a
`GoodsReceipt` and moves stock, but leaves the order `SUBMITTED`, and `GoodsReceiptController`
offers no way to list the receipts for an order. So the system cannot answer "how much of this
order has arrived?".

The purchase order screens state this plainly rather than implying a stage that does not exist.
Partial receiving works — a short delivery is simply a shorter list of lines.

**Recommended follow-up:** either a `GET /purchase-orders/{id}/receipts` endpoint, or received
quantities on `PurchaseOrderItemResponse`.

### 3.3 No product image

`products` has no image column and no upload endpoint exists, so the product form does not offer
one. This is noted in `ProductForm.tsx` beside the fields that do exist.

### 3.4 No self-service password reset

There is no reset endpoint, so the login screen tells the user to ask an administrator rather
than linking to a route that does not exist.

### 3.5 Modules with endpoints but no screens

Implemented server-side, no UI in this pass: promotions, quotations, online orders, budgets,
sale returns, users, and roles/permissions. They are absent from the navigation rather than
present-but-broken. Expenses appears in the navigation and is backed by a working API.

`ProductResponse.isActive` versus `CategoryResponse.active` is an inconsistency in the existing
contract. It was left alone — changing it would break the contract — but it is worth aligning in
a future API revision.

---

## 4. What a new employee can now do

Each of these is a single, discoverable path from the sidebar:

| Question | Answer |
|---|---|
| Where do I add a product? | Products → Add product |
| Where do I edit one? | Products → click the name |
| How do I scan an item into a sale? | Point of Sale — the scan field is focused on arrival, and F2 returns to it |
| How do I receive stock? | Inventory → Receive stock, or Purchase orders → Receive |
| How do I create a customer? | Customers → Add customer |
| How do I create a supplier? | Suppliers → Add supplier |
| How do I raise a purchase order? | Purchase orders → New purchase order |
| How do I receive that order? | Open the order → Receive goods |
| How do I open and close the register? | Register → Open register; Close register produces the Z report |
| How do I take payment? | Point of Sale → Pay; cash, card, store credit or a split |
| How do I find yesterday's sale? | Sales → date range or receipt number |
| How do I see low-stock products? | Dashboard, or Inventory → Alerts |

The setup chain a fresh install needs — store, terminal, register — is at Setup, which names the
next missing step rather than leaving the user to work out why the till will not open.

---

## 5. Verification

- `CurrentRegisterSessionApiIntegrationTests` (4 tests) and `RegisterSessionApiIntegrationTests`
  (2 tests) pass.
- Frontend suite passes: the product list contract, the cart's tax-on-discounted-line arithmetic
  and its promotion-preserving item requests, and the payment dialog's tender rules, including
  that a lone cash payment books at the sale total while the tendered amount only drives change.
- `next build` passes with TypeScript checking.

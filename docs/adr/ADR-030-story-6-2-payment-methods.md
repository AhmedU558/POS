# ADR-030: Story 6.2 payment methods and split tender

**Status:** Accepted — Phase 6, Story 6.2
**Date:** 2026-08-31

`GET /payment-methods` lists active methods. Seeded codes: CASH, CARD, STORE_CREDIT, OTHER.

Split tender is allowed when one or more payments sum to the server `grandTotal`. A single CASH payment still uses the server `grandTotal` (Story 6.1). STORE_CREDIT requires a customer and redeems the ledger in the same transaction. Card authorize/capture is out of scope.

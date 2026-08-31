# ADR-034: Story 7.2 register cash-in and cash-out

**Status:** Accepted — Phase 7, Story 7.2
**Date:** 2026-08-31

`POST /register-sessions/{id}/cash-in` and `/cash-out` (`REGISTER_CASH`) record `cash_transactions` of type CASH_IN / CASH_OUT on an OPEN session. Amount must be > 0. Expected cash is not calculated here (Story 7.3).

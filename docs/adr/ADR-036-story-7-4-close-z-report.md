# ADR-036: Story 7.4 register close and Z report

**Status:** Accepted — Phase 7, Story 7.4
**Date:** 2026-08-31

`POST /register-sessions/{id}/close` requires `actualCash`. The server writes expected cash, variance (`actual − expected`), status CLOSED, and a `register_closings` Z-report number. Closed sessions reject cash-in/out. The client does not calculate expected cash or variance.

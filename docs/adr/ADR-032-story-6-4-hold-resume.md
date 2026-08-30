# ADR-032: Story 6.4 hold and resume

**Status:** Accepted — Phase 6, Story 6.4
**Date:** 2026-08-31

`POST /sales` with no payments creates a HELD sale (no inventory, cash, or credit). Payments present still complete the sale (Story 6.1/6.2). `POST /sales/{id}/hold` is a no-op for HELD and 422 for COMPLETED. `POST /sales/{id}/resume` applies payments and runs the existing completion settlement.

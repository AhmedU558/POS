# ADR-033: Story 7.1 open register session

**Status:** Accepted — Phase 7, Story 7.1
**Date:** 2026-08-31

`POST /registers/{id}/sessions/open` requires `openingCash` (>= 0) and `REGISTER_OPEN`. One OPEN session per register. Opening float is stored on `register_sessions.opening_cash` only (not a cash_transactions row). `GET /register-sessions/{id}` allows `REGISTER_READ` or `REGISTER_OPEN`.

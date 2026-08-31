# ADR-035: Story 7.3 live register session summary

**Status:** Accepted — Phase 7, Story 7.3
**Date:** 2026-08-31

`GET /register-sessions/{id}/summary` computes `expectedCash = openingCash + CASH_IN − CASH_OUT + SALE` from `cash_transactions`. The client must not calculate expected cash.

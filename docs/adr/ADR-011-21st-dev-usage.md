# ADR-011: 21st.dev is used for inspiration and review, not installation

**Status:** Accepted — Phase 0
**Date:** 2026-08-25

## Context

The repository ships the 21st.dev skill pack for three agent harnesses. The 21st.dev catalogue is
built around shadcn/ui and Tailwind CSS.

The approved stack does not include either. System Architecture Document section 3 names
"React / Next.js + TypeScript" with no CSS framework, and UI/UX Specification section 7 defines a
token-based design system that this project implements in plain CSS custom properties. UI/UX
section 26 lists the eighteen primitives to be built, and section 7.1 asks for "restrained
animation".

Installing catalogue components directly would pull Tailwind and shadcn conventions into a stack
that no approved document authorises.

## Decision

- **Permitted:** `21st-ui-explore` for visual direction, and `21st-ui-review` for accessibility,
  responsive and interaction critique of screens already built.
- **Permitted:** reading generated component code as a reference, then hand-porting the pattern
  into the project's own CSS-variable design system.
- **Not permitted without a specification amendment:** `21st install`, adding `components.json`,
  or introducing Tailwind, shadcn, or an animation library as a project dependency.

## Consequences

- No `components.json` exists, so the 21st CLI auto-activation heuristic stays dormant. This is
  intended, not an oversight.
- The stray root-level `motion` / `framer-motion` install left behind by an earlier tool run was
  removed in Phase 0; it had no consumer.
- If a future UI requirement genuinely needs Tailwind, that is a change request against the SAD
  and the UI/UX Specification.

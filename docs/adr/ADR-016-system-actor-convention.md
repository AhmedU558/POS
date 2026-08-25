# ADR-016: A system-initiated action is recorded with a null actor

**Status:** Accepted — Phase 1, Story 1.3
**Date:** 2026-08-25
**Resolves:** the open question in [ADR-014](ADR-014-audit-precedes-bootstrap.md)

## Context

Database Design §20.1 defines `audit_logs.actor_user_id` as a foreign key to `users(id)`. Some
audited actions have no human principal: startup provisioning, scheduled work, integration
callbacks. First-administrator provisioning (Story 1.2) is the sharpest case — at that moment there
is not a single row in `users`, so the first audit record the system ever writes is the one that
cannot name an actor.

ADR-014 set out two candidates and deliberately left the choice to this story.

## Decision

**A system-initiated action is recorded with `actor_user_id` set to null**, and the ambiguity that
would otherwise create is closed at the application boundary rather than in the schema.

The recorder accepts a sealed `AuditActor` with exactly two forms — a human with a user id, or the
system. A null column value is only reachable by explicitly asking for `AuditActor.system()`.
A caller cannot produce one by omitting an argument, because there is no way to omit it.

This is the crux. The usual objection to "null means system" is that null is indistinguishable from
"someone forgot" — and for an audit trail, silently relabelling a human action as system-initiated
would be a serious integrity failure. That objection applies to a nullable parameter, not to a
mandatory sealed type. The schema stays exactly as specified; the guarantee lives where it can be
made total.

## Alternatives rejected

**A reserved SYSTEM user row.** Gives AUD-002 a literal actor, but the row needs a `password_hash`
it can never use and an `is_active` value that lies either way. It then has to be excluded from
every user listing, count, administrative screen and permission calculation — an exclusion that
must be remembered at every future call site, forever. One forgotten exclusion shows a fake user in
a management screen. It also does not solve the bootstrap case cleanly: the SYSTEM row would itself
have to be seeded before the first real user, so provisioning would still be creating users before
any user exists.

**An `actor_type` discriminator column.** Genuinely the most explicit option: `NOT NULL`, with a
check constraint tying `HUMAN` to a non-null id and `SYSTEM` to null, so a forgotten actor becomes
a constraint violation rather than a silent mislabel. Rejected because it adds a column beyond
§20.1, requiring a third specification amendment, and the product owner directed that the existing
database architecture be preserved. The sealed type achieves the same totality in the one place
every write passes through.

## Consequences

- `audit_logs.actor_user_id IS NULL` is a documented, load-bearing convention. Any future reader,
  report or query must interpret null as *system*, never as *unknown*.
- Because the foreign key has no `ON DELETE` clause, **a user named by an audit record cannot be
  deleted**. History outranks account cleanup, which matches Database Design §3's preference for
  deactivation over deletion. A test asserts this.
- If a third actor kind ever appears — an external integration acting on its own behalf, say — null
  can no longer carry the distinction and `actor_type` should be revisited as a proper amendment.
- AUD-002 requires records to carry an actor. A null actor satisfies it only under this convention:
  the actor is *the system*, stated by the absence. That reading is recorded here so it is not
  rediscovered as a defect later.

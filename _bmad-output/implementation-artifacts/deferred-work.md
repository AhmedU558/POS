# Deferred Work

Findings that are real but do not belong to the story that surfaced them.
Append-only. Do not edit existing entries.

- source_spec: `spec-1-1-identity-rbac-persistence.md`
  summary: Disable `spring.jpa.open-in-view`, which defaults to true and lets lazy associations resolve outside a transaction.
  evidence: Story 1.1 introduced the first lazy associations. With OSIV on, a web request masks N+1 and makes the fetch-join queries optional, so nothing forces callers onto them. The setting lives in application.yml, which spec 1.1 fenced as Ask First. Belongs with the first story that serves an authenticated web request (1.4/1.5).

- source_spec: `spec-1-1-identity-rbac-persistence.md`
  summary: No optimistic locking on `users`; concurrent deactivation and role edits are last-write-wins.
  evidence: `User` has no `@Version` and V1 has no version column. Adding one changes the `users` table, which Database Design section 6.1 defines without it — a specification amendment, not a developer decision. Matters because AUTH-005 deactivation is a security action.

- source_spec: `spec-1-1-identity-rbac-persistence.md`
  summary: No Bean Validation on identity entities; blank or oversized values fail at the database rather than at the boundary.
  evidence: Entities carry only JPA length/nullable metadata. API spec section 35 puts Bean Validation on request DTOs, which do not exist yet. Constructor-level domain validation should land with Story 1.2, which is the first code that actually creates a user.

- source_spec: `spec-1-1-identity-rbac-persistence.md`
  summary: No `findByUsernameOrEmail` or `findByIdWithRolesAndPermissions` lookup.
  evidence: UI/UX section 9.1 offers a combined username/email login field, and an authenticated request identifies its subject by id rather than username. Both were left out deliberately: adding untested repository methods ahead of a caller is what the review flagged elsewhere in this story. Add them with the login story.

- source_spec: `spec-1-1-identity-rbac-persistence.md`
  summary: Username and email lookups are case-sensitive with no normalization strategy.
  evidence: `users.email` is a plain VARCHAR UNIQUE, so `Foo@x.com` and `foo@x.com` are distinct accounts and a login lookup misses on case mismatch. Needs a decision between citext, a functional unique index on lower(email), or normalizing on write — all of which touch the approved schema.

- source_spec: `spec-1-1-identity-rbac-persistence.md`
  summary: Nothing guards `ddl-auto: validate` in the main application configuration.
  evidence: `schemaValidationIsActiveSoEntityDriftCannotPassSilently` reads the value under the test profile, so it pins the suite's own conformance check rather than the runtime setting. Relaxing main application.yml to `none` would lose schema validation in production with no test turning red.

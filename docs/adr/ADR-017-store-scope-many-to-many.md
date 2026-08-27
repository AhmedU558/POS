# ADR-017: Store scope implemented via user_stores many-to-many relationship

**Status:** Accepted — Phase 1
**Date:** 2026-08-27

## Context

During the implementation of Phase 1, Story 1.9 (STORES, TERMINALS, REGISTERS & STORE SCOPE), a documentation gap was discovered. The approved documents mandate that "Tenancy and data scoping are enforced through the **user-to-store access scope**" (Implementation Plan section 11, REST API Specification section 30, and ADR-010). However, none of the specifications explicitly defined the data schema, API contract, or exact authorization mechanism for how this user-to-store association should be persisted and enforced.

The Database Design specification defined users and stores, but defined no foreign keys or joining tables between them. The REST API Specification defined endpoints for user administration but did not include store assignment fields.

To fulfill the explicit requirement that cross-store data isolation acts as a security boundary, a concrete mechanism had to be established.

## Decision

We have adopted a many-to-many relationship for user store assignments and a strict programmatic enforcement model.

1.  **Database Model**: A user_stores join table is created to explicitly associate a user with one or more stores. A user cannot access a store unless an explicit mapping exists in this table. This relationship is authoritative.
2.  **Authorization Enforcement**: A StoreScopeEvaluator component evaluates store access. All store-scoped endpoints must use a composite @PreAuthorize check requiring both functional permission (e.g., hasAuthority('STORE_READ')) and scope permission (e.g., @storeScopeEvaluator.canAccess(#storeId)).
3.  **Super Administrator Explicit Mapping**: The Super Administrator role does not grant an implicit global bypass. Super Administrators must be explicitly assigned to stores via user_stores to access them, ensuring consistent application of the authorization model.
4.  **API Contract Extension**: The /api/v1/users endpoints (create and update) are extended to accept a storeIds: Set<UUID> property.
5.  **Subset Privilege Enforcement**: A user (even an administrator) can only assign another user to a store that the administrator themselves has access to. A user cannot use the API to escalate their own store scope.

## Consequences

-   **Schema change**: V7__create_store_organization_schema.sql introduces the user_stores table.
-   **Security**: Cross-store data leaks and unauthorized access are structurally prevented at the controller boundary.
-   **Administration**: Store assignment must be explicitly managed; there are no implicit "all stores" assignments. Administrators must provision themselves to new stores if they need access.
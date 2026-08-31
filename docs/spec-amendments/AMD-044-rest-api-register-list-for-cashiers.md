# AMD-044 — A cashier may list the registers they can open

**Target:** POS REST API Specification §4.3 (permissions) and the store-register endpoints
**Status:** **Approved** — Product UX completion pass
**Raised by:** Testing the till as a Cashier rather than as an administrator

---

## 1. Why this amendment is needed

The Cashier role holds `REGISTER_OPEN`, `REGISTER_CASH` and `REGISTER_CLOSE` — the whole till
workflow, start to finish. It does not hold `REGISTER_READ`.

`GET /stores/{storeId}/registers` required `REGISTER_READ`, so a cashier received `403`. Opening a
register needs its identifier, and the only endpoint that reveals one was closed to them. The
person the entire workflow exists for could not begin it. Verified against a running system: a
Cashier-role account got `403` on both the register list and a single register, while holding
every permission needed to open, run and close a session on it.

This is not a policy question about what a cashier should be trusted with. Permission to open a
register already implies knowing which registers exist; refusing to say which ones simply makes
the granted permission unusable.

The specification already resolves the same tension the same way elsewhere: `§18`'s session
endpoints accept `hasAnyAuthority('REGISTER_READ', 'REGISTER_OPEN')`, precisely so a cashier can
read the session they are entitled to open.

---

## 2. Amendment

Two endpoints widen their read authority to match the session endpoints:

| Endpoint | Was | Now |
|---|---|---|
| `GET /stores/{storeId}/registers` | `REGISTER_READ` | `REGISTER_READ` **or** `REGISTER_OPEN` |
| `GET /stores/{storeId}/registers/{id}` | `REGISTER_READ` | `REGISTER_READ` **or** `REGISTER_OPEN` |

Unchanged:

- **Store scope.** `@storeScopeEvaluator.canAccess(#storeId)` still applies, so a cashier sees only
  registers in stores they are assigned to.
- **Write authority.** Creating and updating a register still requires `REGISTER_WRITE`.
- **Role definitions.** No role gains or loses a permission; the seeded reference data is
  untouched.
- Every other endpoint, DTO, status code and error code.

---

## 3. Verification

`RegisterAccessApiIntegrationTests` covers all four cases: a Cashier can list registers, a Cashier
can read one, a Cashier assigned to a different store is still refused, and a role holding no
`REGISTER_*` permission at all (Accountant) is still refused.

# Specification Amendments

The approved specifications in `Documents/` are the source of truth and are **read-only outside
change control**. When implementation reveals that an approved specification is incomplete or
blocks an approved requirement, the change is proposed here first and only applied to
`Documents/` once the product owner approves it.

An amendment is grouped by the approved document it touches, so one proposal never edits two
specifications at once.

| Amendment | Target document | Status |
|-----------|-----------------|--------|
| [AMD-001](AMD-001-database-design-rotation-and-bootstrap.md) | POS Database Design & ERD Specification | **Approved** 2026-08-25 |
| [AMD-002](AMD-002-rest-api-self-service-password-change.md) | POS REST API Specification | **Approved** 2026-08-25 |

## Status values

- **Proposed** — drafted, awaiting product-owner approval. No code or migration may rely on it.
- **Approved** — accepted; `Documents/` updated and implementation may proceed.
- **Rejected** — declined; the record is kept so the question is not reopened without new information.
- **Superseded** — replaced by a later amendment, named in this file.

## Note on the source documents

Approved amendments are binding addenda. The originals in `Documents/` exist as paired `.docx` and
`.txt` files; editing only the `.txt` would leave the two out of sync, so the `.docx` masters have
**not** been altered. Whoever owns those documents should fold approved amendments into them in a
controlled update. Until then, an approved amendment here carries the same authority as the section
it amends.

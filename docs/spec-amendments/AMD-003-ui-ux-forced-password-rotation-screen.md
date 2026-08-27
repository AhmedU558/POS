# AMD-003 - UI/UX Forced Password Rotation Screen

**Target document:** POS UI/UX and Screen Architecture Specification
**Sections affected:** 9 (Authentication Screens), 25 (Navigation Flow), 26 (Requirements Traceability Matrix)
**Status:** **Approved**
**Raised by:** Phase 1.10 authentication UI implementation
**Date:** 2026-08-27

---

## 1. The gap

The UI/UX specification details SCR-001 (Login) but does not contain a specific screen or flow for when an authenticated user is forced to change their password upon logging in (due to the password_change_required flag on their account, driven by ADR-013/AMD-001/AMD-002).

Without a designated screen and explicit UI behavior, the frontend has no approved visual design or flow to handle this mandatory security requirement.

## 2. The solution

Introduce a new screen, **SCR-033 (Forced Password Rotation)**, and amend the Authentication Screens section to detail its behavior.

### 2.1 Amendments to Section 9 (Authentication Screens)

**Add section 9.3 Forced Password Rotation - SCR-033:**
- **Trigger:** A successful login where the backend returns passwordChangeRequired: true or responds with HTTP 403 PASSWORD_CHANGE_REQUIRED.
- **Form Fields:**
  - Current Password field.
  - New Password field.
  - Confirm New Password field.
- **Controls:**
  - Submit button.
  - Show/hide password toggles.
  - Logout/Cancel button (returns to Login).
- **Security constraints:**
  - The user must not be able to bypass this screen via navigation. All layout navigation elements must be hidden or disabled.
  - Upon successful rotation, the app retrieves the updated user profile and navigates to the intended destination (e.g., SCR-002 Dashboard).

### 2.2 Amendments to Section 25 (Navigation Flow)

**Amend the primary flow to include the rotation branch:**
Login -> (If passwordChangeRequired) -> SCR-033 Forced Rotation -> Dashboard / POS

### 2.3 Amendments to Section 26 (Requirements Traceability Matrix)

**Add new row:**
SCR-033 | Forced Password Rotation | All | MUST
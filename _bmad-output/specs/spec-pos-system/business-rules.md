# Business Rules

- **BR-001**: A sale cannot be completed without required product, quantity, pricing, tax, and payment data.
- **BR-002**: A completed sale must create corresponding payment and inventory records according to configured transaction rules.
- **BR-003**: A cashier must have an open register session to perform cash-register operations.
- **BR-004**: Register closing must calculate expected cash from recorded register activity.
- **BR-005**: Actual closing cash must be entered before a session can be finalized.
- **BR-006**: Refunds must reference an original transaction where the business process requires it.
- **BR-007**: Discounts and promotions must respect eligibility, date, priority, and stacking rules.
- **BR-008**: Stock adjustments must include an authorized reason.
- **BR-009**: Expired-stock behavior must follow the configured store/product policy.
- **BR-010**: Supplier payments cannot exceed the payable balance unless an authorized overpayment workflow exists.
- **BR-011**: Closed register sessions must be protected from normal cashier edits.
- **BR-012**: All financial totals must use a consistent currency and configured precision.

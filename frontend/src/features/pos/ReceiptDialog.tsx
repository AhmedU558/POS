'use client';

import { SaleReceipt } from '@/lib/api/sales';
import { formatDateTime, formatMoney, formatQuantity } from '@/lib/format';
import { Modal } from '@/components/ui/Modal';
import { Button } from '@/components/ui/Button';

/**
 * The receipt.
 *
 * Every figure comes from the sale the server settled, including the payments — nothing here is
 * recomputed, so what the customer is handed matches what was recorded.
 */
export function ReceiptDialog({
  receipt,
  changeDue,
  onClose,
  onNextSale,
}: {
  receipt: SaleReceipt | null;
  changeDue: number | null;
  onClose: () => void;
  onNextSale?: () => void;
}) {
  if (!receipt) return null;

  return (
    <Modal
      open
      onClose={onClose}
      title={`Sale complete — ${receipt.receiptNumber}`}
      footer={
        <>
          <Button variant="secondary" icon="print" onClick={() => window.print()}>
            Print
          </Button>
          {onNextSale ? (
            <Button onClick={onNextSale} size="lg">
              Next sale
            </Button>
          ) : (
            <Button onClick={onClose}>Close</Button>
          )}
        </>
      }
    >
      <div className="receipt stack">
        {changeDue !== null && changeDue > 0.004 && (
          <div className="change-due">
            <span className="change-due__label">Change due</span>
            <span className="change-due__value">{formatMoney(changeDue)}</span>
          </div>
        )}

        <div className="receipt__header">
          <p className="receipt__store">{receipt.storeName}</p>
          <p className="text-small text-muted">{formatDateTime(receipt.createdAt)}</p>
          <p className="text-small text-muted">
            Receipt {receipt.receiptNumber}
            {receipt.cashierName ? ` · Served by ${receipt.cashierName}` : ''}
          </p>
          {receipt.customerName && <p className="text-small">Customer: {receipt.customerName}</p>}
        </div>

        <div>
          {receipt.items.map((item) => (
            <div className="receipt__line" key={`${item.productId}-${item.sku}`}>
              <span>
                {formatQuantity(item.quantity)} × {item.name}
                <span className="text-small text-muted"> {formatMoney(item.unitPrice)} each</span>
              </span>
              <span>{formatMoney(item.lineTotal)}</span>
            </div>
          ))}
        </div>

        <div className="receipt__totals">
          <div className="receipt__line">
            <span className="text-muted">Subtotal</span>
            <span>{formatMoney(receipt.subtotal)}</span>
          </div>
          {receipt.discountTotal > 0 && (
            <div className="receipt__line">
              <span className="text-muted">Discount</span>
              <span>−{formatMoney(receipt.discountTotal)}</span>
            </div>
          )}
          <div className="receipt__line">
            <span className="text-muted">Tax</span>
            <span>{formatMoney(receipt.taxTotal)}</span>
          </div>
          <div className="receipt__line" style={{ fontWeight: 'var(--font-weight-semibold)' }}>
            <span>Total</span>
            <span className="money">{formatMoney(receipt.grandTotal)}</span>
          </div>
        </div>

        <div className="receipt__totals">
          {receipt.payments.map((payment, index) => (
            <div className="receipt__line" key={index}>
              <span className="text-muted">{methodLabel(payment.paymentMethod)}</span>
              <span>{formatMoney(payment.amount)}</span>
            </div>
          ))}
        </div>
      </div>
    </Modal>
  );
}

/** Payments come back as method codes; a receipt should not read STORE_CREDIT. */
function methodLabel(code: string): string {
  return code.charAt(0) + code.slice(1).toLowerCase().replace(/_/g, ' ');
}

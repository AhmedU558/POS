'use client';

import { SaleReceipt } from '@/lib/api/sales';
import { formatDateTime, formatMoney, formatQuantity } from '@/lib/format';
import { Modal } from '@/components/ui/Modal';
import { Button } from '@/components/ui/Button';

/**
 * Thermal-receipt-style receipt dialog.
 *
 * Every figure comes from the sale the server settled, including the payments — nothing here is
 * recomputed, so what the customer is handed matches what was recorded.
 *
 * Designed for 58mm/80mm thermal printers when printed via window.print().
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
            Print Receipt
          </Button>
          {onNextSale ? (
            <Button onClick={onNextSale} size="lg">
              Next Sale
            </Button>
          ) : (
            <Button onClick={onClose}>Close</Button>
          )}
        </>
      }
    >
      <div className="receipt stack">
        {/* ---- Change due (prominent for cashier) ---- */}
        {changeDue !== null && changeDue > 0.004 && (
          <div className="change-due">
            <span className="change-due__label">Change due</span>
            <span className="change-due__value">{formatMoney(changeDue)}</span>
          </div>
        )}

        {/* ---- Store header ---- */}
        <div className="receipt__header">
          <p className="receipt__store">{receipt.storeName}</p>
          <p className="text-small text-muted">{formatDateTime(receipt.createdAt)}</p>
          <p className="text-small text-muted">
            Receipt {receipt.receiptNumber}
            {receipt.cashierName ? ` · Served by ${receipt.cashierName}` : ''}
          </p>
          {receipt.customerName && <p className="text-small">Customer: {receipt.customerName}</p>}
        </div>

        {/* ---- Line items ---- */}
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

        {/* ---- Totals ---- */}
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

        {/* ---- Payments ---- */}
        <div className="receipt__totals">
          {receipt.payments.map((payment, index) => (
            <div className="receipt__line" key={index}>
              <span className="text-muted">{methodLabel(payment.paymentMethod)}</span>
              <span>{formatMoney(payment.amount)}</span>
            </div>
          ))}
        </div>

        {/* ---- FBR section (renders only when real FBR data exists) ---- */}
        <FbrSection receipt={receipt} />

        {/* ---- Branding ---- */}
        <p className="receipt__branding">Powered by Aqvion Labs.com</p>
      </div>
    </Modal>
  );
}

/** Payments come back as method codes; a receipt should not read STORE_CREDIT. */
function methodLabel(code: string): string {
  return code.charAt(0) + code.slice(1).toLowerCase().replace(/_/g, ' ');
}

/**
 * FBR integration section.
 *
 * This renders only when the receipt carries real FBR data from the backend. The QR code and
 * invoice number are printed as part of the thermal receipt. When FBR integration is not
 * configured, nothing is rendered — no fake data is fabricated.
 */
function FbrSection({ receipt }: { receipt: SaleReceipt }) {
  // The receipt type would carry fbrInvoiceNumber and fbrQrCode when FBR integration is active.
  // These fields are populated server-side after successful FBR submission.
  const fbrData = receipt as SaleReceipt & {
    fbrInvoiceNumber?: string | null;
    fbrQrCode?: string | null;
  };

  if (!fbrData.fbrInvoiceNumber && !fbrData.fbrQrCode) {
    return null;
  }

  return (
    <div className="receipt__fbr">
      {fbrData.fbrInvoiceNumber && (
        <p className="text-small">
          <strong>FBR Invoice #:</strong> {fbrData.fbrInvoiceNumber}
        </p>
      )}
      {fbrData.fbrQrCode && (
        <div className="receipt__fbr-qr">
          {/* The QR code URL/data comes from the actual FBR response. Rendered as an image
              so it prints on thermal printers. */}
          <img
            src={fbrData.fbrQrCode}
            alt="FBR verification QR code"
            width={120}
            height={120}
            style={{ imageRendering: 'pixelated' }}
          />
        </div>
      )}
      <p className="text-small text-muted" style={{ marginTop: 'var(--space-1)' }}>
        Verify at FBR
      </p>
    </div>
  );
}

import Image from 'next/image';
import { SaleReceipt } from '@/lib/api/sales';
import { formatDateTime, formatMoney, formatQuantity } from '@/lib/format';

/**
 * Thermal receipt document suitable for isolated preview and printing.
 * 
 * Supports 58mm and 80mm paper widths.
 */
export function ThermalReceiptDocument({
  receipt,
  paper,
}: {
  receipt: SaleReceipt;
  paper: '58mm' | '80mm';
}) {
  const paperClass = paper === '80mm' ? 'receipt-80mm' : 'receipt-58mm';

  return (
    <div className={`thermal-receipt ${paperClass}`}>
      <div className="thermal-receipt__header">
        <h1 className="thermal-receipt__store">{receipt.storeName}</h1>
        {receipt.storeAddress && <p>{receipt.storeAddress}</p>}
        {receipt.storeContact && <p>{receipt.storeContact}</p>}
        <p>Terminal: {receipt.terminalName || 'Main'}</p>
        <p>{formatDateTime(receipt.createdAt)}</p>
        <p>
          Receipt #{receipt.receiptNumber}
          {receipt.cashierName ? ` - Served by ${receipt.cashierName}` : ''}
        </p>
        {receipt.customerName && <p>Customer: {receipt.customerName}</p>}
      </div>

      <div className="thermal-receipt__items">
        {receipt.items.map((item) => (
          <div className="thermal-receipt__line-item" key={`${item.productId}-${item.sku}`}>
            <div className="thermal-receipt__item-name">
              {formatQuantity(item.quantity)} x {item.name}
            </div>
            <div className="thermal-receipt__item-price">
              {formatMoney(item.unitPrice)}
            </div>
            <div className="thermal-receipt__item-total">
              {formatMoney(item.lineTotal)}
            </div>
          </div>
        ))}
      </div>

      <div className="thermal-receipt__totals">
        <div className="thermal-receipt__total-line">
          <span>Subtotal</span>
          <span>{formatMoney(receipt.subtotal)}</span>
        </div>
        {receipt.discountTotal > 0 && (
          <div className="thermal-receipt__total-line">
            <span>Discount</span>
            <span>-{formatMoney(receipt.discountTotal)}</span>
          </div>
        )}
        <div className="thermal-receipt__total-line">
          <span>Tax</span>
          <span>{formatMoney(receipt.taxTotal)}</span>
        </div>
        <div className="thermal-receipt__total-line thermal-receipt__grand-total">
          <span>Total</span>
          <span>{formatMoney(receipt.grandTotal)}</span>
        </div>
      </div>

      <div className="thermal-receipt__tender">
        {receipt.payments.map((payment, index) => (
          <div className="thermal-receipt__total-line" key={index}>
            <span>Tendered ({methodLabel(payment.paymentMethod)})</span>
            <span>{formatMoney(payment.amount)}</span>
          </div>
        ))}
        {receipt.changeAmount !== undefined && receipt.changeAmount > 0.004 && (
          <div className="thermal-receipt__total-line thermal-receipt__change">
            <span>Change</span>
            <span>{formatMoney(receipt.changeAmount)}</span>
          </div>
        )}
      </div>

      <FbrSection receipt={receipt} />

      <div className="thermal-receipt__footer">
        <p>Powered by Aqvion Labs.com</p>
      </div>
    </div>
  );
}

function methodLabel(code: string): string {
  return code.charAt(0) + code.slice(1).toLowerCase().replace(/_/g, ' ');
}

function FbrSection({ receipt }: { receipt: SaleReceipt }) {
  if (receipt.fbrStatus === 'SUBMISSION_SUCCESS' && (receipt.fbrInvoiceNumber || receipt.fbrQrCode)) {
    return (
      <div className="thermal-receipt__fbr">
        {receipt.fbrInvoiceNumber && (
          <p>
            <strong>FBR Invoice No:</strong> {receipt.fbrInvoiceNumber}
          </p>
        )}
        {receipt.fbrQrCode && (
          <div className="thermal-receipt__fbr-qr">
            <Image
              unoptimized
              src={receipt.fbrQrCode}
              alt="FBR verification QR code"
              width={150}
              height={150}
              style={{ imageRendering: 'pixelated' }}
            />
          </div>
        )}
        <p className="thermal-receipt__fbr-verify">Verify at FBR</p>
      </div>
    );
  }

  // Fallback for non-success statuses (for cashier visibility in preview/reprint)
  if (receipt.fbrStatusLabel && receipt.fbrStatusLabel !== 'Not configured' && receipt.fbrStatusLabel !== 'Not Configured') {
    return (
      <div className="thermal-receipt__fbr-status">
        <p>FBR Status: {receipt.fbrStatusLabel}</p>
      </div>
    );
  }
  
  if (receipt.fbrStatusLabel) {
      return (
      <div className="thermal-receipt__fbr-status">
        <p>{receipt.fbrStatusLabel}</p>
      </div>
    );
  }

  return null;
}

'use client';

import { SaleReceipt } from '@/lib/api/sales';
import { formatMoney } from '@/lib/format';
import { Modal } from '@/components/ui/Modal';
import { Button } from '@/components/ui/Button';
import { Icon } from '@/components/ui/Icon';

/**
 * Receipt completion dialog.
 * 
 * Provides links to the dedicated receipt print page.
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

  const printReceipt = (paper: '58mm' | '80mm') => {
    window.open(`/sales/receipt/${receipt.saleId}?paper=${paper}`, '_blank', 'width=400,height=600');
  };

  return (
    <Modal
      open
      onClose={onClose}
      title={`Sale complete – ${receipt.receiptNumber}`}
      footer={
        <>
          {onNextSale ? (
            <Button onClick={onNextSale} size="lg" variant="primary">
              Next Sale
            </Button>
          ) : (
            <Button onClick={onClose} variant="primary">Close</Button>
          )}
        </>
      }
    >
      <div className="receipt-actions stack" style={{ textAlign: 'center', padding: '2rem 1rem' }}>
        <div style={{ marginBottom: '1rem' }}>
          <Icon name="check" size={48} className="text-success" />
        </div>

        {/* ---- Change due (prominent for cashier) ---- */}
        {changeDue !== null && changeDue > 0.004 && (
          <div className="change-due" style={{ justifyContent: 'center', marginBottom: '2rem' }}>
            <span className="change-due__label">Change due</span>
            <span className="change-due__value">{formatMoney(changeDue)}</span>
          </div>
        )}

        <div style={{ display: 'flex', gap: '1rem', justifyContent: 'center', flexWrap: 'wrap' }}>
          <Button variant="secondary" icon="print" onClick={() => printReceipt('58mm')}>
            Preview 58mm
          </Button>
          <Button variant="secondary" icon="print" onClick={() => printReceipt('80mm')}>
            Preview 80mm
          </Button>
        </div>
      </div>
    </Modal>
  );
}


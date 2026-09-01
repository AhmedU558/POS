'use client';

import { SaleReceipt } from '@/lib/api/sales';
import { formatMoney } from '@/lib/format';
import { Modal } from '@/components/ui/Modal';
import { Button } from '@/components/ui/Button';
import { Icon } from '@/components/ui/Icon';
import { Badge } from '@/components/ui/Badge';

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

  const printReceipt = (paper: '58mm' | '80mm', autoPrint: boolean = false) => {
    window.open(`/sales/receipt/${receipt.saleId}?paper=${paper}${autoPrint ? '&print=true' : ''}`, '_blank', 'width=400,height=600');
  };

  const methods = Array.from(new Set(receipt.payments.map(p => p.paymentMethod))).join(', ');
  const tendered = receipt.tenderedAmount ?? receipt.payments.reduce((sum, p) => sum + p.amount, 0);

  return (
    <Modal
      open
      onClose={onClose}
      title="Sale Complete"
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>Close</Button>
          {onNextSale && (
            <Button onClick={onNextSale} size="lg" variant="primary">
              New Sale
            </Button>
          )}
        </>
      }
    >
      <div className="stack" style={{ padding: '1rem' }}>
        <div style={{ textAlign: 'center', marginBottom: '1rem' }}>
          <Icon name="check" size={48} className="text-success" />
          <h2 style={{ marginTop: '0.5rem', marginBottom: '0' }}>{receipt.receiptNumber}</h2>
        </div>

        <div className="form-grid form-grid--2" style={{ background: 'var(--color-surface-hover)', padding: '1rem', borderRadius: 'var(--radius-md)' }}>
          <div>
            <div className="text-small text-muted">Total Paid</div>
            <div style={{ fontWeight: 'var(--font-weight-bold)' }}>{formatMoney(receipt.grandTotal)}</div>
          </div>
          <div>
            <div className="text-small text-muted">Payment Method</div>
            <div>{methods || 'None'}</div>
          </div>
          <div>
            <div className="text-small text-muted">Amount Tendered</div>
            <div>{formatMoney(tendered)}</div>
          </div>
          <div>
            <div className="text-small text-muted">Change Due</div>
            <div style={{ fontWeight: 'var(--font-weight-bold)', color: changeDue && changeDue > 0 ? 'var(--color-primary)' : 'inherit' }}>
              {formatMoney(changeDue ?? receipt.changeAmount ?? 0)}
            </div>
          </div>
        </div>

        {receipt.fbrStatusLabel && (
          <div style={{ padding: '0.75rem', border: '1px solid var(--color-border)', borderRadius: 'var(--radius-md)', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <span className="text-small text-muted">FBR Integration</span>
            <div className="row row-sm">
              <Badge variant={receipt.fbrStatus === 'SUBMISSION_SUCCESS' ? 'success' : 'pending'}>
                {receipt.fbrStatusLabel}
              </Badge>
              {receipt.fbrInvoiceNumber && (
                <span className="text-small" style={{ fontFamily: 'monospace' }}>{receipt.fbrInvoiceNumber}</span>
              )}
            </div>
          </div>
        )}

        <div style={{ display: 'flex', gap: '1rem', justifyContent: 'center', flexWrap: 'wrap', marginTop: '1rem' }}>
          <Button variant="secondary" icon="print" onClick={() => printReceipt('58mm')}>
            Preview 58mm
          </Button>
          <Button variant="secondary" icon="print" onClick={() => printReceipt('80mm')}>
            Preview 80mm
          </Button>
          <Button variant="secondary" icon="print" onClick={() => printReceipt('80mm', true)}>
            Print Receipt
          </Button>
        </div>
      </div>
    </Modal>
  );
}


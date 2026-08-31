'use client';

import React, { useEffect, useMemo, useRef, useState } from 'react';
import { CASH, PaymentMethod, SalePaymentRequest, STORE_CREDIT } from '@/lib/api/sales';
import { formatMoney } from '@/lib/format';
import { Modal } from '@/components/ui/Modal';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Field';
import { Alert } from '@/components/ui/States';
import { Icon } from '@/components/ui/Icon';

/*
 * Taking payment.
 *
 * `total` is the server's figure for this sale, not the till's running estimate — the sale is
 * priced before this dialog opens, so promotions and tax are already settled and the cashier
 * tenders against the real number.
 *
 * Two server rules shape this screen:
 *   - A single CASH payment is recorded at the sale total whatever amount is sent, so cash
 *     tendered above the total is change rather than an overpayment.
 *   - Any other combination must sum to the total exactly, so a split is entered as amounts
 *     applied, and the cash line of a split is capped at the outstanding balance.
 */

export interface PaymentDialogProps {
  open: boolean;
  total: number;
  methods: PaymentMethod[];
  hasCustomer: boolean;
  isSubmitting: boolean;
  error: string | null;
  onCancel: () => void;
  onConfirm: (payments: SalePaymentRequest[], cashTendered: number | null) => void;
}

interface SplitLine {
  methodId: string;
  amount: string;
}

const QUICK_NOTES = [5, 10, 20, 50, 100];

export function PaymentDialog({
  open,
  total,
  methods,
  hasCustomer,
  isSubmitting,
  error,
  onCancel,
  onConfirm,
}: PaymentDialogProps) {
  const active = useMemo(() => methods.filter((method) => method.active), [methods]);
  const cashMethod = useMemo(() => active.find((method) => method.code === CASH), [active]);

  const [methodId, setMethodId] = useState('');
  const [tendered, setTendered] = useState('');
  const [isSplit, setIsSplit] = useState(false);
  const [splits, setSplits] = useState<SplitLine[]>([]);
  const tenderRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (!open) return;
    const initial = cashMethod?.id ?? active[0]?.id ?? '';
    setMethodId(initial);
    setTendered('');
    setIsSplit(false);
    setSplits([
      { methodId: initial, amount: total.toFixed(2) },
      { methodId: active.find((method) => method.id !== initial)?.id ?? initial, amount: '' },
    ]);
    // The amount field is the only thing a cashier types here, so it starts focused.
    window.setTimeout(() => tenderRef.current?.select(), 30);
  }, [open, cashMethod, active, total]);

  const selected = active.find((method) => method.id === methodId);
  const isCash = selected?.code === CASH;
  const tenderedValue = Number(tendered) || 0;
  const change = isCash ? tenderedValue - total : 0;
  const shortfall = isCash && tendered.trim() !== '' && tenderedValue < total;

  const splitTotal = splits.reduce((sum, line) => sum + (Number(line.amount) || 0), 0);
  const splitRemaining = round2(total - splitTotal);

  const creditWithoutCustomer =
    (!isSplit && selected?.code === STORE_CREDIT && !hasCustomer) ||
    (isSplit && splits.some((line) => active.find((m) => m.id === line.methodId)?.code === STORE_CREDIT) && !hasCustomer);

  const canConfirm = (() => {
    if (creditWithoutCustomer) return false;
    if (isSplit) return Math.abs(splitRemaining) < 0.005 && splits.every((line) => line.methodId);
    if (!methodId) return false;
    if (isCash) return tendered.trim() === '' || tenderedValue >= total;
    return true;
  })();

  const confirm = () => {
    if (!canConfirm) return;
    if (isSplit) {
      const payments = splits
        .filter((line) => line.methodId && Number(line.amount) > 0)
        .map((line) => ({ paymentMethodId: line.methodId, amount: round2(Number(line.amount)) }));
      const cashLine = splits.find((line) => active.find((m) => m.id === line.methodId)?.code === CASH);
      onConfirm(payments, cashLine ? Number(cashLine.amount) || 0 : null);
      return;
    }
    // A single cash payment is booked at the sale total; the tendered amount only drives change.
    onConfirm([{ paymentMethodId: methodId, amount: total }], isCash ? tenderedValue || total : null);
  };

  return (
    <Modal
      open={open}
      onClose={onCancel}
      title="Take payment"
      description={`Amount due ${formatMoney(total)}`}
      busy={isSubmitting}
      footer={
        <>
          <Button variant="secondary" onClick={onCancel} disabled={isSubmitting}>
            Back to cart
          </Button>
          <Button onClick={confirm} isLoading={isSubmitting} disabled={!canConfirm} size="lg">
            Complete sale
          </Button>
        </>
      }
    >
      <div className="stack">
        {error && <Alert tone="error">{error}</Alert>}

        <div className="total-row total-row--grand" style={{ borderTop: 'none', marginTop: 0, paddingTop: 0 }}>
          <span className="total-row__label">Amount due</span>
          <span className="total-row__value">{formatMoney(total)}</span>
        </div>

        {!isSplit ? (
          <>
            <fieldset style={{ border: 'none', padding: 0, margin: 0 }}>
              <legend className="field__label" style={{ marginBottom: 'var(--space-2)' }}>
                Payment method
              </legend>
              <div className="method-grid">
                {active.map((method) => (
                  <button
                    key={method.id}
                    type="button"
                    className="method-option"
                    aria-pressed={method.id === methodId}
                    onClick={() => setMethodId(method.id)}
                  >
                    {method.name}
                  </button>
                ))}
              </div>
            </fieldset>

            {isCash && (
              <>
                <Input
                  id="pos-tendered"
                  ref={tenderRef}
                  label="Cash received"
                  type="number"
                  inputMode="decimal"
                  step="0.01"
                  min="0"
                  inputSize="lg"
                  value={tendered}
                  hint="Leave blank for exact money."
                  onChange={(event) => setTendered(event.target.value)}
                  onKeyDown={(event) => {
                    if (event.key === 'Enter' && canConfirm) {
                      event.preventDefault();
                      confirm();
                    }
                  }}
                />
                <div className="tender-grid">
                  <button type="button" className="tender-chip" onClick={() => setTendered(total.toFixed(2))}>
                    Exact
                  </button>
                  {QUICK_NOTES.filter((note) => note >= total).slice(0, 4).map((note) => (
                    <button key={note} type="button" className="tender-chip" onClick={() => setTendered(note.toFixed(2))}>
                      {formatMoney(note)}
                    </button>
                  ))}
                  <button
                    type="button"
                    className="tender-chip"
                    onClick={() => setTendered((Math.ceil(total / 10) * 10).toFixed(2))}
                  >
                    {formatMoney(Math.ceil(total / 10) * 10)}
                  </button>
                </div>

                {tendered.trim() !== '' && (
                  <div className={['change-due', shortfall ? 'change-due--short' : ''].filter(Boolean).join(' ')} role="status">
                    <span className="change-due__label">
                      <Icon name={shortfall ? 'alert' : 'cash'} size={16} /> {shortfall ? 'Still owing' : 'Change due'}
                    </span>
                    <span className="change-due__value">{formatMoney(Math.abs(change))}</span>
                  </div>
                )}
              </>
            )}
          </>
        ) : (
          <>
            <p className="text-small text-muted">
              Enter what each method takes. The amounts must add up to {formatMoney(total)}.
            </p>
            {splits.map((line, index) => (
              <div className="row" key={index}>
                <div className="grow">
                  <label className="field__label" htmlFor={`split-method-${index}`}>
                    Method {index + 1}
                  </label>
                  <select
                    id={`split-method-${index}`}
                    className="control"
                    value={line.methodId}
                    onChange={(event) =>
                      setSplits((current) =>
                        current.map((item, i) => (i === index ? { ...item, methodId: event.target.value } : item))
                      )
                    }
                  >
                    {active.map((method) => (
                      <option key={method.id} value={method.id}>
                        {method.name}
                      </option>
                    ))}
                  </select>
                </div>
                <div style={{ width: '9rem' }}>
                  <Input
                    id={`split-amount-${index}`}
                    label="Amount"
                    type="number"
                    step="0.01"
                    min="0"
                    inputMode="decimal"
                    value={line.amount}
                    onChange={(event) =>
                      setSplits((current) =>
                        current.map((item, i) => (i === index ? { ...item, amount: event.target.value } : item))
                      )
                    }
                  />
                </div>
                {splits.length > 2 && (
                  <Button
                    variant="ghost"
                    size="sm"
                    icon="trash"
                    aria-label={`Remove method ${index + 1}`}
                    onClick={() => setSplits((current) => current.filter((_, i) => i !== index))}
                  />
                )}
              </div>
            ))}
            <div className="row row-between">
              <Button
                variant="secondary"
                size="sm"
                icon="plus"
                onClick={() =>
                  setSplits((current) => [
                    ...current,
                    { methodId: active[0]?.id ?? '', amount: Math.max(splitRemaining, 0).toFixed(2) },
                  ])
                }
              >
                Add method
              </Button>
              <span className={Math.abs(splitRemaining) < 0.005 ? 'text-muted' : 'field__error'}>
                {Math.abs(splitRemaining) < 0.005
                  ? 'Amounts balance'
                  : splitRemaining > 0
                    ? `${formatMoney(splitRemaining)} still to allocate`
                    : `${formatMoney(-splitRemaining)} over the total`}
              </span>
            </div>
          </>
        )}

        {creditWithoutCustomer && (
          <Alert tone="warning">Store credit needs a customer on the sale. Close this and add one to the cart first.</Alert>
        )}

        {active.length > 1 && (
          <Button variant="ghost" size="sm" onClick={() => setIsSplit((current) => !current)}>
            {isSplit ? 'Pay with one method' : 'Split across methods'}
          </Button>
        )}
      </div>
    </Modal>
  );
}

function round2(value: number): number {
  return Math.round(value * 100) / 100;
}

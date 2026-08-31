'use client';

import { useCallback, useEffect, useState } from 'react';
import Link from 'next/link';
import { useAuth } from '@/features/auth/AuthContext';
import { useStoreContext } from '@/features/session/StoreContext';
import { Register, registersApi } from '@/lib/api/organization';
import {
  RegisterClosingReport,
  RegisterSessionSummary,
  registerSessionsApi,
} from '@/lib/api/register-sessions';
import { errorMessage, formatDateTime, formatMoney } from '@/lib/format';
import { P, hasPermission } from '@/lib/permissions';
import { PageHeader } from '@/components/ui/PageHeader';
import { Card, CardBody, CardHeader, DetailItem, DetailList, Metric } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Input, Select, Textarea } from '@/components/ui/Field';
import { Badge } from '@/components/ui/Badge';
import { Modal } from '@/components/ui/Modal';
import { Alert, EmptyState, LoadingState, PermissionRequired } from '@/components/ui/States';
import { useToast } from '@/components/ui/Toast';
import { Icon } from '@/components/ui/Icon';

/**
 * The register: open → cash in and out → close → Z report.
 *
 * Expected cash and variance are read from the API and never recomputed here (UI/UX Specification
 * 11.3 and 14). What the drawer *should* hold is an accounting figure, and a second implementation
 * of it in the browser is a second answer waiting to disagree with the first.
 */
export default function RegisterPage() {
  const { user } = useAuth();
  const { session, activeStore, activeStoreId, refresh, isLoading: contextLoading } = useStoreContext();
  const toast = useToast();

  const canOpen = hasPermission(user?.permissions, P.REGISTER_OPEN);
  const canCash = hasPermission(user?.permissions, P.REGISTER_CASH);
  const canClose = hasPermission(user?.permissions, P.REGISTER_CLOSE);
  const canReadRegisters = hasPermission(user?.permissions, P.REGISTER_READ);

  const [registers, setRegisters] = useState<Register[]>([]);
  const [summary, setSummary] = useState<RegisterSessionSummary | null>(null);
  const [report, setReport] = useState<RegisterClosingReport | null>(null);
  const [isBusy, setIsBusy] = useState(false);
  const [loadError, setLoadError] = useState<string | null>(null);

  const [openDialog, setOpenDialog] = useState(false);
  const [cashDialog, setCashDialog] = useState<'in' | 'out' | null>(null);
  const [closeDialog, setCloseDialog] = useState(false);

  const loadSummary = useCallback(async () => {
    if (!session) {
      setSummary(null);
      return;
    }
    try {
      setSummary(await registerSessionsApi.summary(session.id));
    } catch (caught) {
      setLoadError(errorMessage(caught));
    }
  }, [session]);

  useEffect(() => {
    void loadSummary();
  }, [loadSummary]);

  useEffect(() => {
    if (!activeStoreId || session) return;
    registersApi
      .list(activeStoreId)
      .then((list) => setRegisters(list.filter((register) => register.status === 'ACTIVE')))
      .catch(() => setRegisters([]));
  }, [activeStoreId, session]);

  if (!canOpen && !canClose && !canCash && !canReadRegisters) {
    return (
      <div className="page">
        <PermissionRequired permission={P.REGISTER_OPEN} action="Working a register" />
      </div>
    );
  }

  if (contextLoading) {
    return (
      <div className="page">
        <LoadingState label="Checking the register…" />
      </div>
    );
  }

  return (
    <div className="page">
      <PageHeader
        title="Register"
        description={
          session
            ? 'Your till is open. Record cash in and out during the shift, then close it to count the drawer.'
            : 'Open a register to start a shift. You will be asked for the cash you are starting with.'
        }
        actions={
          session && (
            <Link className="btn btn--primary" href="/pos">
              Go to the till
            </Link>
          )
        }
      />

      {loadError && (
        <div style={{ marginBottom: 'var(--space-4)' }}>
          <Alert tone="error">{loadError}</Alert>
        </div>
      )}

      {report ? (
        <ZReport report={report} onDone={() => setReport(null)} />
      ) : !session ? (
        <ClosedState
          registers={registers}
          canOpen={canOpen}
          storeName={activeStore?.name}
          onOpen={() => setOpenDialog(true)}
        />
      ) : (
        <div className="stack-lg stack">
          <div className="metric-grid">
            <Metric
              label="Opening float"
              value={formatMoney(summary?.openingCash ?? session.openingCash)}
              meta={`Opened ${formatDateTime(session.openedAt)}`}
            />
            <Metric label="Cash sales" value={formatMoney(summary?.cashSalesTotal)} meta="Taken at the till this shift" />
            <Metric
              label="Cash in / out"
              value={`${formatMoney(summary?.cashInTotal)} / ${formatMoney(summary?.cashOutTotal)}`}
              meta="Paid in and paid out"
            />
            <Metric
              label="Expected in drawer"
              value={formatMoney(summary?.expectedCash)}
              meta={<Badge variant="info">Calculated by the system</Badge>}
            />
          </div>

          <Card>
            <CardHeader
              title="During the shift"
              actions={<Badge variant="success">Open</Badge>}
            />
            <CardBody>
              <div className="row row-wrap">
                {canCash && (
                  <>
                    <Button icon="plus" onClick={() => setCashDialog('in')}>
                      Cash in
                    </Button>
                    <Button variant="secondary" icon="minus" onClick={() => setCashDialog('out')}>
                      Cash out
                    </Button>
                  </>
                )}
                {canClose && (
                  <Button variant="danger" onClick={() => setCloseDialog(true)}>
                    Close register
                  </Button>
                )}
              </div>
              <p className="text-small text-muted" style={{ marginTop: 'var(--space-3)' }}>
                Cash in records money added to the drawer — a float top-up or a paid-in. Cash out records money taken
                out, such as a supplier paid in cash. Both change what the drawer is expected to hold at close.
              </p>
            </CardBody>
          </Card>
        </div>
      )}

      <OpenRegisterDialog
        open={openDialog}
        registers={registers}
        isBusy={isBusy}
        onCancel={() => setOpenDialog(false)}
        onConfirm={async (registerId, openingCash) => {
          setIsBusy(true);
          try {
            await registerSessionsApi.open(registerId, openingCash);
            await refresh();
            setOpenDialog(false);
            toast.success('Register open. You can start selling.');
          } catch (caught) {
            toast.error(errorMessage(caught));
          } finally {
            setIsBusy(false);
          }
        }}
      />

      <CashMovementDialog
        direction={cashDialog}
        isBusy={isBusy}
        onCancel={() => setCashDialog(null)}
        onConfirm={async (amount, reason) => {
          if (!session || !cashDialog) return;
          setIsBusy(true);
          try {
            if (cashDialog === 'in') {
              await registerSessionsApi.cashIn(session.id, amount, reason);
            } else {
              await registerSessionsApi.cashOut(session.id, amount, reason);
            }
            await loadSummary();
            setCashDialog(null);
            toast.success(cashDialog === 'in' ? 'Cash in recorded.' : 'Cash out recorded.');
          } catch (caught) {
            toast.error(errorMessage(caught));
          } finally {
            setIsBusy(false);
          }
        }}
      />

      <CloseRegisterDialog
        open={closeDialog}
        expectedCash={summary?.expectedCash ?? 0}
        isBusy={isBusy}
        onCancel={() => setCloseDialog(false)}
        onConfirm={async (actualCash, notes) => {
          if (!session) return;
          setIsBusy(true);
          try {
            const closed = await registerSessionsApi.close(session.id, actualCash, notes);
            setReport(closed);
            setCloseDialog(false);
            await refresh();
          } catch (caught) {
            toast.error(errorMessage(caught));
          } finally {
            setIsBusy(false);
          }
        }}
      />
    </div>
  );
}

function ClosedState({
  registers,
  canOpen,
  storeName,
  onOpen,
}: {
  registers: Register[];
  canOpen: boolean;
  storeName?: string;
  onOpen: () => void;
}) {
  if (registers.length === 0) {
    return (
      <Card>
        <CardBody>
          <EmptyState
            icon="register"
            title="No registers in this store"
            body={`${storeName ?? 'This store'} has no active register yet. A register is the cash drawer a cashier opens at the start of a shift — one has to exist before any sale can be recorded.`}
            action={{ label: 'Set up a register', href: '/setup' }}
          />
        </CardBody>
      </Card>
    );
  }

  return (
    <Card>
      <CardBody>
        <EmptyState
          icon="register"
          title="Your till is closed"
          body="Open a register to start a shift. You will be asked how much cash is in the drawer, so it can be counted against sales at the end."
          action={canOpen ? { label: 'Open a register', onClick: onOpen } : undefined}
        />
      </CardBody>
    </Card>
  );
}

function OpenRegisterDialog({
  open,
  registers,
  isBusy,
  onCancel,
  onConfirm,
}: {
  open: boolean;
  registers: Register[];
  isBusy: boolean;
  onCancel: () => void;
  onConfirm: (registerId: string, openingCash: number) => Promise<void>;
}) {
  const [registerId, setRegisterId] = useState('');
  const [openingCash, setOpeningCash] = useState('0');
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (open) {
      setRegisterId(registers[0]?.id ?? '');
      setOpeningCash('0');
      setError(null);
    }
  }, [open, registers]);

  return (
    <Modal
      open={open}
      onClose={onCancel}
      title="Open register"
      description="Count the cash in the drawer before you start. This is the figure the drawer is checked against at close."
      busy={isBusy}
      footer={
        <>
          <Button variant="secondary" onClick={onCancel} disabled={isBusy}>
            Cancel
          </Button>
          <Button
            isLoading={isBusy}
            onClick={() => {
              const amount = Number(openingCash);
              if (!registerId) {
                setError('Choose which register you are opening.');
                return;
              }
              if (!Number.isFinite(amount) || amount < 0) {
                setError('Enter the opening cash amount. Use 0 for an empty drawer.');
                return;
              }
              void onConfirm(registerId, amount);
            }}
          >
            Open register
          </Button>
        </>
      }
    >
      <div className="stack">
        {error && <Alert tone="error">{error}</Alert>}
        <Select
          id="register-choice"
          label="Register"
          required
          placeholder={null}
          value={registerId}
          onChange={(event) => setRegisterId(event.target.value)}
          options={registers.map((register) => ({ value: register.id, label: `${register.name} (${register.code})` }))}
        />
        <Input
          id="opening-cash"
          label="Opening cash in drawer"
          required
          type="number"
          min="0"
          step="0.01"
          inputMode="decimal"
          inputSize="lg"
          value={openingCash}
          onChange={(event) => setOpeningCash(event.target.value)}
        />
      </div>
    </Modal>
  );
}

function CashMovementDialog({
  direction,
  isBusy,
  onCancel,
  onConfirm,
}: {
  direction: 'in' | 'out' | null;
  isBusy: boolean;
  onCancel: () => void;
  onConfirm: (amount: number, reason: string) => Promise<void>;
}) {
  const [amount, setAmount] = useState('');
  const [reason, setReason] = useState('');
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (direction) {
      setAmount('');
      setReason('');
      setError(null);
    }
  }, [direction]);

  return (
    <Modal
      open={direction !== null}
      onClose={onCancel}
      title={direction === 'out' ? 'Take cash out' : 'Put cash in'}
      description={
        direction === 'out'
          ? 'Money leaving the drawer for something other than a refund.'
          : 'Money added to the drawer that is not a sale.'
      }
      busy={isBusy}
      footer={
        <>
          <Button variant="secondary" onClick={onCancel} disabled={isBusy}>
            Cancel
          </Button>
          <Button
            isLoading={isBusy}
            onClick={() => {
              const value = Number(amount);
              if (!Number.isFinite(value) || value <= 0) {
                setError('Enter an amount greater than zero.');
                return;
              }
              void onConfirm(value, reason.trim());
            }}
          >
            Record
          </Button>
        </>
      }
    >
      <div className="stack">
        {error && <Alert tone="error">{error}</Alert>}
        <Input
          id="cash-amount"
          label="Amount"
          required
          type="number"
          min="0"
          step="0.01"
          inputMode="decimal"
          inputSize="lg"
          value={amount}
          onChange={(event) => setAmount(event.target.value)}
          autoFocus
        />
        <Textarea
          id="cash-reason"
          label="Reason"
          rows={2}
          value={reason}
          hint="Recorded against the shift, and shown on the closing report."
          onChange={(event) => setReason(event.target.value)}
        />
      </div>
    </Modal>
  );
}

function CloseRegisterDialog({
  open,
  expectedCash,
  isBusy,
  onCancel,
  onConfirm,
}: {
  open: boolean;
  expectedCash: number;
  isBusy: boolean;
  onCancel: () => void;
  onConfirm: (actualCash: number, notes: string) => Promise<void>;
}) {
  const [actualCash, setActualCash] = useState('');
  const [notes, setNotes] = useState('');
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (open) {
      setActualCash('');
      setNotes('');
      setError(null);
    }
  }, [open]);

  /*
   * The difference is previewed so the cashier can recount before committing, but the figure that
   * is recorded is the one the server calculates when the session closes.
   */
  const counted = Number(actualCash);
  const preview = actualCash.trim() !== '' && Number.isFinite(counted) ? counted - expectedCash : null;

  return (
    <Modal
      open={open}
      onClose={onCancel}
      title="Close register"
      description="Count the drawer and enter what is actually in it. Closing is final for this shift."
      busy={isBusy}
      footer={
        <>
          <Button variant="secondary" onClick={onCancel} disabled={isBusy}>
            Cancel
          </Button>
          <Button
            variant="danger"
            isLoading={isBusy}
            onClick={() => {
              if (!Number.isFinite(counted) || actualCash.trim() === '' || counted < 0) {
                setError('Enter the amount you counted in the drawer.');
                return;
              }
              void onConfirm(counted, notes.trim());
            }}
          >
            Close and print Z report
          </Button>
        </>
      }
    >
      <div className="stack">
        {error && <Alert tone="error">{error}</Alert>}
        <div className="total-row">
          <span className="total-row__label">Expected in drawer</span>
          <span className="total-row__value money">{formatMoney(expectedCash)}</span>
        </div>
        <Input
          id="actual-cash"
          label="Counted in drawer"
          required
          type="number"
          min="0"
          step="0.01"
          inputMode="decimal"
          inputSize="lg"
          value={actualCash}
          onChange={(event) => setActualCash(event.target.value)}
          autoFocus
        />
        {preview !== null && Math.abs(preview) >= 0.005 && (
          <Alert tone={Math.abs(preview) > 0 ? 'warning' : 'info'}>
            That is {formatMoney(Math.abs(preview))} {preview > 0 ? 'more' : 'less'} than expected. Recount before
            closing if that looks wrong — the difference is recorded against this shift.
          </Alert>
        )}
        <Textarea
          id="close-notes"
          label="Notes"
          rows={2}
          value={notes}
          hint="Optional. Explain any difference here."
          onChange={(event) => setNotes(event.target.value)}
        />
      </div>
    </Modal>
  );
}

function ZReport({ report, onDone }: { report: RegisterClosingReport; onDone: () => void }) {
  const over = report.variance > 0.004;
  const short = report.variance < -0.004;

  return (
    <Card>
      <CardHeader
        title={`Z report ${report.zReportNumber}`}
        actions={
          <>
            <Button variant="secondary" icon="print" onClick={() => window.print()}>
              Print
            </Button>
            <Button onClick={onDone}>Done</Button>
          </>
        }
      />
      <CardBody className="stack-lg stack">
        {over || short ? (
          <Alert tone="warning" title={over ? 'Drawer over' : 'Drawer short'}>
            The drawer held {formatMoney(Math.abs(report.variance))} {over ? 'more' : 'less'} than expected. This has been
            recorded against the shift.
          </Alert>
        ) : (
          <Alert tone="success" title="Drawer balanced">
            The counted cash matched the expected amount exactly.
          </Alert>
        )}

        <div className="metric-grid">
          <Metric label="Expected" value={formatMoney(report.expectedCash)} />
          <Metric label="Counted" value={formatMoney(report.actualCash)} />
          <Metric
            label="Difference"
            value={formatMoney(report.variance)}
            meta={over ? 'Over' : short ? 'Short' : 'Balanced'}
          />
        </div>

        <DetailList>
          <DetailItem label="Opening float">{formatMoney(report.openingCash)}</DetailItem>
          <DetailItem label="Cash sales">{formatMoney(report.cashSalesTotal)}</DetailItem>
          <DetailItem label="Cash in">{formatMoney(report.cashInTotal)}</DetailItem>
          <DetailItem label="Cash out">{formatMoney(report.cashOutTotal)}</DetailItem>
          <DetailItem label="Opened">{formatDateTime(report.openedAt)}</DetailItem>
          <DetailItem label="Closed">{formatDateTime(report.closedAt)}</DetailItem>
        </DetailList>

        {report.notes && (
          <p className="text-muted">
            <Icon name="info" size={14} /> {report.notes}
          </p>
        )}
      </CardBody>
    </Card>
  );
}

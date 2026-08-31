'use client';

import { useCallback, useEffect, useState } from 'react';
import { useAuth } from '@/features/auth/AuthContext';
import { useStoreContext } from '@/features/session/StoreContext';
import {
  Register,
  Store,
  Terminal,
  registersApi,
  storesApi,
  terminalsApi,
} from '@/lib/api/organization';
import { errorMessage } from '@/lib/format';
import { P, hasPermission } from '@/lib/permissions';
import { PageHeader } from '@/components/ui/PageHeader';
import { Card, CardBody, CardHeader } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Checkbox, Input, Select } from '@/components/ui/Field';
import { ActiveBadge, Badge } from '@/components/ui/Badge';
import { Table, Tbody, Td, Th, Thead, Tr } from '@/components/ui/Table';
import { Modal } from '@/components/ui/Modal';
import { Alert, EmptyState, ErrorState, LoadingState, PermissionRequired } from '@/components/ui/States';
import { useToast } from '@/components/ui/Toast';

/**
 * Store setup: the chain a sale depends on.
 *
 * A sale needs an open register session; a session needs a register; a register needs a terminal;
 * a terminal needs a store. Every one of those endpoints existed with no screen in front of it,
 * so a freshly installed system had no path to its first sale at all. This screen is that path,
 * and it states the chain plainly rather than assuming the user knows it.
 */
export default function SetupPage() {
  const { user } = useAuth();
  const { refresh } = useStoreContext();

  const canReadStores = hasPermission(user?.permissions, P.STORE_READ);
  const canWriteStores = hasPermission(user?.permissions, P.STORE_WRITE);
  const canWriteTerminals = hasPermission(user?.permissions, P.TERMINAL_WRITE);
  const canWriteRegisters = hasPermission(user?.permissions, P.REGISTER_WRITE);

  const [stores, setStores] = useState<Store[] | null>(null);
  const [selectedStoreId, setSelectedStoreId] = useState<string>('');
  const [terminals, setTerminals] = useState<Terminal[]>([]);
  const [registers, setRegisters] = useState<Register[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [isLoadingChildren, setIsLoadingChildren] = useState(false);

  const loadStores = useCallback(async () => {
    setError(null);
    try {
      const loaded = await storesApi.list();
      setStores(loaded);
      setSelectedStoreId((current) => current || loaded[0]?.id || '');
    } catch (caught) {
      setError(errorMessage(caught));
      setStores([]);
    }
  }, []);

  const loadChildren = useCallback(async (storeId: string) => {
    if (!storeId) {
      setTerminals([]);
      setRegisters([]);
      return;
    }
    setIsLoadingChildren(true);
    const [terminalResult, registerResult] = await Promise.allSettled([
      terminalsApi.list(storeId),
      registersApi.list(storeId),
    ]);
    setTerminals(terminalResult.status === 'fulfilled' ? terminalResult.value : []);
    setRegisters(registerResult.status === 'fulfilled' ? registerResult.value : []);
    setIsLoadingChildren(false);
  }, []);

  useEffect(() => {
    if (canReadStores) void loadStores();
  }, [canReadStores, loadStores]);

  useEffect(() => {
    void loadChildren(selectedStoreId);
  }, [selectedStoreId, loadChildren]);

  if (!canReadStores) {
    return (
      <div className="page">
        <PermissionRequired permission={P.STORE_READ} action="Store setup" />
      </div>
    );
  }

  if (error) {
    return (
      <div className="page">
        <ErrorState message={error} onRetry={() => void loadStores()} />
      </div>
    );
  }

  if (stores === null) {
    return (
      <div className="page">
        <LoadingState label="Loading setup…" />
      </div>
    );
  }

  const selectedStore = stores.find((store) => store.id === selectedStoreId) ?? null;

  return (
    <div className="page">
      <PageHeader
        title="Setup"
        description="Your stores and the tills in them. A register must exist before anyone can open a till and start selling."
      />

      <div className="stack-lg stack">
        <SetupProgress store={selectedStore} terminals={terminals} registers={registers} />

        <StoresCard
          stores={stores}
          canWrite={canWriteStores}
          selectedStoreId={selectedStoreId}
          onSelect={setSelectedStoreId}
          onChanged={async () => {
            await loadStores();
            await refresh();
          }}
        />

        {selectedStore && (
          <>
            <TerminalsCard
              store={selectedStore}
              terminals={terminals}
              canWrite={canWriteTerminals}
              isLoading={isLoadingChildren}
              onChanged={() => void loadChildren(selectedStore.id)}
            />
            <RegistersCard
              store={selectedStore}
              terminals={terminals}
              registers={registers}
              canWrite={canWriteRegisters}
              isLoading={isLoadingChildren}
              onChanged={() => void loadChildren(selectedStore.id)}
            />
          </>
        )}
      </div>
    </div>
  );
}

/** Names the remaining step, so "why can't I sell yet?" has a visible answer. */
function SetupProgress({
  store,
  terminals,
  registers,
}: {
  store: Store | null;
  terminals: Terminal[];
  registers: Register[];
}) {
  if (!store) {
    return (
      <Alert tone="info" title="Start here">
        Create a store, add a terminal to it, then add a register. Once a register exists, a cashier can open a till and
        the point of sale becomes usable.
      </Alert>
    );
  }

  const activeRegisters = registers.filter((register) => register.status === 'ACTIVE');
  if (activeRegisters.length > 0) {
    return (
      <Alert tone="success" title="Ready to sell">
        {store.name} has {activeRegisters.length} active {activeRegisters.length === 1 ? 'register' : 'registers'}. Open a
        till from the Register screen to start taking payments.
      </Alert>
    );
  }
  if (terminals.length === 0) {
    return (
      <Alert tone="warning" title="Next: add a terminal">
        A terminal is the physical till point in {store.name}. Add one, then add a register to it.
      </Alert>
    );
  }
  return (
    <Alert tone="warning" title="Next: add a register">
      {store.name} has a terminal but no active register. A register is the cash drawer a cashier opens at the start of a
      shift — no sale can be recorded without one.
    </Alert>
  );
}

function StoresCard({
  stores,
  canWrite,
  selectedStoreId,
  onSelect,
  onChanged,
}: {
  stores: Store[];
  canWrite: boolean;
  selectedStoreId: string;
  onSelect: (id: string) => void;
  onChanged: () => Promise<void>;
}) {
  const toast = useToast();
  const [editing, setEditing] = useState<Store | 'new' | null>(null);
  const [code, setCode] = useState('');
  const [name, setName] = useState('');
  const [currencyCode, setCurrencyCode] = useState('USD');
  const [timezone, setTimezone] = useState(guessTimezone());
  const [isSaving, setIsSaving] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);

  const open = (store: Store | 'new') => {
    setEditing(store);
    setFormError(null);
    setCode(store === 'new' ? '' : store.code);
    setName(store === 'new' ? '' : store.name);
    setCurrencyCode(store === 'new' ? 'USD' : store.currencyCode);
    setTimezone(store === 'new' ? guessTimezone() : store.timezone);
  };

  const save = async () => {
    if (!code.trim() || !name.trim()) {
      setFormError('A store needs a short code and a name.');
      return;
    }
    if (!/^[A-Za-z]{3}$/.test(currencyCode.trim())) {
      setFormError('Use a three-letter currency code, such as USD or GBP.');
      return;
    }
    setIsSaving(true);
    setFormError(null);
    try {
      const body = {
        code: code.trim(),
        name: name.trim(),
        currencyCode: currencyCode.trim().toUpperCase(),
        timezone: timezone.trim(),
      };
      if (editing === 'new') {
        const created = await storesApi.create(body);
        onSelect(created.id);
      } else if (editing) {
        await storesApi.update(editing.id, body);
      }
      setEditing(null);
      await onChanged();
    } catch (caught) {
      setFormError(errorMessage(caught));
    } finally {
      setIsSaving(false);
    }
  };

  const setStatus = async (store: Store, active: boolean) => {
    try {
      await storesApi.setStatus(store.id, active);
      await onChanged();
      toast.success(active ? `${store.name} reopened.` : `${store.name} closed.`);
    } catch (caught) {
      toast.error(errorMessage(caught));
    }
  };

  return (
    <Card flush>
      <CardHeader
        title="Stores"
        actions={canWrite ? <Button icon="plus" size="sm" onClick={() => open('new')}>Add store</Button> : undefined}
      />
      {stores.length === 0 ? (
        <EmptyState
          icon="store"
          title="Set up your first store"
          body="Everything hangs off a store: products are sold in it, stock is held in it, and its tills belong to it."
          action={canWrite ? { label: 'Add store', onClick: () => open('new') } : undefined}
        />
      ) : (
      <Table>
        <Thead>
          <Tr>
            <Th>Store</Th>
            <Th>Code</Th>
            <Th>Currency</Th>
            <Th>Time zone</Th>
            <Th>Status</Th>
            <Th className="table__actions">Actions</Th>
          </Tr>
        </Thead>
        <Tbody>
          {stores.map((store) => (
            <Tr key={store.id}>
              <Td>
                <button
                  type="button"
                  className="btn btn--ghost btn--sm"
                  onClick={() => onSelect(store.id)}
                  aria-pressed={store.id === selectedStoreId}
                >
                  <span className="table__primary">{store.name}</span>
                </button>
                {store.id === selectedStoreId && <Badge variant="info">Showing tills</Badge>}
              </Td>
              <Td>
                <span className="mono">{store.code}</span>
              </Td>
              <Td>{store.currencyCode}</Td>
              <Td className="text-muted">{store.timezone}</Td>
              <Td>
                <ActiveBadge active={store.active} />
              </Td>
              <Td className="table__actions">
                {canWrite && (
                  <>
                    <Button variant="secondary" size="sm" onClick={() => open(store)}>
                      Edit
                    </Button>
                    <Button variant="ghost" size="sm" onClick={() => void setStatus(store, !store.active)}>
                      {store.active ? 'Close' : 'Reopen'}
                    </Button>
                  </>
                )}
              </Td>
            </Tr>
          ))}
        </Tbody>
      </Table>
      )}

      <Modal
        open={editing !== null}
        onClose={() => setEditing(null)}
        title={editing === 'new' ? 'Add store' : 'Edit store'}
        busy={isSaving}
        footer={
          <>
            <Button variant="secondary" onClick={() => setEditing(null)} disabled={isSaving}>
              Cancel
            </Button>
            <Button onClick={() => void save()} isLoading={isSaving}>
              Save store
            </Button>
          </>
        }
      >
        <div className="stack">
          {formError && <p className="field__error">{formError}</p>}
          <Input id="store-name" label="Store name" required value={name} onChange={(event) => setName(event.target.value)} />
          <Input
            id="store-code"
            label="Store code"
            required
            value={code}
            hint="A short identifier, such as MAIN or LON-01."
            onChange={(event) => setCode(event.target.value)}
          />
          <div className="form-grid form-grid--2">
            <Input
              id="store-currency"
              label="Currency"
              required
              maxLength={3}
              value={currencyCode}
              hint="Three-letter code, e.g. USD."
              onChange={(event) => setCurrencyCode(event.target.value.toUpperCase())}
            />
            <Input
              id="store-timezone"
              label="Time zone"
              required
              value={timezone}
              hint="Used for daily sales totals."
              onChange={(event) => setTimezone(event.target.value)}
            />
          </div>
        </div>
      </Modal>
    </Card>
  );
}

function TerminalsCard({
  store,
  terminals,
  canWrite,
  isLoading,
  onChanged,
}: {
  store: Store;
  terminals: Terminal[];
  canWrite: boolean;
  isLoading: boolean;
  onChanged: () => void;
}) {
  const toast = useToast();
  const [editing, setEditing] = useState<Terminal | 'new' | null>(null);
  const [code, setCode] = useState('');
  const [name, setName] = useState('');
  const [active, setActive] = useState(true);
  const [isSaving, setIsSaving] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);

  const open = (terminal: Terminal | 'new') => {
    setEditing(terminal);
    setFormError(null);
    setCode(terminal === 'new' ? `T${terminals.length + 1}` : terminal.code);
    setName(terminal === 'new' ? `Terminal ${terminals.length + 1}` : terminal.name);
    setActive(terminal === 'new' ? true : terminal.status === 'ACTIVE');
  };

  const save = async () => {
    if (!code.trim() || !name.trim()) {
      setFormError('A terminal needs a code and a name.');
      return;
    }
    setIsSaving(true);
    setFormError(null);
    try {
      const body = { code: code.trim(), name: name.trim(), status: active ? 'ACTIVE' : 'INACTIVE' };
      if (editing === 'new') {
        await terminalsApi.create(store.id, body);
        toast.success('Terminal added.');
      } else if (editing) {
        await terminalsApi.update(store.id, editing.id, body);
        toast.success('Terminal saved.');
      }
      setEditing(null);
      onChanged();
    } catch (caught) {
      setFormError(errorMessage(caught));
    } finally {
      setIsSaving(false);
    }
  };

  return (
    <Card flush>
      <CardHeader
        title={`Terminals in ${store.name}`}
        actions={canWrite ? <Button icon="plus" size="sm" onClick={() => open('new')}>Add terminal</Button> : undefined}
      />
      {isLoading ? (
        <LoadingState label="Loading terminals…" />
      ) : terminals.length === 0 ? (
        <EmptyState
          icon="pos"
          title="No terminals in this store"
          body="A terminal is a physical till point — one per checkout lane. Registers, and the cash they hold, belong to a terminal."
          action={canWrite ? { label: 'Add terminal', onClick: () => open('new') } : undefined}
        />
      ) : (
        <Table>
          <Thead>
            <Tr>
              <Th>Terminal</Th>
              <Th>Code</Th>
              <Th>Status</Th>
              {canWrite && <Th className="table__actions">Actions</Th>}
            </Tr>
          </Thead>
          <Tbody>
            {terminals.map((terminal) => (
              <Tr key={terminal.id}>
                <Td className="table__primary">{terminal.name}</Td>
                <Td>
                  <span className="mono">{terminal.code}</span>
                </Td>
                <Td>
                  <ActiveBadge active={terminal.status === 'ACTIVE'} />
                </Td>
                {canWrite && (
                  <Td className="table__actions">
                    <Button variant="secondary" size="sm" onClick={() => open(terminal)}>
                      Edit
                    </Button>
                  </Td>
                )}
              </Tr>
            ))}
          </Tbody>
        </Table>
      )}

      <Modal
        open={editing !== null}
        onClose={() => setEditing(null)}
        title={editing === 'new' ? 'Add terminal' : 'Edit terminal'}
        busy={isSaving}
        footer={
          <>
            <Button variant="secondary" onClick={() => setEditing(null)} disabled={isSaving}>
              Cancel
            </Button>
            <Button onClick={() => void save()} isLoading={isSaving}>
              Save terminal
            </Button>
          </>
        }
      >
        <div className="stack">
          {formError && <p className="field__error">{formError}</p>}
          <Input id="terminal-name" label="Name" required value={name} onChange={(event) => setName(event.target.value)} />
          <Input id="terminal-code" label="Code" required value={code} onChange={(event) => setCode(event.target.value)} />
          <Checkbox id="terminal-active" label="Active" checked={active} onChange={(event) => setActive(event.target.checked)} />
        </div>
      </Modal>
    </Card>
  );
}

function RegistersCard({
  store,
  terminals,
  registers,
  canWrite,
  isLoading,
  onChanged,
}: {
  store: Store;
  terminals: Terminal[];
  registers: Register[];
  canWrite: boolean;
  isLoading: boolean;
  onChanged: () => void;
}) {
  const toast = useToast();
  const [editing, setEditing] = useState<Register | 'new' | null>(null);
  const [terminalId, setTerminalId] = useState('');
  const [code, setCode] = useState('');
  const [name, setName] = useState('');
  const [active, setActive] = useState(true);
  const [isSaving, setIsSaving] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);

  const open = (register: Register | 'new') => {
    setEditing(register);
    setFormError(null);
    setTerminalId(register === 'new' ? (terminals[0]?.id ?? '') : register.terminalId);
    setCode(register === 'new' ? `R${registers.length + 1}` : register.code);
    setName(register === 'new' ? `Register ${registers.length + 1}` : register.name);
    setActive(register === 'new' ? true : register.status === 'ACTIVE');
  };

  const save = async () => {
    if (!terminalId) {
      setFormError('Choose the terminal this register sits at.');
      return;
    }
    if (!code.trim() || !name.trim()) {
      setFormError('A register needs a code and a name.');
      return;
    }
    setIsSaving(true);
    setFormError(null);
    try {
      const body = { terminalId, code: code.trim(), name: name.trim(), status: active ? 'ACTIVE' : 'INACTIVE' };
      if (editing === 'new') {
        await registersApi.create(store.id, body);
        toast.success('Register added. It can now be opened from the Register screen.');
      } else if (editing) {
        await registersApi.update(store.id, editing.id, body);
        toast.success('Register saved.');
      }
      setEditing(null);
      onChanged();
    } catch (caught) {
      setFormError(errorMessage(caught));
    } finally {
      setIsSaving(false);
    }
  };

  return (
    <Card flush>
      <CardHeader
        title={`Registers in ${store.name}`}
        actions={
          canWrite && terminals.length > 0 ? (
            <Button icon="plus" size="sm" onClick={() => open('new')}>
              Add register
            </Button>
          ) : undefined
        }
      />
      {isLoading ? (
        <LoadingState label="Loading registers…" />
      ) : terminals.length === 0 ? (
        <CardBody>
          <Alert tone="info">Add a terminal first — a register has to sit at one.</Alert>
        </CardBody>
      ) : registers.length === 0 ? (
        <EmptyState
          icon="register"
          title="No registers yet"
          body="A register is the cash drawer a cashier opens at the start of a shift. Sales are recorded against it, and its cash is counted when the shift ends."
          action={canWrite ? { label: 'Add register', onClick: () => open('new') } : undefined}
        />
      ) : (
        <Table>
          <Thead>
            <Tr>
              <Th>Register</Th>
              <Th>Code</Th>
              <Th>Terminal</Th>
              <Th>Status</Th>
              {canWrite && <Th className="table__actions">Actions</Th>}
            </Tr>
          </Thead>
          <Tbody>
            {registers.map((register) => (
              <Tr key={register.id}>
                <Td className="table__primary">{register.name}</Td>
                <Td>
                  <span className="mono">{register.code}</span>
                </Td>
                <Td>{terminals.find((terminal) => terminal.id === register.terminalId)?.name ?? '—'}</Td>
                <Td>
                  <ActiveBadge active={register.status === 'ACTIVE'} />
                </Td>
                {canWrite && (
                  <Td className="table__actions">
                    <Button variant="secondary" size="sm" onClick={() => open(register)}>
                      Edit
                    </Button>
                  </Td>
                )}
              </Tr>
            ))}
          </Tbody>
        </Table>
      )}

      <Modal
        open={editing !== null}
        onClose={() => setEditing(null)}
        title={editing === 'new' ? 'Add register' : 'Edit register'}
        busy={isSaving}
        footer={
          <>
            <Button variant="secondary" onClick={() => setEditing(null)} disabled={isSaving}>
              Cancel
            </Button>
            <Button onClick={() => void save()} isLoading={isSaving}>
              Save register
            </Button>
          </>
        }
      >
        <div className="stack">
          {formError && <p className="field__error">{formError}</p>}
          <Input id="register-name" label="Name" required value={name} onChange={(event) => setName(event.target.value)} />
          <Input id="register-code" label="Code" required value={code} onChange={(event) => setCode(event.target.value)} />
          <Select
            id="register-terminal"
            label="Terminal"
            required
            placeholder={null}
            value={terminalId}
            onChange={(event) => setTerminalId(event.target.value)}
            options={terminals.map((terminal) => ({ value: terminal.id, label: terminal.name }))}
          />
          <Checkbox
            id="register-active"
            label="Active"
            hint="Only an active register can be opened for a shift."
            checked={active}
            onChange={(event) => setActive(event.target.checked)}
          />
        </div>
      </Modal>
    </Card>
  );
}

function guessTimezone(): string {
  try {
    return Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC';
  } catch {
    return 'UTC';
  }
}

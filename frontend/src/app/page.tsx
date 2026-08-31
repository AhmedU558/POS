'use client';

import { useCallback, useEffect, useState } from 'react';
import Link from 'next/link';
import { useAuth } from '@/features/auth/AuthContext';
import { useStoreContext } from '@/features/session/StoreContext';
import { SaleSummary, salesApi } from '@/lib/api/sales';
import { StockAlert, inventoryApi } from '@/lib/api/inventory';
import { registerSessionsApi } from '@/lib/api/register-sessions';
import { errorMessage, formatDateTime, formatMoney, formatQuantity, todayRange } from '@/lib/format';
import { P, hasPermission } from '@/lib/permissions';
import { PageHeader } from '@/components/ui/PageHeader';
import { Card, CardBody, CardHeader, Metric } from '@/components/ui/Card';
import { Badge, StatusBadge } from '@/components/ui/Badge';
import { Icon, IconName } from '@/components/ui/Icon';
import { Table, Tbody, Td, Th, Thead, Tr } from '@/components/ui/Table';
import { Alert, EmptyState, LoadingState } from '@/components/ui/States';

/**
 * The dashboard.
 *
 * Answers the questions a store manager actually opens the system with: did we take money today,
 * is a till open, what is running out, and what do I do next. Every figure is real data from an
 * endpoint that works — there are no decorative panels waiting for a backend that does not
 * report yet.
 */
export default function DashboardPage() {
  const { user } = useAuth();
  const { session, activeStore, stores, isLoading: contextLoading } = useStoreContext();

  const canReadSales = hasPermission(user?.permissions, P.SALE_READ);
  const canReadInventory = hasPermission(user?.permissions, P.INVENTORY_READ);
  const canSell = hasPermission(user?.permissions, P.SALE_CREATE);

  const [todaySales, setTodaySales] = useState<SaleSummary[] | null>(null);
  const [alerts, setAlerts] = useState<StockAlert[] | null>(null);
  const [expectedCash, setExpectedCash] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);

  const storeId = activeStore?.id;

  const load = useCallback(async () => {
    setError(null);
    const { from, to } = todayRange();

    const [salesResult, alertResult, summaryResult] = await Promise.allSettled([
      canReadSales
        ? salesApi.search({ status: 'COMPLETED', from, to, size: 100, sort: 'createdAt,desc' })
        : Promise.resolve(null),
      canReadInventory && storeId
        ? inventoryApi.getAlerts({ storeId, status: 'OPEN', size: 5 })
        : Promise.resolve(null),
      session ? registerSessionsApi.summary(session.id) : Promise.resolve(null),
    ]);

    if (salesResult.status === 'fulfilled') {
      setTodaySales(salesResult.value?.content ?? []);
    } else {
      setTodaySales([]);
      setError(errorMessage(salesResult.reason));
    }
    setAlerts(alertResult.status === 'fulfilled' ? (alertResult.value?.content ?? []) : []);
    setExpectedCash(summaryResult.status === 'fulfilled' ? (summaryResult.value?.expectedCash ?? null) : null);
  }, [canReadSales, canReadInventory, storeId, session]);

  useEffect(() => {
    if (!contextLoading) void load();
  }, [contextLoading, load]);

  if (contextLoading) {
    return (
      <div className="page">
        <LoadingState label="Loading your dashboard…" />
      </div>
    );
  }

  // A system with no store cannot do anything else, so that is the only thing worth showing.
  if (stores.length === 0 && hasPermission(user?.permissions, P.STORE_READ)) {
    return (
      <div className="page page-narrow">
        <PageHeader title={`Welcome, ${user?.firstName ?? 'there'}`} />
        <Card>
          <CardBody>
            <EmptyState
              icon="store"
              title="Let's set up your store"
              body="Nothing can be sold, stocked or ordered until there is a store with a till in it. Setup walks you through it in three steps."
              action={{ label: 'Start setup', href: '/setup' }}
            />
          </CardBody>
        </Card>
      </div>
    );
  }

  const taken = (todaySales ?? []).reduce((total, sale) => total + Number(sale.grandTotal ?? 0), 0);

  return (
    <div className="page">
      <PageHeader
        title={`${greeting()}, ${user?.firstName ?? ''}`.trim()}
        description={activeStore ? `Here is how ${activeStore.name} is doing today.` : 'Here is how today is going.'}
        actions={
          canSell && (
            <Link className="btn btn--primary" href={session ? '/pos' : '/register'}>
              {session ? 'Go to the till' : 'Open a register'}
            </Link>
          )
        }
      />

      {error && (
        <div style={{ marginBottom: 'var(--space-4)' }}>
          <Alert tone="error">{error}</Alert>
        </div>
      )}

      <div className="metric-grid" style={{ marginBottom: 'var(--space-6)' }}>
        {canReadSales && (
          <>
            <Metric
              label="Taken today"
              value={todaySales === null ? '…' : formatMoney(taken)}
              meta="Completed sales since midnight"
            />
            <Metric
              label="Transactions"
              value={todaySales === null ? '…' : todaySales.length}
              meta={
                todaySales && todaySales.length > 0
                  ? `Average ${formatMoney(taken / todaySales.length)}`
                  : 'No sales yet today'
              }
            />
          </>
        )}
        <Metric
          label="Till"
          value={session ? 'Open' : 'Closed'}
          meta={
            session ? (
              expectedCash === null ? (
                <Badge variant="success">Ready to sell</Badge>
              ) : (
                `${formatMoney(expectedCash)} expected in the drawer`
              )
            ) : (
              'No register is open'
            )
          }
        />
        {canReadInventory && (
          <Metric
            label="Needs attention"
            value={alerts === null ? '…' : alerts.length}
            meta={alerts && alerts.length > 0 ? <Badge variant="warning">Low or expiring stock</Badge> : 'Nothing running low'}
          />
        )}
      </div>

      <div className="stack-lg stack">
        <QuickActions permissions={user?.permissions} hasSession={session !== null} />

        {canReadInventory && alerts && alerts.length > 0 && (
          <Card flush>
            <CardHeader
              title="Running low or expiring"
              actions={
                <Link className="btn btn--ghost btn--sm" href="/inventory/alerts">
                  All alerts
                </Link>
              }
            />
            <Table>
              <Thead>
                <Tr>
                  <Th>Product</Th>
                  <Th className="table__num">On hand</Th>
                  <Th>What to do</Th>
                </Tr>
              </Thead>
              <Tbody>
                {alerts.map((alert) => (
                  <Tr key={alert.id}>
                    <Td>
                      <Link href={`/products/${alert.productId}`} className="table__primary">
                        {alert.productName}
                      </Link>
                      <div className="table__secondary">
                        {alert.alertType === 'LOW_STOCK' ? 'Below re-order level' : 'Approaching expiry'}
                      </div>
                    </Td>
                    <Td className="table__num">{formatQuantity(alert.quantity)}</Td>
                    <Td>{alert.suggestedAction}</Td>
                  </Tr>
                ))}
              </Tbody>
            </Table>
          </Card>
        )}

        {canReadSales && (
          <Card flush>
            <CardHeader
              title="Recent sales"
              actions={
                <Link className="btn btn--ghost btn--sm" href="/sales">
                  All sales
                </Link>
              }
            />
            {todaySales === null ? (
              <LoadingState label="Loading sales…" />
            ) : todaySales.length === 0 ? (
              <EmptyState
                icon="pos"
                title="No sales yet today"
                body={
                  session
                    ? 'Your till is open and ready. Sales will appear here as they are rung up.'
                    : 'Open a register to start taking payments.'
                }
                action={canSell ? { label: session ? 'Go to the till' : 'Open a register', href: session ? '/pos' : '/register' } : undefined}
              />
            ) : (
              <Table>
                <Thead>
                  <Tr>
                    <Th>Receipt</Th>
                    <Th>When</Th>
                    <Th>Cashier</Th>
                    <Th>Status</Th>
                    <Th className="table__num">Total</Th>
                  </Tr>
                </Thead>
                <Tbody>
                  {todaySales.slice(0, 8).map((sale) => (
                    <Tr key={sale.id}>
                      <Td>
                        <span className="mono">{sale.receiptNumber}</span>
                      </Td>
                      <Td>{formatDateTime(sale.createdAt)}</Td>
                      <Td>{sale.cashierName ?? <span className="text-muted">—</span>}</Td>
                      <Td>
                        <StatusBadge kind="sale" status={sale.status} />
                      </Td>
                      <Td className="table__num">
                        <span className="money">{formatMoney(sale.grandTotal)}</span>
                      </Td>
                    </Tr>
                  ))}
                </Tbody>
              </Table>
            )}
          </Card>
        )}
      </div>
    </div>
  );
}

interface Action {
  label: string;
  hint: string;
  href: string;
  icon: IconName;
  permission: string;
}

function QuickActions({ permissions, hasSession }: { permissions: string[] | undefined; hasSession: boolean }) {
  const actions: Action[] = [
    {
      label: hasSession ? 'Sell' : 'Open the register',
      hint: hasSession ? 'Scan items and take payment' : 'Start a shift before selling',
      href: hasSession ? '/pos' : '/register',
      icon: hasSession ? 'pos' : 'register',
      permission: P.SALE_CREATE,
    },
    { label: 'Add a product', hint: 'Put something new on sale', href: '/products/new', icon: 'products', permission: P.PRODUCT_WRITE },
    { label: 'Receive stock', hint: 'Record a delivery', href: '/inventory/receive', icon: 'inventory', permission: P.INVENTORY_RECEIVE },
    { label: 'Order from a supplier', hint: 'Raise a purchase order', href: '/purchase-orders/new', icon: 'purchases', permission: P.PURCHASE_WRITE },
    { label: 'Add a customer', hint: 'Track their purchases and credit', href: '/customers/new', icon: 'customers', permission: P.CUSTOMER_WRITE },
    { label: 'Record a bill', hint: 'A supplier invoice to pay', href: '/accounts-payable/new', icon: 'payables', permission: P.AP_WRITE },
  ];

  const visible = actions.filter((action) => hasPermission(permissions, action.permission));
  if (visible.length === 0) {
    return null;
  }

  return (
    <Card>
      <CardHeader title="Quick actions" />
      <CardBody>
        <div className="metric-grid">
          {visible.map((action) => (
            <Link key={action.href} className="quick-action" href={action.href}>
              <span className="quick-action__icon">
                <Icon name={action.icon} size={20} />
              </span>
              <span>
                <span className="quick-action__title">{action.label}</span>
                <span className="quick-action__hint" style={{ display: 'block' }}>
                  {action.hint}
                </span>
              </span>
            </Link>
          ))}
        </div>
      </CardBody>
    </Card>
  );
}

function greeting(): string {
  const hour = new Date().getHours();
  if (hour < 12) return 'Good morning';
  if (hour < 18) return 'Good afternoon';
  return 'Good evening';
}

'use client';

import { useCallback, useEffect, useState } from 'react';
import Link from 'next/link';
import { useAuth } from '@/features/auth/AuthContext';
import { useStoreContext } from '@/features/session/StoreContext';
import { InventoryBatch, InventoryReportRow, InventoryTransaction, inventoryApi } from '@/lib/api/inventory';
import { SaleSummary, salesApi } from '@/lib/api/sales';
import { Page, emptyPage } from '@/lib/api/http';
import { errorMessage, formatDate, formatDateTime, formatMoney, formatQuantity } from '@/lib/format';
import { P, hasAnyPermission, hasPermission } from '@/lib/permissions';
import { PageHeader } from '@/components/ui/PageHeader';
import { Card, CardHeader, Metric } from '@/components/ui/Card';
import { Checkbox, Input, Select } from '@/components/ui/Field';
import { StatusBadge } from '@/components/ui/Badge';
import { Table, Tbody, Td, Th, Thead, Tr } from '@/components/ui/Table';
import { Alert, EmptyState, ErrorState, LoadingState, PermissionRequired } from '@/components/ui/States';

type ReportKey = 'sales' | 'stock' | 'movements' | 'expiry';

/**
 * Reports.
 *
 * Only reports backed by a working endpoint appear here. The finance report endpoints
 * (`/reports/sales`, `/reports/profit-loss`, `/reports/cash-flow`, `/reports/payables`,
 * `/reports/cash-registers`, and the sales breakdowns) are stubs that return an empty list, so
 * putting a screen in front of them would show an empty table and call it a report. The sales
 * report below is built from the sales history endpoint, which holds real data.
 */
export default function ReportsPage() {
  const { user } = useAuth();
  const { activeStoreId, activeStore } = useStoreContext();

  const canSales = hasPermission(user?.permissions, P.SALE_READ);
  const canInventory = hasPermission(user?.permissions, P.REPORT_INVENTORY);
  const canAny = hasAnyPermission(user?.permissions, [P.SALE_READ, P.REPORT_INVENTORY]);

  const [report, setReport] = useState<ReportKey>(canSales ? 'sales' : 'stock');

  if (!canAny) {
    return (
      <div className="page">
        <PermissionRequired permission={P.REPORT_INVENTORY} action="Viewing reports" />
      </div>
    );
  }

  const options = [
    ...(canSales ? [{ value: 'sales', label: 'Sales' }] : []),
    ...(canInventory
      ? [
          { value: 'stock', label: 'Stock on hand' },
          { value: 'movements', label: 'Stock movements' },
          { value: 'expiry', label: 'Expiring stock' },
        ]
      : []),
  ];

  return (
    <div className="page">
      <PageHeader
        title="Reports"
        description={`What has been happening in ${activeStore?.name ?? 'your store'}.`}
      />

      <div className="toolbar">
        <Select
          id="report-kind"
          label="Report"
          placeholder={null}
          value={report}
          onChange={(event) => setReport(event.target.value as ReportKey)}
          options={options}
          fieldClassName="toolbar__filter"
        />
      </div>

      {report === 'sales' && <SalesReport />}
      {report !== 'sales' && !activeStoreId && (
        <Card>
          <EmptyState
            icon="store"
            title="No store selected"
            body="Inventory reports are per store."
            action={{ label: 'Go to setup', href: '/setup' }}
          />
        </Card>
      )}
      {report === 'stock' && activeStoreId && <StockReport storeId={activeStoreId} />}
      {report === 'movements' && activeStoreId && <MovementsReport storeId={activeStoreId} />}
      {report === 'expiry' && activeStoreId && <ExpiryReport storeId={activeStoreId} />}
    </div>
  );
}

/**
 * Takings over a date range.
 *
 * Every figure comes from a sale the server priced and settled; this view adds them up for the
 * chosen window. It does not price anything itself.
 */
function SalesReport() {
  const [from, setFrom] = useState(daysAgo(7));
  const [to, setTo] = useState(today());
  const [sales, setSales] = useState<Page<SaleSummary>>(emptyPage(200));
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setIsLoading(true);
    setError(null);
    try {
      setSales(
        await salesApi.search({
          status: 'COMPLETED',
          from: new Date(`${from}T00:00:00`).toISOString(),
          to: new Date(`${to}T23:59:59.999`).toISOString(),
          size: 200,
          sort: 'createdAt,desc',
        })
      );
    } catch (caught) {
      setError(errorMessage(caught));
    } finally {
      setIsLoading(false);
    }
  }, [from, to]);

  useEffect(() => {
    void load();
  }, [load]);

  const taken = sales.content.reduce((total, sale) => total + Number(sale.grandTotal ?? 0), 0);
  const average = sales.content.length > 0 ? taken / sales.content.length : 0;
  const truncated = sales.totalElements > sales.content.length;

  return (
    <div className="stack-lg stack">
      <div className="toolbar">
        <Input
          id="sales-report-from"
          label="From"
          type="date"
          value={from}
          onChange={(event) => setFrom(event.target.value)}
          fieldClassName="toolbar__filter"
        />
        <Input
          id="sales-report-to"
          label="To"
          type="date"
          value={to}
          onChange={(event) => setTo(event.target.value)}
          fieldClassName="toolbar__filter"
        />
      </div>

      {error ? (
        <ErrorState message={error} onRetry={() => void load()} />
      ) : isLoading ? (
        <LoadingState label="Adding up sales…" />
      ) : (
        <>
          <div className="metric-grid">
            <Metric label="Taken" value={formatMoney(taken)} meta={`${formatDate(from)} to ${formatDate(to)}`} />
            <Metric label="Sales" value={sales.content.length} meta="Completed transactions" />
            <Metric label="Average sale" value={formatMoney(average)} />
          </div>

          {truncated && (
            <Alert tone="warning">
              This period has {sales.totalElements} sales and only the most recent 200 are included in the totals above.
              Narrow the date range for an exact figure.
            </Alert>
          )}

          <Card flush>
            <CardHeader title="Sales in this period" actions={<Link className="btn btn--ghost btn--sm" href="/sales">Open sales history</Link>} />
            {sales.content.length === 0 ? (
              <EmptyState icon="reports" title="No completed sales in this period" body="Try a wider date range." />
            ) : (
              <Table>
                <Thead>
                  <Tr>
                    <Th>Receipt</Th>
                    <Th>When</Th>
                    <Th>Cashier</Th>
                    <Th>Customer</Th>
                    <Th className="table__num">Total</Th>
                  </Tr>
                </Thead>
                <Tbody>
                  {sales.content.slice(0, 50).map((sale) => (
                    <Tr key={sale.id}>
                      <Td>
                        <span className="mono">{sale.receiptNumber}</span>
                      </Td>
                      <Td>{formatDateTime(sale.createdAt)}</Td>
                      <Td>{sale.cashierName ?? <span className="text-muted">—</span>}</Td>
                      <Td>{sale.customerName ?? <span className="text-muted">Walk-in</span>}</Td>
                      <Td className="table__num">
                        <span className="money">{formatMoney(sale.grandTotal)}</span>
                      </Td>
                    </Tr>
                  ))}
                </Tbody>
              </Table>
            )}
          </Card>
        </>
      )}
    </div>
  );
}

function StockReport({ storeId }: { storeId: string }) {
  const [lowOnly, setLowOnly] = useState(false);
  const [rows, setRows] = useState<InventoryReportRow[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setError(null);
    try {
      const page = await inventoryApi.getInventoryReport({ storeId, size: 100, lowStockOnly: lowOnly });
      setRows(page.content);
    } catch (caught) {
      setError(errorMessage(caught));
      setRows([]);
    }
  }, [storeId, lowOnly]);

  useEffect(() => {
    void load();
  }, [load]);

  if (error) return <ErrorState message={error} onRetry={() => void load()} />;
  if (rows === null) return <LoadingState label="Loading stock report…" />;

  return (
    <>
      <div className="toolbar">
        <Checkbox
          id="stock-report-low"
          label="Low stock only"
          checked={lowOnly}
          onChange={(event) => setLowOnly(event.target.checked)}
        />
      </div>
      <Card flush>
        {rows.length === 0 ? (
          <EmptyState icon="inventory" title={lowOnly ? 'Nothing is running low' : 'No stock recorded'} />
        ) : (
          <Table>
            <Thead>
              <Tr>
                <Th>Product</Th>
                <Th>SKU</Th>
                <Th className="table__num">On hand</Th>
                <Th className="table__num">Re-order at</Th>
                <Th>Below minimum</Th>
              </Tr>
            </Thead>
            <Tbody>
              {rows.map((row) => (
                <Tr key={row.productId}>
                  <Td className="table__primary">{row.productName}</Td>
                  <Td>
                    <span className="mono">{row.sku}</span>
                  </Td>
                  <Td className="table__num">{formatQuantity(row.quantity)}</Td>
                  <Td className="table__num">{formatQuantity(row.minStock)}</Td>
                  <Td>{row.belowMinimum ? 'Yes' : 'No'}</Td>
                </Tr>
              ))}
            </Tbody>
          </Table>
        )}
      </Card>
    </>
  );
}

function MovementsReport({ storeId }: { storeId: string }) {
  const [rows, setRows] = useState<InventoryTransaction[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setError(null);
    try {
      const page = await inventoryApi.getMovementReport({ storeId, size: 100 });
      setRows(page.content);
    } catch (caught) {
      setError(errorMessage(caught));
      setRows([]);
    }
  }, [storeId]);

  useEffect(() => {
    void load();
  }, [load]);

  if (error) return <ErrorState message={error} onRetry={() => void load()} />;
  if (rows === null) return <LoadingState label="Loading movements…" />;

  return (
    <Card flush>
      {rows.length === 0 ? (
        <EmptyState icon="inventory" title="No stock movements yet" body="Receiving, adjusting and selling all leave a record here." />
      ) : (
        <Table>
          <Thead>
            <Tr>
              <Th>When</Th>
              <Th>Product</Th>
              <Th>Type</Th>
              <Th className="table__num">Quantity</Th>
              <Th>Reason</Th>
              <Th>By</Th>
            </Tr>
          </Thead>
          <Tbody>
            {rows.map((row) => (
              <Tr key={row.id}>
                <Td>{formatDateTime(row.createdAt)}</Td>
                <Td className="table__primary">{row.productName}</Td>
                <Td>{humanise(row.transactionType)}</Td>
                <Td className="table__num">{formatQuantity(row.quantity)}</Td>
                <Td>{row.reason || <span className="text-muted">—</span>}</Td>
                <Td>{row.createdByUsername || <span className="text-muted">—</span>}</Td>
              </Tr>
            ))}
          </Tbody>
        </Table>
      )}
    </Card>
  );
}

function ExpiryReport({ storeId }: { storeId: string }) {
  const [days, setDays] = useState(30);
  const [rows, setRows] = useState<InventoryBatch[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setError(null);
    try {
      const page = await inventoryApi.getExpiryReport({ storeId, size: 100, days });
      setRows(page.content);
    } catch (caught) {
      setError(errorMessage(caught));
      setRows([]);
    }
  }, [storeId, days]);

  useEffect(() => {
    void load();
  }, [load]);

  if (error) return <ErrorState message={error} onRetry={() => void load()} />;

  return (
    <>
      <div className="toolbar">
        <Select
          id="expiry-report-days"
          label="Within"
          placeholder={null}
          value={String(days)}
          onChange={(event) => setDays(Number(event.target.value))}
          options={[
            { value: '7', label: '7 days' },
            { value: '30', label: '30 days' },
            { value: '90', label: '90 days' },
          ]}
          fieldClassName="toolbar__filter"
        />
      </div>
      <Card flush>
        {rows === null ? (
          <LoadingState label="Loading expiry report…" />
        ) : rows.length === 0 ? (
          <EmptyState icon="clock" title="Nothing expiring in that window" />
        ) : (
          <Table>
            <Thead>
              <Tr>
                <Th>Product</Th>
                <Th>Batch</Th>
                <Th className="table__num">Quantity</Th>
                <Th>Expires</Th>
                <Th>Status</Th>
              </Tr>
            </Thead>
            <Tbody>
              {rows.map((row) => (
                <Tr key={row.id}>
                  <Td className="table__primary">{row.productName}</Td>
                  <Td>
                    <span className="mono">{row.batchNumber}</span>
                  </Td>
                  <Td className="table__num">{formatQuantity(row.quantity)}</Td>
                  <Td>{formatDate(row.expirationDate)}</Td>
                  <Td>
                    <StatusBadge kind="batch" status={row.status} />
                  </Td>
                </Tr>
              ))}
            </Tbody>
          </Table>
        )}
      </Card>
    </>
  );
}

function humanise(value: string): string {
  return value.charAt(0) + value.slice(1).toLowerCase().replace(/_/g, ' ');
}

function today(): string {
  return new Date().toISOString().slice(0, 10);
}

function daysAgo(days: number): string {
  const date = new Date();
  date.setDate(date.getDate() - days);
  return date.toISOString().slice(0, 10);
}

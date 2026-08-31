'use client';

import { useCallback, useEffect, useState } from 'react';
import Link from 'next/link';
import { useAuth } from '@/features/auth/AuthContext';
import { useStoreContext } from '@/features/session/StoreContext';
import { StockAlert, inventoryApi } from '@/lib/api/inventory';
import { Page, emptyPage } from '@/lib/api/http';
import { errorMessage, formatDate, formatQuantity } from '@/lib/format';
import { P, hasPermission } from '@/lib/permissions';
import { PageHeader } from '@/components/ui/PageHeader';
import { Card } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Select } from '@/components/ui/Field';
import { Badge, StatusBadge } from '@/components/ui/Badge';
import { Pagination, Table, Tbody, Td, Th, Thead, Tr } from '@/components/ui/Table';
import { EmptyState, ErrorState, PermissionRequired, TableSkeleton } from '@/components/ui/States';
import { useToast } from '@/components/ui/Toast';
import { InventoryTabs } from '@/features/inventory/InventoryTabs';

const PAGE_SIZE = 20;

/** Low stock and approaching expiry, with the action the system suggests for each. */
export default function StockAlertsPage() {
  const { user } = useAuth();
  const { activeStoreId, activeStore } = useStoreContext();
  const toast = useToast();
  const canRead = hasPermission(user?.permissions, P.INVENTORY_READ);

  const [alertType, setAlertType] = useState('');
  const [status, setStatus] = useState('OPEN');
  const [page, setPage] = useState(0);
  const [alerts, setAlerts] = useState<Page<StockAlert>>(emptyPage(PAGE_SIZE));
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [busyId, setBusyId] = useState<string | null>(null);

  const load = useCallback(async () => {
    if (!activeStoreId) {
      setIsLoading(false);
      return;
    }
    setIsLoading(true);
    setError(null);
    try {
      setAlerts(
        await inventoryApi.getAlerts({
          storeId: activeStoreId,
          page,
          size: PAGE_SIZE,
          alertType: alertType || undefined,
          status: status || undefined,
        })
      );
    } catch (caught) {
      setError(errorMessage(caught));
    } finally {
      setIsLoading(false);
    }
  }, [activeStoreId, alertType, status, page]);

  useEffect(() => {
    if (canRead) void load();
  }, [canRead, load]);

  useEffect(() => {
    setPage(0);
  }, [alertType, status]);

  if (!canRead) {
    return (
      <div className="page">
        <PermissionRequired permission={P.INVENTORY_READ} action="Viewing stock alerts" />
      </div>
    );
  }

  const acknowledge = async (alert: StockAlert) => {
    setBusyId(alert.id);
    try {
      await inventoryApi.acknowledgeAlert(alert.id);
      toast.success(`Alert for ${alert.productName} acknowledged.`);
      await load();
    } catch (caught) {
      toast.error(errorMessage(caught));
    } finally {
      setBusyId(null);
    }
  };

  return (
    <div className="page">
      <PageHeader
        title="Stock alerts"
        breadcrumbs={[{ label: 'Inventory', href: '/inventory' }, { label: 'Alerts' }]}
        description={`What needs attention in ${activeStore?.name ?? 'this store'} — products running low and batches nearing their expiry date.`}
      />

      <InventoryTabs active="alerts" permissions={user?.permissions} />

      <div className="toolbar">
        <Select
          id="alert-type"
          label="Type"
          placeholder="All alerts"
          value={alertType}
          onChange={(event) => setAlertType(event.target.value)}
          options={[
            { value: 'LOW_STOCK', label: 'Low stock' },
            { value: 'EXPIRY', label: 'Expiry' },
          ]}
          fieldClassName="toolbar__filter"
        />
        <Select
          id="alert-status"
          label="Status"
          placeholder="All"
          value={status}
          onChange={(event) => setStatus(event.target.value)}
          options={[
            { value: 'OPEN', label: 'Needs action' },
            { value: 'ACKNOWLEDGED', label: 'Acknowledged' },
          ]}
          fieldClassName="toolbar__filter"
        />
      </div>

      <Card flush>
        {error ? (
          <ErrorState message={error} onRetry={() => void load()} />
        ) : isLoading && alerts.content.length === 0 ? (
          <TableSkeleton rows={5} columns={5} />
        ) : alerts.content.length === 0 ? (
          <EmptyState
            icon="check-circle"
            title={status === 'OPEN' ? 'Nothing needs attention' : 'No alerts here'}
            body={
              status === 'OPEN'
                ? 'No product is below its re-order level and no batch is close to expiring.'
                : 'Try a different status or alert type.'
            }
          />
        ) : (
          <>
            <Table>
              <Thead>
                <Tr>
                  <Th>Product</Th>
                  <Th>Alert</Th>
                  <Th className="table__num">On hand</Th>
                  <Th>Suggested action</Th>
                  <Th>Status</Th>
                  <Th className="table__actions">Actions</Th>
                </Tr>
              </Thead>
              <Tbody>
                {alerts.content.map((alert) => (
                  <Tr key={alert.id}>
                    <Td>
                      <Link href={`/products/${alert.productId}`} className="table__primary">
                        {alert.productName}
                      </Link>
                      <div className="table__secondary mono">{alert.sku}</div>
                    </Td>
                    <Td>
                      {alert.alertType === 'LOW_STOCK' ? (
                        <Badge variant="warning">Low stock</Badge>
                      ) : (
                        <Badge variant="error">Expiry</Badge>
                      )}
                      {alert.alertType === 'EXPIRY' && alert.expirationDate && (
                        <div className="table__secondary">
                          {alert.batchNumber ? `Batch ${alert.batchNumber} · ` : ''}
                          {formatDate(alert.expirationDate)}
                        </div>
                      )}
                    </Td>
                    <Td className="table__num">
                      {formatQuantity(alert.quantity)}
                      {alert.minimumLevel !== null && (
                        <div className="table__secondary">min {formatQuantity(alert.minimumLevel)}</div>
                      )}
                    </Td>
                    <Td>{alert.suggestedAction}</Td>
                    <Td>
                      <StatusBadge kind="alert" status={alert.status} />
                    </Td>
                    <Td className="table__actions">
                      {alert.status === 'OPEN' && (
                        <Button
                          variant="secondary"
                          size="sm"
                          isLoading={busyId === alert.id}
                          onClick={() => void acknowledge(alert)}
                        >
                          Acknowledge
                        </Button>
                      )}
                    </Td>
                  </Tr>
                ))}
              </Tbody>
            </Table>
            <Pagination
              page={alerts.number}
              totalPages={alerts.totalPages}
              totalElements={alerts.totalElements}
              pageSize={alerts.size || PAGE_SIZE}
              onPageChange={setPage}
              isLoading={isLoading}
            />
          </>
        )}
      </Card>
    </div>
  );
}

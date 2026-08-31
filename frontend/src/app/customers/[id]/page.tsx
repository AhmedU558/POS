'use client';

import { useCallback, useEffect, useState } from 'react';
import Link from 'next/link';
import { useParams, useRouter } from 'next/navigation';
import { useAuth } from '@/features/auth/AuthContext';
import { Customer, CustomerCredit, customersApi } from '@/lib/api/customers';
import { Page } from '@/lib/api/http';
import { errorMessage, formatDateTime, formatMoney } from '@/lib/format';
import { P, hasPermission } from '@/lib/permissions';
import { PageHeader } from '@/components/ui/PageHeader';
import { Card, CardBody, CardHeader, Metric } from '@/components/ui/Card';
import { ActiveBadge, StatusBadge } from '@/components/ui/Badge';
import { Table, Tbody, Td, Th, Thead, Tr } from '@/components/ui/Table';
import { EmptyState, ErrorState, LoadingState, PermissionRequired } from '@/components/ui/States';
import { useToast } from '@/components/ui/Toast';
import {
  CustomerForm,
  CustomerFormErrors,
  CustomerFormValues,
  customerFormToRequest,
  validateCustomerForm,
} from '@/features/customers/CustomerForm';

type SaleRow = { id: string; receiptNumber: string; status: string; grandTotal: number; createdAt: string };

export default function CustomerDetailPage() {
  const { id } = useParams<{ id: string }>();
  const router = useRouter();
  const toast = useToast();
  const { user } = useAuth();

  const canRead = hasPermission(user?.permissions, P.CUSTOMER_READ);
  const canWrite = hasPermission(user?.permissions, P.CUSTOMER_WRITE);
  const canReadCredit = hasPermission(user?.permissions, P.CREDIT_READ);

  const [customer, setCustomer] = useState<Customer | null>(null);
  const [credit, setCredit] = useState<CustomerCredit | null>(null);
  const [sales, setSales] = useState<Page<SaleRow> | null>(null);
  const [values, setValues] = useState<CustomerFormValues | null>(null);
  const [errors, setErrors] = useState<CustomerFormErrors>({});
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  const load = useCallback(async () => {
    setIsLoading(true);
    setLoadError(null);
    try {
      const loaded = await customersApi.get(id);
      setCustomer(loaded);
      setValues({
        customerCode: loaded.customerCode,
        name: loaded.name,
        phone: loaded.phone ?? '',
        email: loaded.email ?? '',
        address: loaded.address ?? '',
        creditLimit: String(loaded.creditLimit ?? 0),
        isActive: loaded.active,
      });
    } catch (caught) {
      setLoadError(errorMessage(caught));
    } finally {
      setIsLoading(false);
    }
  }, [id]);

  useEffect(() => {
    if (canRead) void load();
  }, [canRead, load]);

  useEffect(() => {
    if (!canRead) return;
    customersApi
      .listSales(id, 0, 10)
      .then(setSales)
      .catch(() => setSales(null));
    if (canReadCredit) {
      customersApi
        .getCredit(id, 0, 5)
        .then(setCredit)
        .catch(() => setCredit(null));
    }
  }, [canRead, canReadCredit, id]);

  if (!canRead) {
    return (
      <div className="page">
        <PermissionRequired permission={P.CUSTOMER_READ} action="Viewing customers" />
      </div>
    );
  }

  if (isLoading) {
    return (
      <div className="page">
        <LoadingState label="Loading customer…" />
      </div>
    );
  }

  if (loadError || !customer || !values) {
    return (
      <div className="page">
        <ErrorState message={loadError ?? 'Customer not found.'} onRetry={() => void load()} />
      </div>
    );
  }

  const save = async () => {
    const found = validateCustomerForm(values);
    setErrors(found);
    if (Object.keys(found).length > 0) {
      setSubmitError('Check the highlighted fields and try again.');
      return;
    }
    setIsSubmitting(true);
    setSubmitError(null);
    try {
      const saved = await customersApi.update(id, customerFormToRequest(values));
      setCustomer(saved);
      toast.success('Customer saved.');
    } catch (caught) {
      setSubmitError(errorMessage(caught));
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="page">
      <PageHeader
        title={customer.name}
        breadcrumbs={[{ label: 'Customers', href: '/customers' }, { label: customer.name }]}
        description={`Customer code ${customer.customerCode}`}
        actions={
          <>
            <ActiveBadge active={customer.active} />
            {canReadCredit && (
              <Link className="btn btn--secondary" href={`/customers/${id}/credit`}>
                Store credit
              </Link>
            )}
          </>
        }
      />

      {canReadCredit && credit && (
        <div className="metric-grid" style={{ marginBottom: 'var(--space-6)' }}>
          <Metric label="Credit limit" value={formatMoney(credit.creditLimit)} />
          <Metric
            label="Currently owed"
            value={formatMoney(credit.balance)}
            meta={
              credit.balance >= credit.creditLimit && credit.creditLimit > 0
                ? 'At their limit'
                : `${formatMoney(credit.creditLimit - credit.balance)} available`
            }
          />
        </div>
      )}

      <div className="stack-lg stack">
        <CustomerForm
          values={values}
          errors={errors}
          submitError={submitError}
          isSubmitting={isSubmitting}
          submitLabel="Save changes"
          onChange={(field, value) => {
            setValues((current) => (current ? { ...current, [field]: value } : current));
            setErrors((current) => ({ ...current, [field]: undefined }));
          }}
          onSubmit={() => void (canWrite ? save() : undefined)}
          onCancel={() => router.push('/customers')}
        />

        <Card flush>
          <CardHeader
            title="Recent purchases"
            actions={
              <Link className="btn btn--ghost btn--sm" href="/sales">
                All sales
              </Link>
            }
          />
          {sales === null || sales.content.length === 0 ? (
            <CardBody>
              <EmptyState
                icon="reports"
                title="No purchases yet"
                body="Sales appear here once this customer is added to one at the till."
              />
            </CardBody>
          ) : (
            <Table>
              <Thead>
                <Tr>
                  <Th>Receipt</Th>
                  <Th>When</Th>
                  <Th>Status</Th>
                  <Th className="table__num">Total</Th>
                </Tr>
              </Thead>
              <Tbody>
                {sales.content.map((sale) => (
                  <Tr key={sale.id}>
                    <Td>
                      <span className="mono">{sale.receiptNumber}</span>
                    </Td>
                    <Td>{formatDateTime(sale.createdAt)}</Td>
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
      </div>
    </div>
  );
}

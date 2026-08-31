'use client';

import { useCallback, useEffect, useState } from 'react';
import Link from 'next/link';
import { useParams, useRouter } from 'next/navigation';
import { useAuth } from '@/features/auth/AuthContext';
import { Supplier, SupplierProduct, suppliersApi } from '@/lib/api/suppliers';
import { Product } from '@/types/catalog';
import { errorMessage } from '@/lib/format';
import { P, hasPermission } from '@/lib/permissions';
import { PageHeader } from '@/components/ui/PageHeader';
import { Card, CardBody, CardHeader } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { ActiveBadge } from '@/components/ui/Badge';
import { Table, Tbody, Td, Th, Thead, Tr } from '@/components/ui/Table';
import { ConfirmDialog } from '@/components/ui/Modal';
import { EmptyState, ErrorState, LoadingState, PermissionRequired } from '@/components/ui/States';
import { useToast } from '@/components/ui/Toast';
import { ProductPicker } from '@/features/products/ProductPicker';
import {
  SupplierForm,
  SupplierFormErrors,
  SupplierFormValues,
  supplierFormToRequest,
  validateSupplierForm,
} from '@/features/suppliers/SupplierForm';

type Tab = 'details' | 'products';

export default function SupplierDetailPage() {
  const { id } = useParams<{ id: string }>();
  const router = useRouter();
  const toast = useToast();
  const { user } = useAuth();

  const canRead = hasPermission(user?.permissions, P.SUPPLIER_READ);
  const canWrite = hasPermission(user?.permissions, P.SUPPLIER_WRITE);
  const canReadStatement = hasPermission(user?.permissions, P.AP_READ);

  const [tab, setTab] = useState<Tab>('details');
  const [supplier, setSupplier] = useState<Supplier | null>(null);
  const [products, setProducts] = useState<SupplierProduct[]>([]);
  const [values, setValues] = useState<SupplierFormValues | null>(null);
  const [errors, setErrors] = useState<SupplierFormErrors>({});
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [isLoading, setIsLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);

  const [toAdd, setToAdd] = useState<Product | null>(null);
  const [toRemove, setToRemove] = useState<SupplierProduct | null>(null);

  const load = useCallback(async () => {
    setIsLoading(true);
    setLoadError(null);
    try {
      const [loaded, loadedProducts] = await Promise.all([
        suppliersApi.get(id),
        suppliersApi.listProducts(id).catch(() => [] as SupplierProduct[]),
      ]);
      setSupplier(loaded);
      setProducts(loadedProducts);
      setValues({
        supplierCode: loaded.supplierCode,
        name: loaded.name,
        phone: loaded.phone ?? '',
        email: loaded.email ?? '',
        address: loaded.address ?? '',
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

  if (!canRead) {
    return (
      <div className="page">
        <PermissionRequired permission={P.SUPPLIER_READ} action="Viewing suppliers" />
      </div>
    );
  }

  if (isLoading) {
    return (
      <div className="page">
        <LoadingState label="Loading supplier…" />
      </div>
    );
  }

  if (loadError || !supplier || !values) {
    return (
      <div className="page">
        <ErrorState message={loadError ?? 'Supplier not found.'} onRetry={() => void load()} />
      </div>
    );
  }

  const save = async () => {
    const found = validateSupplierForm(values);
    setErrors(found);
    if (Object.keys(found).length > 0) {
      setSubmitError('Check the highlighted fields and try again.');
      return;
    }
    setIsSubmitting(true);
    setSubmitError(null);
    try {
      setSupplier(await suppliersApi.update(id, supplierFormToRequest(values)));
      toast.success('Supplier saved.');
    } catch (caught) {
      setSubmitError(errorMessage(caught));
    } finally {
      setIsSubmitting(false);
    }
  };

  /*
   * The API replaces the whole catalogue in one PUT, so adding or removing one product means
   * sending the full list. The screen keeps that detail to itself.
   */
  const replaceProducts = async (productIds: string[], message: string) => {
    setIsSubmitting(true);
    try {
      setProducts(await suppliersApi.replaceProducts(id, productIds));
      toast.success(message);
    } catch (caught) {
      toast.error(errorMessage(caught));
    } finally {
      setIsSubmitting(false);
      setToAdd(null);
      setToRemove(null);
    }
  };

  return (
    <div className="page">
      <PageHeader
        title={supplier.name}
        breadcrumbs={[{ label: 'Suppliers', href: '/suppliers' }, { label: supplier.name }]}
        description={`Supplier code ${supplier.supplierCode}`}
        actions={
          <>
            <ActiveBadge active={supplier.active} />
            {canReadStatement && (
              <Link className="btn btn--secondary" href={`/suppliers/${id}/statement`}>
                Statement
              </Link>
            )}
            {hasPermission(user?.permissions, P.PURCHASE_WRITE) && (
              <Link className="btn btn--primary" href={`/purchase-orders/new?supplierId=${id}`}>
                New purchase order
              </Link>
            )}
          </>
        }
      />

      <div className="tabs" role="tablist">
        <button type="button" role="tab" className="tab" aria-selected={tab === 'details'} onClick={() => setTab('details')}>
          Details
        </button>
        <button type="button" role="tab" className="tab" aria-selected={tab === 'products'} onClick={() => setTab('products')}>
          Products they supply ({products.length})
        </button>
      </div>

      {tab === 'details' ? (
        <SupplierForm
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
          onCancel={() => router.push('/suppliers')}
        />
      ) : (
        <Card flush>
          <CardHeader title="Products they supply" />
          {canWrite && (
            <CardBody>
              <div className="row row-wrap">
                <div className="grow">
                  <ProductPicker
                    id="supplier-add-product"
                    label="Add a product"
                    hint="Records that this supplier can provide the product. It does not change stock or pricing."
                    selected={toAdd}
                    onSelect={setToAdd}
                  />
                </div>
                <Button
                  disabled={!toAdd || products.some((existing) => existing.productId === toAdd.id)}
                  isLoading={isSubmitting}
                  onClick={() => {
                    if (!toAdd) return;
                    void replaceProducts(
                      [...products.map((existing) => existing.productId), toAdd.id],
                      `${toAdd.name} added to ${supplier.name}.`
                    );
                  }}
                >
                  Add
                </Button>
              </div>
            </CardBody>
          )}
          {products.length === 0 ? (
            <EmptyState
              icon="products"
              title="No products linked yet"
              body="Linking products records who supplies what. It is optional — a purchase order can include any product."
            />
          ) : (
            <Table>
              <Thead>
                <Tr>
                  <Th>Product</Th>
                  <Th>SKU</Th>
                  <Th>Status</Th>
                  {canWrite && <Th className="table__actions">Actions</Th>}
                </Tr>
              </Thead>
              <Tbody>
                {products.map((product) => (
                  <Tr key={product.id}>
                    <Td>
                      <Link href={`/products/${product.productId}`} className="table__primary">
                        {product.name}
                      </Link>
                    </Td>
                    <Td>
                      <span className="mono">{product.sku}</span>
                    </Td>
                    <Td>
                      <ActiveBadge active={product.active} />
                    </Td>
                    {canWrite && (
                      <Td className="table__actions">
                        <Button variant="ghost" size="sm" icon="trash" onClick={() => setToRemove(product)}>
                          Remove
                        </Button>
                      </Td>
                    )}
                  </Tr>
                ))}
              </Tbody>
            </Table>
          )}
        </Card>
      )}

      <ConfirmDialog
        open={toRemove !== null}
        title="Remove this product from the supplier?"
        description={`${toRemove?.name ?? ''} will no longer be listed as supplied by ${supplier.name}. Existing purchase orders are unaffected.`}
        confirmLabel="Remove"
        destructive
        isWorking={isSubmitting}
        onCancel={() => setToRemove(null)}
        onConfirm={() => {
          if (!toRemove) return;
          void replaceProducts(
            products.filter((existing) => existing.id !== toRemove.id).map((existing) => existing.productId),
            `${toRemove.name} removed.`
          );
        }}
      />
    </div>
  );
}

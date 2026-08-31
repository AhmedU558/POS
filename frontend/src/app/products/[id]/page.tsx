'use client';

import { useCallback, useEffect, useState } from 'react';
import Link from 'next/link';
import { useParams, useRouter } from 'next/navigation';
import { useAuth } from '@/features/auth/AuthContext';
import { useStoreContext } from '@/features/session/StoreContext';
import {
  CatalogReferenceData,
  addProductBarcode,
  getCatalogReferenceData,
  getProduct,
  getProductBarcodes,
  getProductPrices,
  removeProductBarcode,
  updateProduct,
  updateProductStatus,
} from '@/lib/api/catalog';
import { InventoryBalance, inventoryApi } from '@/lib/api/inventory';
import { ApiError } from '@/lib/api/http';
import { Product, ProductBarcode, ProductPrice } from '@/types/catalog';
import { errorMessage, formatDate, formatMoney, formatQuantity } from '@/lib/format';
import { P, hasPermission } from '@/lib/permissions';
import { PageHeader } from '@/components/ui/PageHeader';
import { Card, CardBody, CardHeader, Metric } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Checkbox, Input } from '@/components/ui/Field';
import { ActiveBadge, Badge } from '@/components/ui/Badge';
import { Table, Tbody, Td, Th, Thead, Tr } from '@/components/ui/Table';
import { ConfirmDialog } from '@/components/ui/Modal';
import { Alert, EmptyState, ErrorState, LoadingState, PermissionRequired } from '@/components/ui/States';
import { useToast } from '@/components/ui/Toast';
import {
  ProductForm,
  ProductFormErrors,
  ProductFormValues,
  formToCreateRequest,
  productToForm,
  validateProductForm,
} from '@/features/products/ProductForm';

type Tab = 'details' | 'barcodes' | 'prices';

export default function ProductDetailPage() {
  const { id } = useParams<{ id: string }>();
  const router = useRouter();
  const toast = useToast();
  const { user } = useAuth();
  const { activeStoreId } = useStoreContext();

  const canRead = hasPermission(user?.permissions, P.PRODUCT_READ);
  const canWrite = hasPermission(user?.permissions, P.PRODUCT_WRITE);
  const canWritePrices = hasPermission(user?.permissions, P.PRODUCT_PRICE_WRITE);
  const canReadInventory = hasPermission(user?.permissions, P.INVENTORY_READ);

  const [tab, setTab] = useState<Tab>('details');
  const [product, setProduct] = useState<Product | null>(null);
  const [reference, setReference] = useState<CatalogReferenceData>({ categories: [], brands: [], units: [] });
  const [barcodes, setBarcodes] = useState<ProductBarcode[]>([]);
  const [prices, setPrices] = useState<ProductPrice[]>([]);
  const [balance, setBalance] = useState<InventoryBalance | null>(null);

  const [values, setValues] = useState<ProductFormValues | null>(null);
  const [errors, setErrors] = useState<ProductFormErrors>({});
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  const [isLoading, setIsLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [statusPrompt, setStatusPrompt] = useState(false);
  const [barcodeToRemove, setBarcodeToRemove] = useState<ProductBarcode | null>(null);

  const load = useCallback(async () => {
    setIsLoading(true);
    setLoadError(null);
    try {
      const [loaded, loadedBarcodes, loadedPrices, loadedReference] = await Promise.all([
        getProduct(id),
        getProductBarcodes(id).catch(() => [] as ProductBarcode[]),
        getProductPrices(id).catch(() => [] as ProductPrice[]),
        getCatalogReferenceData().catch(() => ({ categories: [], brands: [], units: [] })),
      ]);
      setProduct(loaded);
      setValues(productToForm(loaded));
      setBarcodes(loadedBarcodes);
      setPrices(loadedPrices);
      setReference(loadedReference);
    } catch (caught) {
      setLoadError(errorMessage(caught));
    } finally {
      setIsLoading(false);
    }
  }, [id]);

  useEffect(() => {
    if (canRead) void load();
  }, [canRead, load]);

  // Stock lives in a different module; showing it here answers "do we have any?" in one place.
  useEffect(() => {
    if (!canReadInventory || !activeStoreId) return;
    inventoryApi
      .getBalance(id, activeStoreId)
      .then(setBalance)
      .catch(() => setBalance(null));
  }, [canReadInventory, activeStoreId, id]);

  if (!canRead) {
    return (
      <div className="page">
        <PermissionRequired permission={P.PRODUCT_READ} action="Viewing products" />
      </div>
    );
  }

  if (isLoading) {
    return (
      <div className="page">
        <LoadingState label="Loading product…" />
      </div>
    );
  }

  if (loadError || !product || !values) {
    return (
      <div className="page">
        <ErrorState message={loadError ?? 'Product not found.'} onRetry={() => void load()} />
      </div>
    );
  }

  const change = <K extends keyof ProductFormValues>(field: K, value: ProductFormValues[K]) => {
    setValues((current) => (current ? { ...current, [field]: value } : current));
    setErrors((current) => ({ ...current, [field]: undefined }));
  };

  const save = async () => {
    const found = validateProductForm(values);
    setErrors(found);
    if (Object.keys(found).length > 0) {
      setSubmitError('Check the highlighted fields and try again.');
      return;
    }
    setIsSubmitting(true);
    setSubmitError(null);
    try {
      /*
       * `isActive` is deliberately dropped: PATCH /products/{id} does not accept it, and
       * availability is changed through the explicit control below so it is never a side effect
       * of editing a price.
       */
      const { isActive, ...update } = formToCreateRequest(values);
      void isActive;
      const saved = await updateProduct(id, update);
      setProduct(saved);
      setValues(productToForm(saved));
      toast.success('Product saved.');
    } catch (caught) {
      if (caught instanceof ApiError && caught.code === 'CONFLICT') {
        setErrors({ sku: 'Another product already uses this SKU.' });
        setSubmitError('That SKU is already taken. Pick a different one.');
      } else {
        setSubmitError(errorMessage(caught));
      }
    } finally {
      setIsSubmitting(false);
    }
  };

  const toggleStatus = async () => {
    setIsSubmitting(true);
    try {
      await updateProductStatus(id, !product.isActive);
      const refreshed = await getProduct(id);
      setProduct(refreshed);
      setValues(productToForm(refreshed));
      toast.success(refreshed.isActive ? 'Product is available for sale.' : 'Product withdrawn from sale.');
    } catch (caught) {
      toast.error(errorMessage(caught));
    } finally {
      setIsSubmitting(false);
      setStatusPrompt(false);
    }
  };

  return (
    <div className="page">
      <PageHeader
        title={product.name}
        breadcrumbs={[{ label: 'Products', href: '/products' }, { label: product.name }]}
        description={`SKU ${product.sku}`}
        actions={
          <>
            <ActiveBadge active={product.isActive} />
            {canWrite && (
              <Button variant={product.isActive ? 'secondary' : 'primary'} onClick={() => setStatusPrompt(true)}>
                {product.isActive ? 'Withdraw from sale' : 'Make available'}
              </Button>
            )}
          </>
        }
      />

      <div className="metric-grid" style={{ marginBottom: 'var(--space-6)' }}>
        <Metric label="Selling price" value={formatMoney(product.sellingPrice)} meta={`Cost ${formatMoney(product.purchasePrice)}`} />
        <Metric
          label="Stock on hand"
          value={balance ? formatQuantity(balance.quantity) : canReadInventory ? '—' : 'Hidden'}
          meta={
            balance ? (
              balance.quantity <= product.minStock ? (
                <Badge variant="warning">At or below re-order level</Badge>
              ) : (
                `Re-order at ${formatQuantity(product.minStock)}`
              )
            ) : (
              'No stock recorded in this store'
            )
          }
        />
        <Metric label="Barcodes" value={barcodes.length} meta={barcodes.length === 0 ? 'Cannot be scanned yet' : 'Scannable at the till'} />
      </div>

      <div className="tabs" role="tablist">
        <button type="button" role="tab" className="tab" aria-selected={tab === 'details'} onClick={() => setTab('details')}>
          Details
        </button>
        <button type="button" role="tab" className="tab" aria-selected={tab === 'barcodes'} onClick={() => setTab('barcodes')}>
          Barcodes ({barcodes.length})
        </button>
        <button type="button" role="tab" className="tab" aria-selected={tab === 'prices'} onClick={() => setTab('prices')}>
          Price history ({prices.length})
        </button>
      </div>

      {tab === 'details' && (
        <ProductForm
          mode="edit"
          values={values}
          errors={errors}
          reference={reference}
          isSubmitting={isSubmitting}
          submitError={submitError}
          readOnly={!canWrite}
          onChange={change}
          onSubmit={() => void save()}
          onCancel={() => router.push('/products')}
        />
      )}

      {tab === 'barcodes' && (
        <BarcodesPanel
          productId={id}
          barcodes={barcodes}
          canWrite={canWrite}
          onChanged={setBarcodes}
          onRemoveRequest={setBarcodeToRemove}
        />
      )}

      {tab === 'prices' && <PricesPanel prices={prices} canWrite={canWritePrices} />}

      <ConfirmDialog
        open={statusPrompt}
        title={product.isActive ? 'Withdraw from sale?' : 'Make available for sale?'}
        description={
          product.isActive
            ? `${product.name} will no longer be sellable at the till. Existing stock and sales history are kept.`
            : `${product.name} will appear at the till and can be added to sales.`
        }
        confirmLabel={product.isActive ? 'Withdraw' : 'Make available'}
        destructive={product.isActive}
        isWorking={isSubmitting}
        onConfirm={() => void toggleStatus()}
        onCancel={() => setStatusPrompt(false)}
      />

      <ConfirmDialog
        open={barcodeToRemove !== null}
        title="Remove barcode?"
        description={`${barcodeToRemove?.barcode ?? ''} will no longer scan to this product.`}
        confirmLabel="Remove"
        destructive
        isWorking={isSubmitting}
        onCancel={() => setBarcodeToRemove(null)}
        onConfirm={async () => {
          if (!barcodeToRemove) return;
          setIsSubmitting(true);
          try {
            await removeProductBarcode(id, barcodeToRemove.id);
            setBarcodes(await getProductBarcodes(id));
            toast.success('Barcode removed.');
          } catch (caught) {
            toast.error(errorMessage(caught));
          } finally {
            setIsSubmitting(false);
            setBarcodeToRemove(null);
          }
        }}
      />
    </div>
  );
}

function BarcodesPanel({
  productId,
  barcodes,
  canWrite,
  onChanged,
  onRemoveRequest,
}: {
  productId: string;
  barcodes: ProductBarcode[];
  canWrite: boolean;
  onChanged: (next: ProductBarcode[]) => void;
  onRemoveRequest: (barcode: ProductBarcode) => void;
}) {
  const toast = useToast();
  const [value, setValue] = useState('');
  const [isPrimary, setIsPrimary] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [isSaving, setIsSaving] = useState(false);

  const add = async () => {
    const trimmed = value.trim();
    if (!trimmed) {
      setError('Scan or type a barcode first.');
      return;
    }
    setIsSaving(true);
    setError(null);
    try {
      await addProductBarcode(productId, { barcode: trimmed, isPrimary });
      onChanged(await getProductBarcodes(productId));
      setValue('');
      setIsPrimary(false);
      toast.success('Barcode added.');
    } catch (caught) {
      setError(errorMessage(caught));
    } finally {
      setIsSaving(false);
    }
  };

  return (
    <Card flush>
      <CardHeader title="Barcodes" />
      {canWrite && (
        <CardBody>
          <form
            className="row row-wrap"
            onSubmit={(event) => {
              event.preventDefault();
              void add();
            }}
          >
            <div className="grow">
              <Input
                id="new-barcode"
                label="Add a barcode"
                value={value}
                error={error ?? undefined}
                placeholder="Scan the product or type its number"
                onChange={(event) => setValue(event.target.value)}
              />
            </div>
            <Checkbox
              id="barcode-primary"
              label="Primary"
              checked={isPrimary}
              onChange={(event) => setIsPrimary(event.target.checked)}
            />
            <Button type="submit" isLoading={isSaving}>
              Add
            </Button>
          </form>
        </CardBody>
      )}
      {barcodes.length === 0 ? (
        <EmptyState
          icon="barcode"
          title="No barcodes yet"
          body="Without a barcode this product can still be found by name or SKU at the till, but it cannot be scanned."
        />
      ) : (
        <Table>
          <Thead>
            <Tr>
              <Th>Barcode</Th>
              <Th>Type</Th>
              {canWrite && <Th className="table__actions">Actions</Th>}
            </Tr>
          </Thead>
          <Tbody>
            {barcodes.map((barcode) => (
              <Tr key={barcode.id}>
                <Td>
                  <span className="mono">{barcode.barcode}</span>
                </Td>
                <Td>{barcode.isPrimary ? <Badge variant="info">Primary</Badge> : <span className="text-muted">Alternate</span>}</Td>
                {canWrite && (
                  <Td className="table__actions">
                    <Button variant="ghost" size="sm" icon="trash" onClick={() => onRemoveRequest(barcode)}>
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
  );
}

function PricesPanel({ prices, canWrite }: { prices: ProductPrice[]; canWrite: boolean }) {
  return (
    <Card flush>
      <CardHeader title="Price history" />
      {!canWrite && (
        <CardBody>
          <Alert tone="info">
            Recorded prices are read-only for your role. The selling price on the Details tab is what the till charges.
          </Alert>
        </CardBody>
      )}
      {prices.length === 0 ? (
        <EmptyState
          icon="reports"
          title="No recorded price changes"
          body="The till charges the selling price on the Details tab. Dated prices recorded here are kept for reporting."
        />
      ) : (
        <Table>
          <Thead>
            <Tr>
              <Th>Type</Th>
              <Th className="table__num">Amount</Th>
              <Th>Effective from</Th>
              <Th>Effective to</Th>
            </Tr>
          </Thead>
          <Tbody>
            {prices.map((price) => (
              <Tr key={price.id}>
                <Td>{price.priceType.charAt(0) + price.priceType.slice(1).toLowerCase()}</Td>
                <Td className="table__num">{formatMoney(price.amount)}</Td>
                <Td>{formatDate(price.effectiveFrom)}</Td>
                <Td>{price.effectiveTo ? formatDate(price.effectiveTo) : <span className="text-muted">Ongoing</span>}</Td>
              </Tr>
            ))}
          </Tbody>
        </Table>
      )}
    </Card>
  );
}

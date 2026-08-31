'use client';

import React, { useMemo, useState } from 'react';
import { CatalogReferenceData } from '@/lib/api/catalog';
import { Product, ProductCreateRequest } from '@/types/catalog';
import { Button } from '@/components/ui/Button';
import { Checkbox, Input, Select, Textarea } from '@/components/ui/Field';
import { Card, CardBody, CardFooter } from '@/components/ui/Card';
import { Alert } from '@/components/ui/States';

/*
 * The product form, shared by create and edit.
 *
 * Every field maps to something the API already stores — nothing here is invented. The two the
 * backend does not have are called out in the notes below rather than faked:
 *
 *   - No product image. `products` has no image column and no upload endpoint exists.
 *   - No supplier link on the product itself; that association lives on the supplier
 *     (`PUT /suppliers/{id}/products`) and is edited from the supplier's page.
 */

export interface ProductFormValues {
  sku: string;
  name: string;
  description: string;
  categoryId: string;
  brandId: string;
  unitId: string;
  purchasePrice: string;
  sellingPrice: string;
  wholesalePrice: string;
  taxRatePercent: string;
  minStock: string;
  maxStock: string;
  trackBatch: boolean;
  trackExpiry: boolean;
  isActive: boolean;
  imageUrl: string;
  /** Create only: a barcode entered here is saved straight after the product. */
  barcode: string;
}

export function emptyProductForm(): ProductFormValues {
  return {
    sku: '',
    name: '',
    description: '',
    categoryId: '',
    brandId: '',
    unitId: '',
    purchasePrice: '',
    sellingPrice: '',
    wholesalePrice: '',
    taxRatePercent: '0',
    minStock: '0',
    maxStock: '',
    trackBatch: false,
    trackExpiry: false,
    isActive: true,
    imageUrl: '',
    barcode: '',
  };
}

export function productToForm(product: Product): ProductFormValues {
  return {
    sku: product.sku,
    name: product.name,
    description: product.description ?? '',
    categoryId: product.categoryId ?? '',
    brandId: product.brandId ?? '',
    unitId: product.unitId ?? '',
    purchasePrice: String(product.purchasePrice ?? ''),
    sellingPrice: String(product.sellingPrice ?? ''),
    wholesalePrice: product.wholesalePrice === null ? '' : String(product.wholesalePrice),
    // Stored as a fraction, entered as a percentage: nobody types a tax rate as 0.15.
    taxRatePercent: String(round((Number(product.taxRate) || 0) * 100, 4)),
    minStock: String(product.minStock ?? '0'),
    maxStock: product.maxStock === null ? '' : String(product.maxStock),
    trackBatch: product.trackBatch,
    trackExpiry: product.trackExpiry,
    isActive: product.isActive,
    imageUrl: product.imageUrl ?? '',
    barcode: '',
  };
}

export type ProductFormErrors = Partial<Record<keyof ProductFormValues, string>>;

export function validateProductForm(values: ProductFormValues): ProductFormErrors {
  const errors: ProductFormErrors = {};

  if (!values.sku.trim()) errors.sku = 'A SKU is required. It must be unique across the catalogue.';
  if (!values.name.trim()) errors.name = 'Give the product a name.';

  const selling = Number(values.sellingPrice);
  if (values.sellingPrice.trim() === '' || Number.isNaN(selling)) {
    errors.sellingPrice = 'Enter the price customers pay.';
  } else if (selling < 0) {
    errors.sellingPrice = 'The selling price cannot be negative.';
  }

  const cost = Number(values.purchasePrice);
  if (values.purchasePrice.trim() === '' || Number.isNaN(cost)) {
    errors.purchasePrice = 'Enter what you pay for the product. Use 0 if you do not track cost.';
  } else if (cost < 0) {
    errors.purchasePrice = 'The cost price cannot be negative.';
  }

  if (values.wholesalePrice.trim() !== '' && Number(values.wholesalePrice) < 0) {
    errors.wholesalePrice = 'The wholesale price cannot be negative.';
  }

  const tax = Number(values.taxRatePercent);
  if (values.taxRatePercent.trim() === '' || Number.isNaN(tax) || tax < 0 || tax > 100) {
    errors.taxRatePercent = 'Enter a tax rate between 0 and 100.';
  }

  const min = Number(values.minStock);
  if (values.minStock.trim() === '' || Number.isNaN(min) || min < 0) {
    errors.minStock = 'Enter a re-order level, or 0 if you do not want low-stock alerts.';
  }

  if (values.maxStock.trim() !== '') {
    const max = Number(values.maxStock);
    if (Number.isNaN(max) || max < 0) {
      errors.maxStock = 'The maximum must be a positive number.';
    } else if (!Number.isNaN(min) && max < min) {
      errors.maxStock = 'The maximum cannot be below the re-order level.';
    }
  }

  // Expiry dates are recorded against a batch, so tracking one without the other stores nothing.
  if (values.trackExpiry && !values.trackBatch) {
    errors.trackBatch = 'Expiry dates are recorded per batch, so batch tracking must be on too.';
  }

  return errors;
}

export function formToCreateRequest(values: ProductFormValues): ProductCreateRequest {
  return {
    sku: values.sku.trim(),
    name: values.name.trim(),
    description: values.description.trim() || null,
    categoryId: values.categoryId || null,
    brandId: values.brandId || null,
    unitId: values.unitId || null,
    purchasePrice: Number(values.purchasePrice),
    sellingPrice: Number(values.sellingPrice),
    wholesalePrice: values.wholesalePrice.trim() === '' ? null : Number(values.wholesalePrice),
    taxRate: round(Number(values.taxRatePercent) / 100, 6),
    minStock: Number(values.minStock),
    maxStock: values.maxStock.trim() === '' ? null : Number(values.maxStock),
    trackBatch: values.trackBatch,
    trackExpiry: values.trackExpiry,
    isActive: values.isActive,
    imageUrl: values.imageUrl.trim() || null,
  };
}

function round(value: number, places: number): number {
  const factor = 10 ** places;
  return Math.round(value * factor) / factor;
}

export interface ProductFormProps {
  mode: 'create' | 'edit';
  values: ProductFormValues;
  errors: ProductFormErrors;
  reference: CatalogReferenceData;
  isSubmitting: boolean;
  submitError: string | null;
  readOnly?: boolean;
  onChange: <K extends keyof ProductFormValues>(field: K, value: ProductFormValues[K]) => void;
  onSubmit: () => void;
  onCancel: () => void;
}

export function ProductForm({
  mode,
  values,
  errors,
  reference,
  isSubmitting,
  submitError,
  readOnly = false,
  onChange,
  onSubmit,
  onCancel,
}: ProductFormProps) {
  const [showAdvanced, setShowAdvanced] = useState(mode === 'edit');

  const margin = useMemo(() => {
    const cost = Number(values.purchasePrice);
    const price = Number(values.sellingPrice);
    if (!Number.isFinite(cost) || !Number.isFinite(price) || price <= 0 || cost <= 0) {
      return null;
    }
    return `${round(((price - cost) / price) * 100, 1)}% margin`;
  }, [values.purchasePrice, values.sellingPrice]);

  return (
    <form
      className="stack-lg stack"
      onSubmit={(event) => {
        event.preventDefault();
        onSubmit();
      }}
      noValidate
    >
      {submitError && <Alert tone="error">{submitError}</Alert>}

      <Card>
        <CardBody className="stack">
          <div className="form-section">
            <h2 className="form-section__title">Identity</h2>
            <p className="form-section__description">How the product is named and found.</p>
            <div className="form-grid form-grid--2">
              <Input
                id="product-name"
                label="Product name"
                required
                value={values.name}
                error={errors.name}
                disabled={readOnly}
                onChange={(event) => onChange('name', event.target.value)}
                autoFocus={mode === 'create'}
              />
              <Input
                id="product-sku"
                label="SKU"
                required
                value={values.sku}
                error={errors.sku}
                disabled={readOnly}
                hint="Your own code for this product. Must be unique."
                onChange={(event) => onChange('sku', event.target.value)}
              />
            </div>
            {mode === 'create' && (
              <div style={{ marginTop: 'var(--space-4)' }}>
                <Input
                  id="product-barcode"
                  label="Barcode"
                  value={values.barcode}
                  disabled={readOnly}
                  hint="Scan the product now and it can be rung up at the till straight away. You can add more barcodes later."
                  onChange={(event) => onChange('barcode', event.target.value)}
                />
              </div>
            )}
            <div style={{ marginTop: 'var(--space-4)' }}>
              <Textarea
                id="product-description"
                label="Description"
                rows={2}
                value={values.description}
                disabled={readOnly}
                onChange={(event) => onChange('description', event.target.value)}
              />
            </div>
            <div style={{ marginTop: 'var(--space-4)' }}>
              <Input
                id="product-image-url"
                label="Image URL"
                value={values.imageUrl}
                disabled={readOnly}
                hint="URL to a product image. A 1:1 square image works best on the POS."
                onChange={(event) => onChange('imageUrl', event.target.value)}
              />
            </div>
          </div>

          <div className="form-section">
            <h2 className="form-section__title">Classification</h2>
            <p className="form-section__description">
              Optional, but categories drive the till&apos;s product grid and the sales-by-category report.
            </p>
            <div className="form-grid">
              <Select
                id="product-category"
                label="Category"
                placeholder="No category"
                value={values.categoryId}
                disabled={readOnly}
                onChange={(event) => onChange('categoryId', event.target.value)}
                options={reference.categories.map((category) => ({ value: category.id, label: category.name }))}
              />
              <Select
                id="product-brand"
                label="Brand"
                placeholder="No brand"
                value={values.brandId}
                disabled={readOnly}
                onChange={(event) => onChange('brandId', event.target.value)}
                options={reference.brands.map((brand) => ({ value: brand.id, label: brand.name }))}
              />
              <Select
                id="product-unit"
                label="Unit"
                placeholder="No unit"
                value={values.unitId}
                disabled={readOnly}
                onChange={(event) => onChange('unitId', event.target.value)}
                options={reference.units.map((unit) => ({ value: unit.id, label: `${unit.name} (${unit.code})` }))}
              />
            </div>
          </div>

          <div className="form-section">
            <h2 className="form-section__title">Pricing</h2>
            <p className="form-section__description">
              Tax is added to the line at the till using the rate below.
              {margin && <> Current {margin}.</>}
            </p>
            <div className="form-grid">
              <Input
                id="product-cost"
                label="Cost price"
                required
                type="number"
                step="0.01"
                min="0"
                inputMode="decimal"
                value={values.purchasePrice}
                error={errors.purchasePrice}
                disabled={readOnly}
                onChange={(event) => onChange('purchasePrice', event.target.value)}
              />
              <Input
                id="product-price"
                label="Selling price"
                required
                type="number"
                step="0.01"
                min="0"
                inputMode="decimal"
                value={values.sellingPrice}
                error={errors.sellingPrice}
                disabled={readOnly}
                onChange={(event) => onChange('sellingPrice', event.target.value)}
              />
              <Input
                id="product-tax"
                label="Tax rate (%)"
                required
                type="number"
                step="0.01"
                min="0"
                max="100"
                inputMode="decimal"
                value={values.taxRatePercent}
                error={errors.taxRatePercent}
                disabled={readOnly}
                hint="Enter 15 for 15%."
                onChange={(event) => onChange('taxRatePercent', event.target.value)}
              />
              <Input
                id="product-wholesale"
                label="Wholesale price"
                type="number"
                step="0.01"
                min="0"
                inputMode="decimal"
                value={values.wholesalePrice}
                error={errors.wholesalePrice}
                disabled={readOnly}
                hint="Optional."
                onChange={(event) => onChange('wholesalePrice', event.target.value)}
              />
            </div>
          </div>

          {showAdvanced ? (
            <div className="form-section">
              <h2 className="form-section__title">Stock control</h2>
              <p className="form-section__description">
                Controls low-stock alerts and whether the product is received in batches.
              </p>
              <div className="form-grid form-grid--2">
                <Input
                  id="product-min-stock"
                  label="Re-order level"
                  required
                  type="number"
                  step="1"
                  min="0"
                  inputMode="decimal"
                  value={values.minStock}
                  error={errors.minStock}
                  disabled={readOnly}
                  hint="Raises a low-stock alert when quantity falls to this level."
                  onChange={(event) => onChange('minStock', event.target.value)}
                />
                <Input
                  id="product-max-stock"
                  label="Maximum stock"
                  type="number"
                  step="1"
                  min="0"
                  inputMode="decimal"
                  value={values.maxStock}
                  error={errors.maxStock}
                  disabled={readOnly}
                  hint="Optional."
                  onChange={(event) => onChange('maxStock', event.target.value)}
                />
              </div>
              <div className="row row-wrap" style={{ marginTop: 'var(--space-2)' }}>
                <Checkbox
                  id="product-track-batch"
                  label="Track batches"
                  hint="Record a batch number when stock is received."
                  checked={values.trackBatch}
                  disabled={readOnly}
                  onChange={(event) => onChange('trackBatch', event.target.checked)}
                />
                <Checkbox
                  id="product-track-expiry"
                  label="Track expiry dates"
                  hint="Adds expiry alerts and shows the product in the expiry report."
                  checked={values.trackExpiry}
                  disabled={readOnly}
                  onChange={(event) => onChange('trackExpiry', event.target.checked)}
                />
              </div>
              {errors.trackBatch && (
                <p className="field__error" style={{ marginTop: 'var(--space-2)' }}>
                  {errors.trackBatch}
                </p>
              )}
              {mode === 'create' && (
                <div style={{ marginTop: 'var(--space-2)' }}>
                  <Checkbox
                    id="product-active"
                    label="Available for sale"
                    hint="Turn off to keep the product on file without offering it at the till."
                    checked={values.isActive}
                    disabled={readOnly}
                    onChange={(event) => onChange('isActive', event.target.checked)}
                  />
                </div>
              )}
            </div>
          ) : (
            <div className="form-section">
              <Button variant="ghost" onClick={() => setShowAdvanced(true)}>
                Stock control settings
              </Button>
              <p className="text-small text-muted" style={{ marginTop: 'var(--space-1)' }}>
                Re-order level, batch and expiry tracking. Sensible defaults are already set.
              </p>
            </div>
          )}
        </CardBody>
        {!readOnly && (
          <CardFooter>
            <Button variant="secondary" onClick={onCancel} disabled={isSubmitting}>
              Cancel
            </Button>
            <Button type="submit" isLoading={isSubmitting}>
              {mode === 'create' ? 'Create product' : 'Save changes'}
            </Button>
          </CardFooter>
        )}
      </Card>
    </form>
  );
}

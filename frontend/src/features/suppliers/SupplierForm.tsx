'use client';

import { SupplierRequest } from '@/lib/api/suppliers';
import { Button } from '@/components/ui/Button';
import { Card, CardBody, CardFooter } from '@/components/ui/Card';
import { Checkbox, Input, Textarea } from '@/components/ui/Field';
import { Alert } from '@/components/ui/States';

export interface SupplierFormValues {
  supplierCode: string;
  name: string;
  phone: string;
  email: string;
  address: string;
  isActive: boolean;
}

export function emptySupplierForm(): SupplierFormValues {
  return { supplierCode: '', name: '', phone: '', email: '', address: '', isActive: true };
}

export type SupplierFormErrors = Partial<Record<keyof SupplierFormValues, string>>;

export function validateSupplierForm(values: SupplierFormValues): SupplierFormErrors {
  const errors: SupplierFormErrors = {};
  if (!values.name.trim()) errors.name = 'Give the supplier a name.';
  if (!values.supplierCode.trim()) errors.supplierCode = 'A supplier code is required and must be unique.';
  if (values.email.trim() && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(values.email.trim())) {
    errors.email = 'That does not look like an email address.';
  }
  return errors;
}

export function supplierFormToRequest(values: SupplierFormValues): SupplierRequest {
  return {
    supplierCode: values.supplierCode.trim(),
    name: values.name.trim(),
    phone: values.phone.trim() || null,
    email: values.email.trim() || null,
    address: values.address.trim() || null,
    isActive: values.isActive,
  };
}

export function suggestSupplierCode(name: string): string {
  const letters = name
    .toUpperCase()
    .replace(/[^A-Z0-9 ]/g, '')
    .split(/\s+/)
    .filter(Boolean)
    .map((word) => word.slice(0, 3))
    .join('')
    .slice(0, 8);
  return letters ? `${letters}-${String(Date.now()).slice(-4)}` : '';
}

export function SupplierForm({
  values,
  errors,
  submitError,
  isSubmitting,
  submitLabel,
  onChange,
  onSubmit,
  onCancel,
}: {
  values: SupplierFormValues;
  errors: SupplierFormErrors;
  submitError: string | null;
  isSubmitting: boolean;
  submitLabel: string;
  onChange: <K extends keyof SupplierFormValues>(field: K, value: SupplierFormValues[K]) => void;
  onSubmit: () => void;
  onCancel: () => void;
}) {
  return (
    <form
      className="stack"
      onSubmit={(event) => {
        event.preventDefault();
        onSubmit();
      }}
      noValidate
    >
      {submitError && <Alert tone="error">{submitError}</Alert>}
      <Card>
        <CardBody className="stack">
          <div className="form-grid form-grid--2">
            <Input
              id="supplier-name"
              label="Supplier name"
              required
              value={values.name}
              error={errors.name}
              autoFocus
              onChange={(event) => {
                onChange('name', event.target.value);
                if (!values.supplierCode) {
                  onChange('supplierCode', suggestSupplierCode(event.target.value));
                }
              }}
            />
            <Input
              id="supplier-code"
              label="Supplier code"
              required
              value={values.supplierCode}
              error={errors.supplierCode}
              hint="Your reference for this supplier. Suggested from the name."
              onChange={(event) => onChange('supplierCode', event.target.value)}
            />
          </div>
          <div className="form-grid form-grid--2">
            <Input
              id="supplier-phone"
              label="Phone"
              type="tel"
              value={values.phone}
              onChange={(event) => onChange('phone', event.target.value)}
            />
            <Input
              id="supplier-email"
              label="Email"
              type="email"
              value={values.email}
              error={errors.email}
              onChange={(event) => onChange('email', event.target.value)}
            />
          </div>
          <Textarea
            id="supplier-address"
            label="Address"
            rows={2}
            value={values.address}
            onChange={(event) => onChange('address', event.target.value)}
          />
          <Checkbox
            id="supplier-active"
            label="Active"
            hint="Inactive suppliers cannot be chosen on a new purchase order."
            checked={values.isActive}
            onChange={(event) => onChange('isActive', event.target.checked)}
          />
        </CardBody>
        <CardFooter>
          <Button variant="secondary" onClick={onCancel} disabled={isSubmitting}>
            Cancel
          </Button>
          <Button type="submit" isLoading={isSubmitting}>
            {submitLabel}
          </Button>
        </CardFooter>
      </Card>
    </form>
  );
}

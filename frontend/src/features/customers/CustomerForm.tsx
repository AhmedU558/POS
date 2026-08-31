'use client';

import { CustomerRequest } from '@/lib/api/customers';
import { Button } from '@/components/ui/Button';
import { Card, CardBody, CardFooter } from '@/components/ui/Card';
import { Checkbox, Input, Textarea } from '@/components/ui/Field';
import { Alert } from '@/components/ui/States';

export interface CustomerFormValues {
  customerCode: string;
  name: string;
  phone: string;
  email: string;
  address: string;
  creditLimit: string;
  isActive: boolean;
}

export function emptyCustomerForm(): CustomerFormValues {
  return { customerCode: '', name: '', phone: '', email: '', address: '', creditLimit: '0', isActive: true };
}

export type CustomerFormErrors = Partial<Record<keyof CustomerFormValues, string>>;

export function validateCustomerForm(values: CustomerFormValues): CustomerFormErrors {
  const errors: CustomerFormErrors = {};
  if (!values.name.trim()) errors.name = 'Give the customer a name.';
  if (!values.customerCode.trim()) errors.customerCode = 'A customer code is required and must be unique.';
  if (values.email.trim() && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(values.email.trim())) {
    errors.email = 'That does not look like an email address.';
  }
  const limit = Number(values.creditLimit);
  if (values.creditLimit.trim() === '' || !Number.isFinite(limit) || limit < 0) {
    errors.creditLimit = 'Enter a credit limit, or 0 for cash-only.';
  }
  return errors;
}

export function customerFormToRequest(values: CustomerFormValues): CustomerRequest {
  return {
    customerCode: values.customerCode.trim(),
    name: values.name.trim(),
    phone: values.phone.trim() || null,
    email: values.email.trim() || null,
    address: values.address.trim() || null,
    creditLimit: Number(values.creditLimit),
    isActive: values.isActive,
  };
}

/** Suggests a code from the name so nobody has to invent a numbering scheme to save a record. */
export function suggestCustomerCode(name: string): string {
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

export function CustomerForm({
  values,
  errors,
  submitError,
  isSubmitting,
  submitLabel,
  onChange,
  onSubmit,
  onCancel,
}: {
  values: CustomerFormValues;
  errors: CustomerFormErrors;
  submitError: string | null;
  isSubmitting: boolean;
  submitLabel: string;
  onChange: <K extends keyof CustomerFormValues>(field: K, value: CustomerFormValues[K]) => void;
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
              id="customer-name"
              label="Name"
              required
              value={values.name}
              error={errors.name}
              autoFocus
              onChange={(event) => {
                onChange('name', event.target.value);
                if (!values.customerCode) {
                  onChange('customerCode', suggestCustomerCode(event.target.value));
                }
              }}
            />
            <Input
              id="customer-code"
              label="Customer code"
              required
              value={values.customerCode}
              error={errors.customerCode}
              hint="Their account reference. Suggested from the name."
              onChange={(event) => onChange('customerCode', event.target.value)}
            />
          </div>
          <div className="form-grid form-grid--2">
            <Input
              id="customer-phone"
              label="Phone"
              type="tel"
              value={values.phone}
              onChange={(event) => onChange('phone', event.target.value)}
            />
            <Input
              id="customer-email"
              label="Email"
              type="email"
              value={values.email}
              error={errors.email}
              onChange={(event) => onChange('email', event.target.value)}
            />
          </div>
          <Textarea
            id="customer-address"
            label="Address"
            rows={2}
            value={values.address}
            onChange={(event) => onChange('address', event.target.value)}
          />
          <Input
            id="customer-credit-limit"
            label="Credit limit"
            required
            type="number"
            min="0"
            step="0.01"
            inputMode="decimal"
            value={values.creditLimit}
            error={errors.creditLimit}
            hint="The most this customer may owe at once. Leave at 0 if they always pay at the till."
            onChange={(event) => onChange('creditLimit', event.target.value)}
          />
          <Checkbox
            id="customer-active"
            label="Active"
            hint="Inactive customers cannot be added to new sales."
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

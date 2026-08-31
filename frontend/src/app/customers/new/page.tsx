'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { useAuth } from '@/features/auth/AuthContext';
import { customersApi } from '@/lib/api/customers';
import { ApiError } from '@/lib/api/http';
import { errorMessage } from '@/lib/format';
import { P, hasPermission } from '@/lib/permissions';
import { PageHeader } from '@/components/ui/PageHeader';
import { PermissionRequired } from '@/components/ui/States';
import { useToast } from '@/components/ui/Toast';
import {
  CustomerForm,
  CustomerFormErrors,
  CustomerFormValues,
  customerFormToRequest,
  emptyCustomerForm,
  validateCustomerForm,
} from '@/features/customers/CustomerForm';

export default function NewCustomerPage() {
  const router = useRouter();
  const toast = useToast();
  const { user } = useAuth();
  const canWrite = hasPermission(user?.permissions, P.CUSTOMER_WRITE);

  const [values, setValues] = useState<CustomerFormValues>(emptyCustomerForm);
  const [errors, setErrors] = useState<CustomerFormErrors>({});
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  if (!canWrite) {
    return (
      <div className="page">
        <PermissionRequired permission={P.CUSTOMER_WRITE} action="Creating customers" />
      </div>
    );
  }

  const submit = async () => {
    const found = validateCustomerForm(values);
    setErrors(found);
    if (Object.keys(found).length > 0) {
      setSubmitError('Check the highlighted fields and try again.');
      return;
    }
    setIsSubmitting(true);
    setSubmitError(null);
    try {
      const created = await customersApi.create(customerFormToRequest(values));
      toast.success(`${created.name} added.`);
      router.push(`/customers/${created.id}`);
    } catch (caught) {
      if (caught instanceof ApiError && caught.code === 'CONFLICT') {
        setErrors({ customerCode: 'Another customer already uses this code.' });
        setSubmitError('That customer code is taken. Pick a different one.');
      } else {
        setSubmitError(errorMessage(caught));
      }
      setIsSubmitting(false);
    }
  };

  return (
    <div className="page page-narrow">
      <PageHeader
        title="Add customer"
        breadcrumbs={[{ label: 'Customers', href: '/customers' }, { label: 'Add customer' }]}
        description="Only a name and a code are required."
      />
      <CustomerForm
        values={values}
        errors={errors}
        submitError={submitError}
        isSubmitting={isSubmitting}
        submitLabel="Add customer"
        onChange={(field, value) => {
          setValues((current) => ({ ...current, [field]: value }));
          setErrors((current) => ({ ...current, [field]: undefined }));
        }}
        onSubmit={() => void submit()}
        onCancel={() => router.push('/customers')}
      />
    </div>
  );
}

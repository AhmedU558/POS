'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { useAuth } from '@/features/auth/AuthContext';
import { suppliersApi } from '@/lib/api/suppliers';
import { ApiError } from '@/lib/api/http';
import { errorMessage } from '@/lib/format';
import { P, hasPermission } from '@/lib/permissions';
import { PageHeader } from '@/components/ui/PageHeader';
import { PermissionRequired } from '@/components/ui/States';
import { useToast } from '@/components/ui/Toast';
import {
  SupplierForm,
  SupplierFormErrors,
  SupplierFormValues,
  emptySupplierForm,
  supplierFormToRequest,
  validateSupplierForm,
} from '@/features/suppliers/SupplierForm';

export default function NewSupplierPage() {
  const router = useRouter();
  const toast = useToast();
  const { user } = useAuth();
  const canWrite = hasPermission(user?.permissions, P.SUPPLIER_WRITE);

  const [values, setValues] = useState<SupplierFormValues>(emptySupplierForm);
  const [errors, setErrors] = useState<SupplierFormErrors>({});
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  if (!canWrite) {
    return (
      <div className="page">
        <PermissionRequired permission={P.SUPPLIER_WRITE} action="Creating suppliers" />
      </div>
    );
  }

  const submit = async () => {
    const found = validateSupplierForm(values);
    setErrors(found);
    if (Object.keys(found).length > 0) {
      setSubmitError('Check the highlighted fields and try again.');
      return;
    }
    setIsSubmitting(true);
    setSubmitError(null);
    try {
      const created = await suppliersApi.create(supplierFormToRequest(values));
      toast.success(`${created.name} added. You can now raise a purchase order for them.`);
      router.push(`/suppliers/${created.id}`);
    } catch (caught) {
      if (caught instanceof ApiError && caught.code === 'CONFLICT') {
        setErrors({ supplierCode: 'Another supplier already uses this code.' });
        setSubmitError('That supplier code is taken. Pick a different one.');
      } else {
        setSubmitError(errorMessage(caught));
      }
      setIsSubmitting(false);
    }
  };

  return (
    <div className="page page-narrow">
      <PageHeader
        title="Add supplier"
        breadcrumbs={[{ label: 'Suppliers', href: '/suppliers' }, { label: 'Add supplier' }]}
        description="Only a name and a code are required."
      />
      <SupplierForm
        values={values}
        errors={errors}
        submitError={submitError}
        isSubmitting={isSubmitting}
        submitLabel="Add supplier"
        onChange={(field, value) => {
          setValues((current) => ({ ...current, [field]: value }));
          setErrors((current) => ({ ...current, [field]: undefined }));
        }}
        onSubmit={() => void submit()}
        onCancel={() => router.push('/suppliers')}
      />
    </div>
  );
}

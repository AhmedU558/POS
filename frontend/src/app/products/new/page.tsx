'use client';

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { useAuth } from '@/features/auth/AuthContext';
import { CatalogReferenceData, addProductBarcode, createProduct, getCatalogReferenceData } from '@/lib/api/catalog';
import { ApiError } from '@/lib/api/http';
import { errorMessage } from '@/lib/format';
import { P, hasPermission } from '@/lib/permissions';
import { PageHeader } from '@/components/ui/PageHeader';
import { LoadingState, PermissionRequired } from '@/components/ui/States';
import { useToast } from '@/components/ui/Toast';
import {
  ProductForm,
  ProductFormErrors,
  ProductFormValues,
  emptyProductForm,
  formToCreateRequest,
  validateProductForm,
} from '@/features/products/ProductForm';

export default function NewProductPage() {
  const router = useRouter();
  const toast = useToast();
  const { user } = useAuth();
  const canWrite = hasPermission(user?.permissions, P.PRODUCT_WRITE);

  const [values, setValues] = useState<ProductFormValues>(emptyProductForm);
  const [errors, setErrors] = useState<ProductFormErrors>({});
  const [reference, setReference] = useState<CatalogReferenceData | null>(null);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  useEffect(() => {
    if (!canWrite) return;
    getCatalogReferenceData()
      .then(setReference)
      // Classification is optional, so an empty reference list must not block product creation.
      .catch(() => setReference({ categories: [], brands: [], units: [] }));
  }, [canWrite]);

  if (!canWrite) {
    return (
      <div className="page">
        <PermissionRequired permission={P.PRODUCT_WRITE} action="Creating products" />
      </div>
    );
  }

  const change = <K extends keyof ProductFormValues>(field: K, value: ProductFormValues[K]) => {
    setValues((current) => ({ ...current, [field]: value }));
    setErrors((current) => ({ ...current, [field]: undefined }));
  };

  const submit = async () => {
    const found = validateProductForm(values);
    setErrors(found);
    if (Object.keys(found).length > 0) {
      setSubmitError('Check the highlighted fields and try again.');
      return;
    }

    setIsSubmitting(true);
    setSubmitError(null);
    try {
      const product = await createProduct(formToCreateRequest(values));

      /*
       * The barcode is a second call because it is a separate resource. A failure here leaves a
       * perfectly good product, so it is reported as a warning and the user is dropped on the
       * product page where they can retry — not treated as a failed creation.
       */
      const barcode = values.barcode.trim();
      if (barcode) {
        try {
          await addProductBarcode(product.id, { barcode, isPrimary: true });
        } catch (caught) {
          toast.error(`${product.name} was created, but the barcode could not be saved: ${errorMessage(caught)}`);
          router.push(`/products/${product.id}`);
          return;
        }
      }

      toast.success(`${product.name} created${barcode ? ' and ready to scan' : ''}.`);
      router.push(`/products/${product.id}`);
    } catch (caught) {
      if (caught instanceof ApiError && caught.code === 'CONFLICT') {
        setErrors({ sku: 'Another product already uses this SKU.' });
        setSubmitError('That SKU is already taken. Pick a different one.');
      } else if (caught instanceof ApiError && caught.fieldErrors) {
        setSubmitError(caught.message);
      } else {
        setSubmitError(errorMessage(caught));
      }
      setIsSubmitting(false);
    }
  };

  return (
    <div className="page page-narrow">
      <PageHeader
        title="Add product"
        breadcrumbs={[
          { label: 'Products', href: '/products' },
          { label: 'Add product' },
        ]}
        description="Name, SKU and selling price are all that is required. Everything else can be filled in later."
      />
      {reference === null ? (
        <LoadingState label="Loading categories and units…" />
      ) : (
        <ProductForm
          mode="create"
          values={values}
          errors={errors}
          reference={reference}
          isSubmitting={isSubmitting}
          submitError={submitError}
          onChange={change}
          onSubmit={() => void submit()}
          onCancel={() => router.push('/products')}
        />
      )}
    </div>
  );
}

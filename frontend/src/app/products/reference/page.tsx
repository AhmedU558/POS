'use client';

import { useCallback, useEffect, useState } from 'react';
import { useAuth } from '@/features/auth/AuthContext';
import {
  createBrand,
  createCategory,
  createUnit,
  getBrands,
  getCategories,
  getUnits,
  updateBrand,
  updateCategory,
  updateUnit,
} from '@/lib/api/catalog';
import { Brand, Category, Unit } from '@/types/catalog';
import { errorMessage } from '@/lib/format';
import { P, hasPermission } from '@/lib/permissions';
import { PageHeader } from '@/components/ui/PageHeader';
import { Card, CardHeader } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Checkbox, Input, Select } from '@/components/ui/Field';
import { ActiveBadge } from '@/components/ui/Badge';
import { Table, Tbody, Td, Th, Thead, Tr } from '@/components/ui/Table';
import { Modal } from '@/components/ui/Modal';
import { EmptyState, ErrorState, LoadingState, PermissionRequired } from '@/components/ui/States';
import { useToast } from '@/components/ui/Toast';

type Tab = 'categories' | 'brands' | 'units';

/**
 * Categories, brands and units.
 *
 * Grouped behind Products rather than given three top-level menu entries: they exist only to
 * classify products, and a store manager should not have to learn them as separate modules.
 */
export default function CatalogReferencePage() {
  const { user } = useAuth();
  const canRead = hasPermission(user?.permissions, P.PRODUCT_READ);
  const canWrite = hasPermission(user?.permissions, P.PRODUCT_WRITE);
  const [tab, setTab] = useState<Tab>('categories');

  if (!canRead) {
    return (
      <div className="page">
        <PermissionRequired permission={P.PRODUCT_READ} action="Viewing the catalogue" />
      </div>
    );
  }

  return (
    <div className="page">
      <PageHeader
        title="Categories, brands & units"
        breadcrumbs={[{ label: 'Products', href: '/products' }, { label: 'Categories & units' }]}
        description="How products are grouped. Categories also drive the product grid at the till and the sales-by-category report."
      />

      <div className="tabs" role="tablist">
        <button type="button" role="tab" className="tab" aria-selected={tab === 'categories'} onClick={() => setTab('categories')}>
          Categories
        </button>
        <button type="button" role="tab" className="tab" aria-selected={tab === 'brands'} onClick={() => setTab('brands')}>
          Brands
        </button>
        <button type="button" role="tab" className="tab" aria-selected={tab === 'units'} onClick={() => setTab('units')}>
          Units
        </button>
      </div>

      {tab === 'categories' && <CategoriesPanel canWrite={canWrite} />}
      {tab === 'brands' && <BrandsPanel canWrite={canWrite} />}
      {tab === 'units' && <UnitsPanel canWrite={canWrite} />}
    </div>
  );
}

/** Shared load/error/empty scaffolding so the three panels behave identically. */
function useReferenceList<T>(loader: () => Promise<T[]>) {
  const [items, setItems] = useState<T[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setError(null);
    try {
      setItems(await loader());
    } catch (caught) {
      setError(errorMessage(caught));
      setItems([]);
    }
  }, [loader]);

  useEffect(() => {
    void load();
  }, [load]);

  return { items, error, reload: load };
}

function CategoriesPanel({ canWrite }: { canWrite: boolean }) {
  const toast = useToast();
  const { items, error, reload } = useReferenceList<Category>(getCategories);
  const [editing, setEditing] = useState<Category | 'new' | null>(null);
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [parentId, setParentId] = useState('');
  const [isActive, setIsActive] = useState(true);
  const [isSaving, setIsSaving] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);

  const open = (category: Category | 'new') => {
    setEditing(category);
    setFormError(null);
    setName(category === 'new' ? '' : category.name);
    setDescription(category === 'new' ? '' : (category.description ?? ''));
    setParentId(category === 'new' ? '' : (category.parentId ?? ''));
    setIsActive(category === 'new' ? true : category.active);
  };

  const save = async () => {
    if (!name.trim()) {
      setFormError('Give the category a name.');
      return;
    }
    setIsSaving(true);
    setFormError(null);
    try {
      const body = { name: name.trim(), description: description.trim() || null, parentId: parentId || null, isActive };
      if (editing === 'new') {
        await createCategory(body);
        toast.success('Category created.');
      } else if (editing) {
        await updateCategory(editing.id, body);
        toast.success('Category saved.');
      }
      setEditing(null);
      await reload();
    } catch (caught) {
      setFormError(errorMessage(caught));
    } finally {
      setIsSaving(false);
    }
  };

  if (error) return <ErrorState message={error} onRetry={() => void reload()} />;
  if (items === null) return <LoadingState label="Loading categories…" />;

  return (
    <Card flush>
      <CardHeader
        title="Categories"
        actions={canWrite ? <Button icon="plus" size="sm" onClick={() => open('new')}>Add category</Button> : undefined}
      />
      {items.length === 0 ? (
        <EmptyState
          icon="products"
          title="No categories yet"
          body="Categories group products so the till can show them as tabs. They are optional."
          action={canWrite ? { label: 'Add category', onClick: () => open('new') } : undefined}
        />
      ) : (
        <Table>
          <Thead>
            <Tr>
              <Th>Name</Th>
              <Th>Parent</Th>
              <Th>Status</Th>
              {canWrite && <Th className="table__actions">Actions</Th>}
            </Tr>
          </Thead>
          <Tbody>
            {items.map((category) => (
              <Tr key={category.id}>
                <Td>
                  <span className="table__primary">{category.name}</span>
                  {category.description && <div className="table__secondary">{category.description}</div>}
                </Td>
                <Td>{items.find((other) => other.id === category.parentId)?.name ?? <span className="text-muted">—</span>}</Td>
                <Td>
                  <ActiveBadge active={category.active} />
                </Td>
                {canWrite && (
                  <Td className="table__actions">
                    <Button variant="secondary" size="sm" onClick={() => open(category)}>
                      Edit
                    </Button>
                  </Td>
                )}
              </Tr>
            ))}
          </Tbody>
        </Table>
      )}

      <Modal
        open={editing !== null}
        onClose={() => setEditing(null)}
        title={editing === 'new' ? 'Add category' : 'Edit category'}
        busy={isSaving}
        footer={
          <>
            <Button variant="secondary" onClick={() => setEditing(null)} disabled={isSaving}>
              Cancel
            </Button>
            <Button onClick={() => void save()} isLoading={isSaving}>
              Save
            </Button>
          </>
        }
      >
        <div className="stack">
          {formError && <p className="field__error">{formError}</p>}
          <Input id="category-name" label="Name" required value={name} onChange={(event) => setName(event.target.value)} />
          <Input
            id="category-description"
            label="Description"
            value={description}
            onChange={(event) => setDescription(event.target.value)}
          />
          <Select
            id="category-parent"
            label="Parent category"
            placeholder="Top level"
            value={parentId}
            onChange={(event) => setParentId(event.target.value)}
            options={items
              .filter((candidate) => editing === 'new' || candidate.id !== editing?.id)
              .map((candidate) => ({ value: candidate.id, label: candidate.name }))}
          />
          <Checkbox id="category-active" label="Active" checked={isActive} onChange={(event) => setIsActive(event.target.checked)} />
        </div>
      </Modal>
    </Card>
  );
}

function BrandsPanel({ canWrite }: { canWrite: boolean }) {
  const toast = useToast();
  const { items, error, reload } = useReferenceList<Brand>(getBrands);
  const [editing, setEditing] = useState<Brand | 'new' | null>(null);
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [isActive, setIsActive] = useState(true);
  const [isSaving, setIsSaving] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);

  const open = (brand: Brand | 'new') => {
    setEditing(brand);
    setFormError(null);
    setName(brand === 'new' ? '' : brand.name);
    setDescription(brand === 'new' ? '' : (brand.description ?? ''));
    setIsActive(brand === 'new' ? true : brand.active);
  };

  const save = async () => {
    if (!name.trim()) {
      setFormError('Give the brand a name.');
      return;
    }
    setIsSaving(true);
    setFormError(null);
    try {
      const body = { name: name.trim(), description: description.trim() || null, isActive };
      if (editing === 'new') {
        await createBrand(body);
        toast.success('Brand created.');
      } else if (editing) {
        await updateBrand(editing.id, body);
        toast.success('Brand saved.');
      }
      setEditing(null);
      await reload();
    } catch (caught) {
      setFormError(errorMessage(caught));
    } finally {
      setIsSaving(false);
    }
  };

  if (error) return <ErrorState message={error} onRetry={() => void reload()} />;
  if (items === null) return <LoadingState label="Loading brands…" />;

  return (
    <Card flush>
      <CardHeader
        title="Brands"
        actions={canWrite ? <Button icon="plus" size="sm" onClick={() => open('new')}>Add brand</Button> : undefined}
      />
      {items.length === 0 ? (
        <EmptyState
          icon="products"
          title="No brands yet"
          body="Brands are optional. Add them if you want to filter and report by manufacturer."
          action={canWrite ? { label: 'Add brand', onClick: () => open('new') } : undefined}
        />
      ) : (
        <Table>
          <Thead>
            <Tr>
              <Th>Name</Th>
              <Th>Description</Th>
              <Th>Status</Th>
              {canWrite && <Th className="table__actions">Actions</Th>}
            </Tr>
          </Thead>
          <Tbody>
            {items.map((brand) => (
              <Tr key={brand.id}>
                <Td className="table__primary">{brand.name}</Td>
                <Td>{brand.description ?? <span className="text-muted">—</span>}</Td>
                <Td>
                  <ActiveBadge active={brand.active} />
                </Td>
                {canWrite && (
                  <Td className="table__actions">
                    <Button variant="secondary" size="sm" onClick={() => open(brand)}>
                      Edit
                    </Button>
                  </Td>
                )}
              </Tr>
            ))}
          </Tbody>
        </Table>
      )}

      <Modal
        open={editing !== null}
        onClose={() => setEditing(null)}
        title={editing === 'new' ? 'Add brand' : 'Edit brand'}
        busy={isSaving}
        footer={
          <>
            <Button variant="secondary" onClick={() => setEditing(null)} disabled={isSaving}>
              Cancel
            </Button>
            <Button onClick={() => void save()} isLoading={isSaving}>
              Save
            </Button>
          </>
        }
      >
        <div className="stack">
          {formError && <p className="field__error">{formError}</p>}
          <Input id="brand-name" label="Name" required value={name} onChange={(event) => setName(event.target.value)} />
          <Input
            id="brand-description"
            label="Description"
            value={description}
            onChange={(event) => setDescription(event.target.value)}
          />
          <Checkbox id="brand-active" label="Active" checked={isActive} onChange={(event) => setIsActive(event.target.checked)} />
        </div>
      </Modal>
    </Card>
  );
}

function UnitsPanel({ canWrite }: { canWrite: boolean }) {
  const toast = useToast();
  const { items, error, reload } = useReferenceList<Unit>(getUnits);
  const [editing, setEditing] = useState<Unit | 'new' | null>(null);
  const [code, setCode] = useState('');
  const [name, setName] = useState('');
  const [isActive, setIsActive] = useState(true);
  const [isSaving, setIsSaving] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);

  const open = (unit: Unit | 'new') => {
    setEditing(unit);
    setFormError(null);
    setCode(unit === 'new' ? '' : unit.code);
    setName(unit === 'new' ? '' : unit.name);
    setIsActive(unit === 'new' ? true : unit.active);
  };

  const save = async () => {
    if (!code.trim() || !name.trim()) {
      setFormError('A unit needs both a short code and a name.');
      return;
    }
    setIsSaving(true);
    setFormError(null);
    try {
      const body = { code: code.trim(), name: name.trim(), isActive };
      if (editing === 'new') {
        await createUnit(body);
        toast.success('Unit created.');
      } else if (editing) {
        await updateUnit(editing.id, body);
        toast.success('Unit saved.');
      }
      setEditing(null);
      await reload();
    } catch (caught) {
      setFormError(errorMessage(caught));
    } finally {
      setIsSaving(false);
    }
  };

  if (error) return <ErrorState message={error} onRetry={() => void reload()} />;
  if (items === null) return <LoadingState label="Loading units…" />;

  return (
    <Card flush>
      <CardHeader
        title="Units of measure"
        actions={canWrite ? <Button icon="plus" size="sm" onClick={() => open('new')}>Add unit</Button> : undefined}
      />
      {items.length === 0 ? (
        <EmptyState
          icon="box"
          title="No units yet"
          body="Units describe how a product is counted — each, kilogram, litre. They are optional."
          action={canWrite ? { label: 'Add unit', onClick: () => open('new') } : undefined}
        />
      ) : (
        <Table>
          <Thead>
            <Tr>
              <Th>Code</Th>
              <Th>Name</Th>
              <Th>Status</Th>
              {canWrite && <Th className="table__actions">Actions</Th>}
            </Tr>
          </Thead>
          <Tbody>
            {items.map((unit) => (
              <Tr key={unit.id}>
                <Td>
                  <span className="mono">{unit.code}</span>
                </Td>
                <Td className="table__primary">{unit.name}</Td>
                <Td>
                  <ActiveBadge active={unit.active} />
                </Td>
                {canWrite && (
                  <Td className="table__actions">
                    <Button variant="secondary" size="sm" onClick={() => open(unit)}>
                      Edit
                    </Button>
                  </Td>
                )}
              </Tr>
            ))}
          </Tbody>
        </Table>
      )}

      <Modal
        open={editing !== null}
        onClose={() => setEditing(null)}
        title={editing === 'new' ? 'Add unit' : 'Edit unit'}
        busy={isSaving}
        footer={
          <>
            <Button variant="secondary" onClick={() => setEditing(null)} disabled={isSaving}>
              Cancel
            </Button>
            <Button onClick={() => void save()} isLoading={isSaving}>
              Save
            </Button>
          </>
        }
      >
        <div className="stack">
          {formError && <p className="field__error">{formError}</p>}
          <Input
            id="unit-code"
            label="Code"
            required
            value={code}
            hint="Short form shown on receipts and forms, such as EA or KG."
            onChange={(event) => setCode(event.target.value)}
          />
          <Input id="unit-name" label="Name" required value={name} onChange={(event) => setName(event.target.value)} />
          <Checkbox id="unit-active" label="Active" checked={isActive} onChange={(event) => setIsActive(event.target.checked)} />
        </div>
      </Modal>
    </Card>
  );
}

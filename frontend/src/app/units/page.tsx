"use client";

import React, { useState, useEffect } from 'react';
import { useAuth } from '@/features/auth/AuthContext';
import { getUnits, createUnit, updateUnit, ApiError } from '@/lib/api/catalog';
import { Unit, UnitRequest } from '@/types/catalog';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Checkbox } from '@/components/ui/Checkbox';
import { Badge } from '@/components/ui/Badge';
import { Table, Thead, Tbody, Tr, Th, Td } from '@/components/ui/Table';

export default function UnitsPage() {
  const { user } = useAuth();
  const hasWritePermission = user?.permissions?.includes('PRODUCT_WRITE');

  const [units, setUnits] = useState<Unit[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  
  const [isFormOpen, setIsFormOpen] = useState(false);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [formData, setFormData] = useState<UnitRequest>({ name: '', abbreviation: '', allowFractions: false, isActive: true });
  const [formError, setFormError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  useEffect(() => {
    fetchUnits();
  }, []);

  const fetchUnits = async () => {
    try {
      setIsLoading(true);
      setError(null);
      const data = await getUnits();
      setUnits(data);
    } catch (err: any) {
      setError(err.message || 'Failed to load units');
    } finally {
      setIsLoading(false);
    }
  };

  const handleOpenCreate = () => {
    setEditingId(null);
    setFormData({ name: '', abbreviation: '', allowFractions: false, isActive: true });
    setFormError(null);
    setIsFormOpen(true);
  };

  const handleOpenEdit = (unit: Unit) => {
    setEditingId(unit.id);
    setFormData({
      name: unit.name,
      abbreviation: unit.abbreviation,
      allowFractions: unit.allowFractions,
      isActive: unit.active
    });
    setFormError(null);
    setIsFormOpen(true);
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setFormError(null);
    setIsSubmitting(true);

    try {
      const request: UnitRequest = {
        name: formData.name,
        abbreviation: formData.abbreviation,
        allowFractions: formData.allowFractions,
        isActive: formData.isActive
      };

      if (editingId) {
        await updateUnit(editingId, request);
      } else {
        await createUnit(request);
      }
      setIsFormOpen(false);
      fetchUnits();
    } catch (err: any) {
      setFormError(err.message || 'Validation failed');
    } finally {
      setIsSubmitting(false);
    }
  };

  if (isLoading) return <div style={{ padding: 'var(--space-6)' }}>Loading units...</div>;
  if (error) return <div style={{ padding: 'var(--space-6)', color: 'var(--color-error)' }}>{error}</div>;

  return (
    <div style={{ padding: 'var(--space-6)', maxWidth: 'var(--layout-max-width)', margin: '0 auto' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 'var(--space-6)' }}>
        <h1 style={{ fontSize: 'var(--font-size-heading)' }}>Units of Measure</h1>
        {hasWritePermission && (
          <Button onClick={handleOpenCreate}>Create Unit</Button>
        )}
      </div>

      {isFormOpen && (
        <div style={{
          backgroundColor: 'var(--color-surface)',
          padding: 'var(--space-6)',
          borderRadius: 'var(--radius-lg)',
          boxShadow: 'var(--shadow-md)',
          marginBottom: 'var(--space-6)'
        }}>
          <h2 style={{ marginBottom: 'var(--space-4)' }}>{editingId ? 'Edit Unit' : 'New Unit'}</h2>
          
          {formError && (
            <div style={{
              backgroundColor: 'var(--color-error-surface)',
              color: 'var(--color-error)',
              padding: 'var(--space-3)',
              borderRadius: 'var(--radius-sm)',
              marginBottom: 'var(--space-4)'
            }}>{formError}</div>
          )}

          <form onSubmit={handleSubmit}>
            <Input 
              id="name"
              label="Unit Name" 
              value={formData.name} 
              onChange={e => setFormData({ ...formData, name: e.target.value })} 
              required 
            />
            <Input 
              id="abbreviation"
              label="Abbreviation" 
              value={formData.abbreviation} 
              onChange={e => setFormData({ ...formData, abbreviation: e.target.value })} 
              required 
            />
            <Checkbox 
              id="allowFractions"
              label="Allow Fractions (e.g. 1.5 kg)"
              checked={formData.allowFractions}
              onChange={e => setFormData({ ...formData, allowFractions: e.target.checked })}
            />
            {editingId && (
              <Checkbox 
                id="isActive"
                label="Active"
                checked={formData.isActive}
                onChange={e => setFormData({ ...formData, isActive: e.target.checked })}
              />
            )}
            
            <div style={{ display: 'flex', gap: 'var(--space-4)', marginTop: 'var(--space-6)' }}>
              <Button type="button" variant="secondary" onClick={() => setIsFormOpen(false)} disabled={isSubmitting}>Cancel</Button>
              <Button type="submit" isLoading={isSubmitting}>Save</Button>
            </div>
          </form>
        </div>
      )}

      {units.length === 0 ? (
        <div style={{ padding: 'var(--space-8)', textAlign: 'center', backgroundColor: 'var(--color-surface-sunken)', borderRadius: 'var(--radius-md)' }}>
          No units found.
        </div>
      ) : (
        <Table>
          <Thead>
            <Tr>
              <Th>Name</Th>
              <Th>Abbreviation</Th>
              <Th>Allow Fractions</Th>
              <Th>Status</Th>
              <Th style={{ textAlign: 'right' }}>Actions</Th>
            </Tr>
          </Thead>
          <Tbody>
            {units.map(unit => (
              <Tr key={unit.id}>
                <Td style={{ fontWeight: 'var(--font-weight-medium)' }}>{unit.name}</Td>
                <Td>{unit.abbreviation}</Td>
                <Td>{unit.allowFractions ? 'Yes' : 'No'}</Td>
                <Td>
                  {unit.active ? <Badge variant="success">Active</Badge> : <Badge variant="error">Inactive</Badge>}
                </Td>
                <Td style={{ textAlign: 'right' }}>
                  {hasWritePermission && (
                    <Button variant="secondary" onClick={() => handleOpenEdit(unit)}>Edit</Button>
                  )}
                </Td>
              </Tr>
            ))}
          </Tbody>
        </Table>
      )}
    </div>
  );
}

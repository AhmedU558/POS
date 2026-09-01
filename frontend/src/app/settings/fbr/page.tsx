'use client';

import { useState, useEffect } from 'react';
import { useAuth } from '@/features/auth/AuthContext';
import { useStoreContext } from '@/features/session/StoreContext';
import { P, hasPermission } from '@/lib/permissions';
import { get, put } from '@/lib/api/http';
import { PageHeader } from '@/components/ui/PageHeader';
import { Card } from '@/components/ui/Card';
import { Input, Select } from '@/components/ui/Field';
import { Button } from '@/components/ui/Button';
import { Badge } from '@/components/ui/Badge';
import { Icon } from '@/components/ui/Icon';
import { PermissionRequired } from '@/components/ui/States';
import { useToast } from '@/components/ui/Toast';

interface FbrConfigResponse {
  enabled: boolean;
  environment: string;
  ntn: string;
  strn: string;
  posId: string;
  hasSecret: boolean;
}

export default function FbrSettingsPage() {
  const { user } = useAuth();
  const { activeStore } = useStoreContext();
  const canManage = hasPermission(user?.permissions, P.STORE_WRITE);
  const toast = useToast();

  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [enabled, setEnabled] = useState(false);
  const [environment, setEnvironment] = useState('sandbox');
  const [ntn, setNtn] = useState('');
  const [strn, setStrn] = useState('');
  const [posId, setPosId] = useState('');
  const [secret, setSecret] = useState('');
  const [hasSecret, setHasSecret] = useState(false);

  useEffect(() => {
    async function loadConfig() {
      if (!activeStore) return;
      try {
        setLoading(true);
        const data = await get<FbrConfigResponse>(`/stores/${activeStore.id}/fbr-config`);
        setEnabled(data.enabled);
        setEnvironment(data.environment || 'sandbox');
        setNtn(data.ntn || '');
        setStrn(data.strn || '');
        setPosId(data.posId || '');
        setHasSecret(data.hasSecret);
      } catch {
        toast.error('Failed to load FBR configuration');
      } finally {
        setLoading(false);
      }
    }
    if (canManage && activeStore) {
      loadConfig();
    }
  }, [activeStore, canManage, toast]);

  const saveConfig = async () => {
    if (!activeStore) return;
    try {
      setSaving(true);
      const data = await put<{hasSecret: boolean}>(`/stores/${activeStore.id}/fbr-config`, {
        enabled,
        environment,
        ntn,
        strn,
        posId,
        secret
      });
      setHasSecret(data.hasSecret);
      setSecret(''); // Clear secret input after save
      toast.success('FBR configuration saved');
    } catch (err: unknown) {
      if (err instanceof Error) {
        toast.error(err.message || 'Failed to save configuration');
      } else {
        toast.error('Failed to save configuration');
      }
    } finally {
      setSaving(false);
    }
  };

  if (!canManage) {
    return (
      <div className="page">
        <PermissionRequired permission={P.STORE_WRITE} action="Managing FBR integration" />
      </div>
    );
  }

  return (
    <div className="page">
      <PageHeader
        title="FBR Integration"
        description="Federal Board of Revenue POS integration for Pakistan tax compliance."
      />

      {loading ? (
        <div style={{ padding: '2rem', textAlign: 'center' }}>Loading configuration...</div>
      ) : (
        <div className="stack-lg">
          {/* ---- Connection status ---- */}
          <Card>
            <div className="card__header">
              <h2 className="card__title">Connection Status</h2>
            </div>
            <div className="card__body stack">
              <div className="row row-between">
                <span>Integration Status</span>
                <Badge variant={enabled ? "success" : "pending"}>
                  <Icon name={enabled ? "check" : "info"} size={14} /> {enabled ? "Configured" : "Not Connected"}
                </Badge>
              </div>
              <div className="row row-between">
                <span>Store</span>
                <span className="text-muted">{activeStore?.name ?? '—'}</span>
              </div>
              
              <div
                style={{
                  padding: 'var(--space-3)',
                  background: 'var(--color-warning-surface)',
                  borderRadius: 'var(--radius-md)',
                  fontSize: 'var(--font-size-small)',
                  color: 'var(--color-warning)',
                  marginTop: '1rem'
                }}
              >
                <strong>Note:</strong> FBR integration requires a licensed POS integrator. The backend integration
                adapter is ready, but live integration cannot be fully verified because valid FBR/licensed-integrator credentials are not configured in this environment.
              </div>
            </div>
          </Card>

          {/* ---- Configuration ---- */}
          <Card>
            <div className="card__header">
              <h2 className="card__title">Configuration</h2>
            </div>
            <div className="card__body stack">
              <div className="row row-between" style={{ marginBottom: '1rem' }}>
                <label htmlFor="fbr-enabled" style={{ fontWeight: 'var(--font-weight-medium)' }}>
                  Enable FBR Integration
                </label>
                <input
                  id="fbr-enabled"
                  type="checkbox"
                  checked={enabled}
                  onChange={(event) => setEnabled(event.target.checked)}
                />
              </div>

              <Select
                id="fbr-environment"
                label="Environment"
                value={environment}
                onChange={(event) => setEnvironment(event.target.value)}
                hint="Use Sandbox for testing. Switch to Production only with a verified integrator."
                options={[
                  { label: 'Sandbox (Testing)', value: 'sandbox' },
                  { label: 'Production', value: 'production' }
                ]}
                placeholder={null}
              />

              <div className="form-grid form-grid--2">
                <Input
                  id="fbr-ntn"
                  label="NTN (National Tax Number)"
                  value={ntn}
                  placeholder="1234567-8"
                  onChange={(event) => setNtn(event.target.value)}
                />
                <Input
                  id="fbr-strn"
                  label="STRN (Sales Tax Registration)"
                  value={strn}
                  placeholder="12-34-5678-901-23"
                  onChange={(event) => setStrn(event.target.value)}
                  hint="Required for sales tax registered businesses."
                />
              </div>

              <div className="form-grid form-grid--2">
                <Input
                  id="fbr-pos-id"
                  label="POS ID"
                  value={posId}
                  placeholder="Assigned by FBR or integrator"
                  onChange={(event) => setPosId(event.target.value)}
                  hint="The unique POS terminal identifier registered with FBR."
                />
                <Input
                  id="fbr-secret"
                  label="Provider API Secret"
                  type="password"
                  value={secret}
                  placeholder={hasSecret ? '••••••••' : 'Enter API secret'}
                  onChange={(event) => setSecret(event.target.value)}
                  hint="Credentials are encrypted before storage."
                />
              </div>

              <div className="row" style={{ marginTop: 'var(--space-2)' }}>
                <Button variant="primary" onClick={saveConfig} disabled={saving}>
                  {saving ? 'Saving...' : 'Save Configuration'}
                </Button>
                <Button variant="secondary" disabled>
                  Test Connection
                </Button>
              </div>
            </div>
          </Card>
        </div>
      )}
    </div>
  );
}

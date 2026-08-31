'use client';

import { useState } from 'react';
import { useAuth } from '@/features/auth/AuthContext';
import { useStoreContext } from '@/features/session/StoreContext';
import { P, hasPermission } from '@/lib/permissions';
import { PageHeader } from '@/components/ui/PageHeader';
import { Card } from '@/components/ui/Card';
import { Input, Select } from '@/components/ui/Field';
import { Button } from '@/components/ui/Button';
import { Badge } from '@/components/ui/Badge';
import { Icon } from '@/components/ui/Icon';
import { PermissionRequired } from '@/components/ui/States';

/**
 * FBR Integration settings.
 *
 * This page provides the configuration UI for the Federal Board of Revenue (FBR) POS
 * integration required in Pakistan. All FBR communication happens server-side.
 *
 * When no backend FBR service is connected, the page shows placeholder configuration
 * fields and a clear "not connected" status.
 */
export default function FbrSettingsPage() {
  const { user } = useAuth();
  const { activeStore } = useStoreContext();
  const canManage = hasPermission(user?.permissions, P.STORE_WRITE);

  const [enabled, setEnabled] = useState(false);
  const [environment, setEnvironment] = useState('sandbox');
  const [ntn, setNtn] = useState('');
  const [strn, setStrn] = useState('');
  const [posId, setPosId] = useState('');

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

      <div className="stack-lg">
        {/* ---- Connection status ---- */}
        <Card>
          <div className="card__header">
            <h2 className="card__title">Connection Status</h2>
          </div>
          <div className="card__body stack">
            <div className="row row-between">
              <span>Integration Status</span>
              <Badge variant="pending">
                <Icon name="info" size={14} /> Not Connected
              </Badge>
            </div>
            <div className="row row-between">
              <span>Store</span>
              <span className="text-muted">{activeStore?.name ?? '—'}</span>
            </div>
            <div className="row row-between">
              <span>Last Successful Sync</span>
              <span className="text-muted">—</span>
            </div>
            <div className="row row-between">
              <span>Failed Invoices</span>
              <span className="text-muted">0</span>
            </div>

            <div
              style={{
                padding: 'var(--space-3)',
                background: 'var(--color-warning-surface)',
                borderRadius: 'var(--radius-md)',
                fontSize: 'var(--font-size-small)',
                color: 'var(--color-warning)',
              }}
            >
              <strong>Note:</strong> FBR integration requires a licensed POS integrator. Configure your
              integrator credentials in the backend environment variables. Never expose API tokens or
              secrets in the frontend.
            </div>
          </div>
        </Card>

        {/* ---- Configuration ---- */}
        <Card>
          <div className="card__header">
            <h2 className="card__title">Configuration</h2>
          </div>
          <div className="card__body stack">
            <div className="row row-between">
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

            <Input
              id="fbr-pos-id"
              label="POS ID"
              value={posId}
              placeholder="Assigned by FBR or integrator"
              onChange={(event) => setPosId(event.target.value)}
              hint="The unique POS terminal identifier registered with FBR."
            />

            <div className="row" style={{ marginTop: 'var(--space-2)' }}>
              <Button variant="primary" disabled>
                Save Configuration
              </Button>
              <Button variant="secondary" disabled>
                Test Connection
              </Button>
            </div>

            <p className="text-small text-muted">
              Configuration saving is not yet connected to a backend service. Once the FBR integration
              backend is implemented, this form will persist settings and enable connection testing.
            </p>
          </div>
        </Card>

        {/* ---- Integrator info ---- */}
        <Card>
          <div className="card__header">
            <h2 className="card__title">About FBR Integration</h2>
          </div>
          <div className="card__body stack">
            <p className="text-small">
              The Federal Board of Revenue requires all POS systems in Pakistan to report sales
              transactions in real-time. This integration submits each completed sale to FBR and
              receives a verification invoice number and QR code that must be printed on the receipt.
            </p>
            <p className="text-small">
              <strong>Requirements:</strong>
            </p>
            <ul className="text-small" style={{ paddingLeft: 'var(--space-4)', lineHeight: '1.8' }}>
              <li>Valid NTN and registered POS with FBR</li>
              <li>Licensed POS integrator credentials (configured server-side)</li>
              <li>Internet connectivity for real-time reporting</li>
              <li>QR code printed on every receipt for customer verification</li>
            </ul>
          </div>
        </Card>
      </div>
    </div>
  );
}

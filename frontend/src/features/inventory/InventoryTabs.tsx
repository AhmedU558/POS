'use client';

import Link from 'next/link';
import { P, hasPermission } from '@/lib/permissions';

/**
 * One navigation strip across the inventory area.
 *
 * The previous screens each carried their own row of links to the others, which meant no screen
 * agreed with any other about where you were.
 */
export function InventoryTabs({
  active,
  permissions,
}: {
  active: 'stock' | 'batches' | 'alerts';
  permissions: string[] | undefined;
}) {
  if (!hasPermission(permissions, P.INVENTORY_READ)) {
    return null;
  }

  const tabs = [
    { key: 'stock', label: 'Stock on hand', href: '/inventory' },
    { key: 'batches', label: 'Batches & expiry', href: '/inventory/batches' },
    { key: 'alerts', label: 'Alerts', href: '/inventory/alerts' },
  ] as const;

  return (
    <nav className="tabs" aria-label="Inventory sections">
      {tabs.map((tab) => (
        <Link key={tab.key} href={tab.href} className="tab" aria-current={tab.key === active ? 'page' : undefined}>
          {tab.label}
        </Link>
      ))}
    </nav>
  );
}

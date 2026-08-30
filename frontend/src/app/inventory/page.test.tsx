import { describe, it, expect, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import InventoryOverviewPage from './page';

vi.mock('@/lib/api/inventory', () => ({
  inventoryApi: {
    getBalances: vi.fn().mockResolvedValue({
      content: [
        { productId: '1', productName: 'Apple', sku: 'APL', storeName: 'Main Store', quantity: 15, lastUpdatedAt: '2026-08-29T10:00:00Z' }
      ]
    })
  }
}));

vi.mock('@/features/auth/AuthContext', () => ({
  useAuth: () => ({
    user: { storeIds: ['store-1'], permissions: ['INVENTORY_READ', 'INVENTORY_RECEIVE'] }
  })
}));

describe('InventoryOverviewPage', () => {
  it('renders the overview table with data', async () => {
    render(<InventoryOverviewPage />);

    expect(screen.getByText('Inventory Overview')).toBeTruthy();
    expect(screen.getByText('Receive Stock')).toBeTruthy();

    await waitFor(() => {
      expect(screen.getByText('Apple')).toBeTruthy();
      expect(screen.getByText('APL')).toBeTruthy();
      expect(screen.getByText('15')).toBeTruthy();
    });
  });
});
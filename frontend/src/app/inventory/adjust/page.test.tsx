import { describe, it, expect, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import StockAdjustmentPage from './page';

vi.mock('@/lib/api/inventory', () => ({
  inventoryApi: {
    adjustStock: vi.fn().mockResolvedValue({ productId: '1', quantity: 10 })
  }
}));

vi.mock('@/lib/api/catalog', () => ({
  getProducts: vi.fn().mockResolvedValue([
    { id: '1', name: 'Apple', sku: 'APL' }
  ])
}));

vi.mock('@/features/auth/AuthContext', () => ({
  useAuth: () => ({
    user: { storeIds: ['store-1'] }
  })
}));

vi.mock('next/navigation', () => ({
  useRouter: () => ({
    push: vi.fn(),
    back: vi.fn()
  })
}));

describe('StockAdjustmentPage', () => {
  it('renders the adjustment form with product dropdown', async () => {
    render(<StockAdjustmentPage />);

    expect(screen.getByText('Adjust Stock')).toBeTruthy();

    await waitFor(() => {
      expect(screen.getByText('Apple (APL)')).toBeTruthy();
    });

    expect(screen.getByText('Submit Adjustment')).toBeTruthy();
    expect(screen.getByText('Cancel')).toBeTruthy();
  });
});

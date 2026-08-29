
import { render, screen, waitFor, cleanup } from '@testing-library/react';
import { vi, describe, it, expect, beforeEach, afterEach } from 'vitest';
import ProductsPage from './page';
import { useAuth } from '@/features/auth/AuthContext';
import { getProducts } from '@/lib/api/catalog';
import { useRouter } from 'next/navigation';

vi.mock('@/features/auth/AuthContext');
vi.mock('@/lib/api/catalog');
vi.mock('next/navigation');

describe('ProductsPage', () => {
  const mockRouter = { push: vi.fn() };
  
  beforeEach(() => {
    vi.mocked(useRouter).mockReturnValue(mockRouter as any);
    vi.mocked(getProducts).mockResolvedValue([]);
  });

  afterEach(() => {
    cleanup();
    vi.resetAllMocks();
  });

  it('renders products and allows creation when user has PRODUCT_WRITE', async () => {
    vi.mocked(useAuth).mockReturnValue({ user: { permissions: ['PRODUCT_WRITE'] } } as any);
    vi.mocked(getProducts).mockResolvedValue([{
      id: 'p1', sku: 'SKU1', name: 'Product 1', description: null, categoryId: null, brandId: null, unitId: null,
      purchasePrice: 10, sellingPrice: 20, wholesalePrice: null, taxRate: 0, minStock: 5, maxStock: null,
      trackBatch: false, trackExpiry: false, active: true, createdAt: '', updatedAt: ''
    } as any]);

    render(<ProductsPage />);
    
    expect(screen.getByText('Loading products...')).toBeTruthy();
    
    await waitFor(() => {
      expect(screen.getByText('SKU1')).toBeTruthy();
      expect(screen.getByText('Product 1')).toBeTruthy();
      expect(screen.getByText('Create Product')).toBeTruthy();
      expect(screen.getByText('Manage')).toBeTruthy();
    });
  });

  it('hides create button when user lacks PRODUCT_WRITE', async () => {
    vi.mocked(useAuth).mockReturnValue({ user: { permissions: ['PRODUCT_READ'] } } as any);
    vi.mocked(getProducts).mockResolvedValue([{
      id: 'p1', sku: 'SKU1', name: 'Product 1', description: null, categoryId: null, brandId: null, unitId: null,
      purchasePrice: 10, sellingPrice: 20, wholesalePrice: null, taxRate: 0, minStock: 5, maxStock: null,
      trackBatch: false, trackExpiry: false, active: true, createdAt: '', updatedAt: ''
    } as any]);

    render(<ProductsPage />);
    
    await waitFor(() => {
      expect(screen.getByText('SKU1')).toBeTruthy();
      expect(screen.queryByText('Create Product')).toBeNull();
      expect(screen.getByText('View')).toBeTruthy();
    });
  });
});

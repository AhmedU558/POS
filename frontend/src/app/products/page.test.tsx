import { cleanup, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import ProductsPage from './page';
import { useAuth } from '@/features/auth/AuthContext';
import { getCategories, searchProducts } from '@/lib/api/catalog';
import type { Product } from '@/types/catalog';

vi.mock('@/features/auth/AuthContext');
vi.mock('@/lib/api/catalog');
vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: vi.fn() }),
  usePathname: () => '/products',
}));

function product(overrides: Partial<Product> = {}): Product {
  return {
    id: 'p1',
    sku: 'SKU-1',
    name: 'Blue Widget',
    description: null,
    categoryId: null,
    brandId: null,
    unitId: null,
    purchasePrice: 10,
    sellingPrice: 20,
    wholesalePrice: null,
    taxRate: 0,
    minStock: 5,
    maxStock: null,
    trackBatch: false,
    trackExpiry: false,
    isActive: true,
    createdAt: '',
    updatedAt: '',
    ...overrides,
  };
}

function pageOf(products: Product[]) {
  return { content: products, totalElements: products.length, totalPages: 1, size: 20, number: 0 };
}

describe('ProductsPage', () => {
  beforeEach(() => {
    vi.mocked(getCategories).mockResolvedValue([]);
  });

  afterEach(() => {
    cleanup();
    vi.resetAllMocks();
  });

  it('renders products from the paged envelope the API returns', async () => {
    // The list previously typed this response as an array and threw on .map.
    vi.mocked(useAuth).mockReturnValue({ user: { permissions: ['PRODUCT_READ'] } } as never);
    vi.mocked(searchProducts).mockResolvedValue(pageOf([product()]));

    render(<ProductsPage />);

    await waitFor(() => {
      expect(screen.getByText('Blue Widget')).toBeTruthy();
      expect(screen.getByText('SKU-1')).toBeTruthy();
    });
  });

  it('reads the active flag from isActive, not active', async () => {
    /*
     * The backend serialises the record component `isActive`. Reading `active` yielded undefined,
     * so every product rendered as inactive.
     */
    vi.mocked(useAuth).mockReturnValue({ user: { permissions: ['PRODUCT_READ'] } } as never);
    vi.mocked(searchProducts).mockResolvedValue(
      pageOf([product({ id: 'a', name: 'Live Item', isActive: true }), product({ id: 'b', sku: 'SKU-2', name: 'Dead Item', isActive: false })])
    );

    render(<ProductsPage />);

    await waitFor(() => {
      expect(screen.getByText('Live Item')).toBeTruthy();
    });
    // "Active" also appears as a status filter option, so match the badges specifically.
    const badges = document.querySelectorAll('.badge');
    const labels = Array.from(badges).map((badge) => badge.textContent);
    expect(labels).toContain('Active');
    expect(labels).toContain('Inactive');
  });

  it('offers a way to add a product when the catalogue is empty and the user may write', async () => {
    vi.mocked(useAuth).mockReturnValue({ user: { permissions: ['PRODUCT_READ', 'PRODUCT_WRITE'] } } as never);
    vi.mocked(searchProducts).mockResolvedValue(pageOf([]));

    render(<ProductsPage />);

    await waitFor(() => {
      expect(screen.getByText('No products yet')).toBeTruthy();
    });
    expect(screen.getAllByText('Add product').length).toBeGreaterThan(0);
  });

  it('does not offer creation to a read-only user', async () => {
    vi.mocked(useAuth).mockReturnValue({ user: { permissions: ['PRODUCT_READ'] } } as never);
    vi.mocked(searchProducts).mockResolvedValue(pageOf([]));

    render(<ProductsPage />);

    await waitFor(() => {
      expect(screen.getByText('No products yet')).toBeTruthy();
    });
    expect(screen.queryByText('Add product')).toBeNull();
  });

  it('explains itself instead of rendering blank without PRODUCT_READ', async () => {
    vi.mocked(useAuth).mockReturnValue({ user: { permissions: [] } } as never);

    render(<ProductsPage />);

    expect(screen.getByText('You do not have access')).toBeTruthy();
    expect(searchProducts).not.toHaveBeenCalled();
  });
});

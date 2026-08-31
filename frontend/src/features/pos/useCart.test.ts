import { act, renderHook } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { useCart } from './useCart';
import type { Product } from '@/types/catalog';

function product(overrides: Partial<Product> = {}): Product {
  return {
    id: 'p1',
    sku: 'SKU-1',
    name: 'Widget',
    description: null,
    categoryId: null,
    brandId: null,
    unitId: null,
    purchasePrice: 4,
    sellingPrice: 10,
    wholesalePrice: null,
    taxRate: 0.1,
    minStock: 0,
    maxStock: null,
    trackBatch: false,
    trackExpiry: false,
    isActive: true,
    createdAt: '',
    updatedAt: '',
    ...overrides,
  };
}

describe('useCart', () => {
  it('merges a rescan of the same product into one line', () => {
    const { result } = renderHook(() => useCart());

    act(() => {
      result.current.add(product());
      result.current.add(product());
    });

    expect(result.current.lines).toHaveLength(1);
    expect(result.current.lines[0].quantity).toBe(2);
  });

  it('estimates tax on the discounted line, not the gross line', () => {
    const { result } = renderHook(() => useCart());

    act(() => {
      result.current.add(product(), 2);
    });
    act(() => {
      result.current.setDiscount('p1', 5);
    });

    // 2 x 10 = 20, less 5 = 15, tax at 10% = 1.50, total 16.50
    expect(result.current.estimate.subtotal).toBe(20);
    expect(result.current.estimate.discountTotal).toBe(5);
    expect(result.current.estimate.taxTotal).toBe(1.5);
    expect(result.current.estimate.grandTotal).toBe(16.5);
  });

  it('removes a line when its quantity is stepped down to zero', () => {
    const { result } = renderHook(() => useCart());

    act(() => {
      result.current.add(product());
    });
    act(() => {
      result.current.setQuantity('p1', 0);
    });

    expect(result.current.lines).toHaveLength(0);
  });

  it('omits discountAmount entirely when no manual discount was given', () => {
    /*
     * Sending 0 counts as "a manual discount was applied" server-side and suppresses promotions
     * for the line, so the field has to be absent rather than zero.
     */
    const { result } = renderHook(() => useCart());

    act(() => {
      result.current.add(product());
    });

    const [item] = result.current.toItemRequests();
    expect(item).toEqual({ productId: 'p1', quantity: 1 });
    expect('discountAmount' in item).toBe(false);
  });

  it('sends discountAmount when the cashier applied one', () => {
    const { result } = renderHook(() => useCart());

    act(() => {
      result.current.add(product());
    });
    act(() => {
      result.current.setDiscount('p1', 2.5);
    });

    expect(result.current.toItemRequests()[0]).toEqual({ productId: 'p1', quantity: 1, discountAmount: 2.5 });
  });

  it('never lets a discount exceed the line it applies to', () => {
    const { result } = renderHook(() => useCart());

    act(() => {
      result.current.add(product());
    });
    act(() => {
      result.current.setDiscount('p1', 999);
    });

    expect(result.current.estimate.discountTotal).toBe(10);
    expect(result.current.estimate.grandTotal).toBe(0);
  });
});

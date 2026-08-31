'use client';

import { useCallback, useMemo, useState } from 'react';
import { Product } from '@/types/catalog';
import { SaleItemRequest } from '@/lib/api/sales';
import { toNumber } from '@/lib/format';

export interface CartLine {
  productId: string;
  sku: string;
  name: string;
  unitPrice: number;
  taxRate: number;
  quantity: number;
  /** Manual line discount. Requires SALE_DISCOUNT server-side. */
  discount: number;
}

/**
 * The till's basket.
 *
 * The totals here are an estimate for the cashier's benefit while they build the sale. They are
 * not what anybody pays: the server prices the sale, applies promotions and computes tax, and the
 * payment step tenders against that authoritative figure (see the checkout screen).
 */
export function useCart() {
  const [lines, setLines] = useState<CartLine[]>([]);

  const add = useCallback((product: Product, quantity = 1) => {
    setLines((current) => {
      const existing = current.find((line) => line.productId === product.id);
      if (existing) {
        return current.map((line) =>
          line.productId === product.id ? { ...line, quantity: round4(line.quantity + quantity) } : line
        );
      }
      return [
        ...current,
        {
          productId: product.id,
          sku: product.sku,
          name: product.name,
          unitPrice: toNumber(product.sellingPrice),
          taxRate: toNumber(product.taxRate),
          quantity,
          discount: 0,
        },
      ];
    });
  }, []);

  const setQuantity = useCallback((productId: string, quantity: number) => {
    setLines((current) =>
      quantity <= 0
        ? current.filter((line) => line.productId !== productId)
        : current.map((line) => (line.productId === productId ? { ...line, quantity: round4(quantity) } : line))
    );
  }, []);

  const setDiscount = useCallback((productId: string, discount: number) => {
    setLines((current) =>
      current.map((line) => (line.productId === productId ? { ...line, discount: Math.max(0, discount) } : line))
    );
  }, []);

  const remove = useCallback((productId: string) => {
    setLines((current) => current.filter((line) => line.productId !== productId));
  }, []);

  const clear = useCallback(() => setLines([]), []);

  const estimate = useMemo(() => {
    let subtotal = 0;
    let discountTotal = 0;
    let taxTotal = 0;
    for (const line of lines) {
      const lineSubtotal = line.unitPrice * line.quantity;
      const lineDiscount = Math.min(line.discount, lineSubtotal);
      subtotal += lineSubtotal;
      discountTotal += lineDiscount;
      taxTotal += (lineSubtotal - lineDiscount) * line.taxRate;
    }
    return {
      subtotal: round2(subtotal),
      discountTotal: round2(discountTotal),
      taxTotal: round2(taxTotal),
      grandTotal: round2(subtotal - discountTotal + taxTotal),
      itemCount: round4(lines.reduce((total, line) => total + line.quantity, 0)),
    };
  }, [lines]);

  const toItemRequests = useCallback(
    (): SaleItemRequest[] =>
      lines.map((line) => ({
        productId: line.productId,
        quantity: line.quantity,
        // Sending 0 would count as "a manual discount was applied" and suppress promotions.
        ...(line.discount > 0 ? { discountAmount: line.discount } : {}),
      })),
    [lines]
  );

  return { lines, add, setQuantity, setDiscount, remove, clear, estimate, toItemRequests };
}

function round2(value: number): number {
  return Math.round(value * 100) / 100;
}

function round4(value: number): number {
  return Math.round(value * 10000) / 10000;
}

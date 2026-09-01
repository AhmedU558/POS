import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it } from 'vitest';
import { ThermalReceiptDocument } from './ThermalReceiptDocument';
import type { SaleReceipt } from '@/lib/api/sales';

function receipt(overrides: Partial<SaleReceipt> = {}): SaleReceipt {
  return {
    saleId: 'sale-1',
    receiptNumber: 'R-2026-000123',
    createdAt: '2026-09-01T00:35:00Z',
    storeName: 'Demo Store',
    storeAddress: 'Lahore, Pakistan',
    storeContact: '+92 300 0000000',
    terminalName: 'Front Counter',
    cashierName: 'Demo Administrator',
    customerName: 'Walk-in Customer',
    status: 'COMPLETED',
    subtotal: 1925,
    discountTotal: 0,
    taxTotal: 0,
    grandTotal: 1925,
    tenderedAmount: 2000,
    changeAmount: 75,
    fbrStatus: 'NOT_CONFIGURED',
    fbrStatusLabel: 'Not configured',
    fbrInvoiceNumber: null,
    fbrQrCode: null,
    payments: [{ paymentMethod: 'CASH', amount: 1925 }],
    items: [
      {
        productId: 'p1',
        sku: 'SKU-1',
        name: 'Super Kernel Rice 5kg',
        quantity: 1,
        unitPrice: 1750,
        discountAmount: 0,
        taxAmount: 0,
        lineTotal: 1750,
      },
      {
        productId: 'p2',
        sku: 'SKU-2',
        name: '7UP 1.5L',
        quantity: 1,
        unitPrice: 140,
        discountAmount: 0,
        taxAmount: 0,
        lineTotal: 140,
      },
    ],
    ...overrides,
  };
}

describe('ThermalReceiptDocument', () => {
  afterEach(() => {
    cleanup();
  });

  it('renders a standalone receipt with the exact branding line', () => {
    render(<ThermalReceiptDocument receipt={receipt()} paper="80mm" />);

    expect(screen.getByText('Demo Store')).toBeTruthy();
    expect(screen.getByText(/Front Counter/i)).toBeTruthy();
    expect(screen.getByText('Powered by Aqvion Labs.com')).toBeTruthy();
    expect(screen.queryByText('Cart')).toBeNull();
    expect(screen.queryByText('Add customer')).toBeNull();
    expect(screen.queryByText('Cart is empty')).toBeNull();
  });

  it('does not render an FBR QR or invoice number before verified FBR success', () => {
    render(<ThermalReceiptDocument receipt={receipt()} paper="58mm" />);

    expect(screen.getByText('Not configured')).toBeTruthy();
    expect(screen.queryByAltText('FBR verification QR code')).toBeNull();
    expect(screen.queryByText(/FBR Invoice/i)).toBeNull();
  });

  it('renders the real FBR block only when the backend provides verified data', () => {
    render(
      <ThermalReceiptDocument
        receipt={receipt({
          fbrStatus: 'SUBMISSION_SUCCESS',
          fbrStatusLabel: 'Submission successful',
          fbrInvoiceNumber: '1234567890123',
          fbrQrCode: 'data:image/png;base64,abc123',
        })}
        paper="58mm"
      />
    );

    expect(screen.getByText(/FBR Invoice No:/i)).toBeTruthy();
    expect(screen.getByText('1234567890123')).toBeTruthy();
    expect(screen.getByAltText('FBR verification QR code')).toBeTruthy();
  });
});

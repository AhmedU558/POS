'use client';

import { useEffect, useState } from 'react';
import { notFound, useSearchParams, useParams } from 'next/navigation';
import { SaleReceipt, salesApi } from '@/lib/api/sales';
import { ThermalReceiptDocument } from '@/features/pos/ThermalReceiptDocument';


export default function ReceiptPage() {
  const params = useParams();
  const saleId = params?.saleId as string;
  const [receipt, setReceipt] = useState<SaleReceipt | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  
  const searchParams = useSearchParams();
  const paper = (searchParams.get('paper') || '80mm') as '58mm' | '80mm';

  useEffect(() => {
    async function loadReceipt() {
      if (!saleId) return;
      try {
        setLoading(true);
        const data = await salesApi.receipt(saleId);
        setReceipt(data);
      } catch (err: any) {
        if (err.status === 404) {
          notFound();
        } else {
          setError(err.message || 'Failed to load receipt');
        }
      } finally {
        setLoading(false);
      }
    }
    loadReceipt();
  }, [saleId]);

  if (loading) {
    return <div style={{ padding: '2rem', textAlign: 'center' }}>Loading receipt...</div>;
  }

  if (error) {
    return (
      <div style={{ padding: '2rem' }}>
        <div style={{ padding: '1rem', background: '#fee2e2', color: '#991b1b', borderRadius: '4px' }}>
          <strong>Error loading receipt:</strong> {error}
        </div>
      </div>
    );
  }

  if (!receipt) {
    return null;
  }

  // Handle auto-print safely
  useEffect(() => {
    if (receipt && searchParams.get('print') === 'true') {
      const timer = setTimeout(() => {
        window.print();
      }, 500);
      return () => clearTimeout(timer);
    }
  }, [receipt, searchParams]);

  return (
    <div className="receipt-page">
      <ThermalReceiptDocument receipt={receipt} paper={paper} />
    </div>
  );
}

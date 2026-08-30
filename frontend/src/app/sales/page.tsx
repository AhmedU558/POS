'use client';

import { FormEvent, useState } from 'react';
import { useAuth } from '@/features/auth/AuthContext';
import { getProducts } from '@/lib/api/catalog';
import { salesApi, Sale } from '@/lib/api/sales';
import { Product } from '@/types/catalog';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Table, Thead, Tbody, Tr, Th, Td } from '@/components/ui/Table';

interface CartLine {
  productId: string;
  sku: string;
  name: string;
  quantity: string;
}

export default function PosCheckoutPage() {
  const { user } = useAuth();
  const canCreate = user?.permissions?.includes('SALE_CREATE') ?? false;

  const [query, setQuery] = useState('');
  const [results, setResults] = useState<Product[]>([]);
  const [cart, setCart] = useState<CartLine[]>([]);
  const [storeId, setStoreId] = useState(user?.storeIds?.[0] ?? '');
  const [terminalId, setTerminalId] = useState('');
  const [registerId, setRegisterId] = useState('');
  const [registerSessionId, setRegisterSessionId] = useState('');
  const [paymentMethodId, setPaymentMethodId] = useState('');
  const [customerId, setCustomerId] = useState('');
  const [completed, setCompleted] = useState<Sale | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [isSearching, setIsSearching] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);

  if (!canCreate) {
    return (
      <div style={{ padding: 'var(--space-6)' }}>
        <h1>POS Checkout</h1>
        <p role="status">Access is restricted. You do not have permission to complete sales.</p>
      </div>
    );
  }

  const search = async () => {
    setIsSearching(true);
    setError(null);
    try {
      const products = await getProducts({ query: query || undefined, isActive: true, size: 25 });
      const list = Array.isArray(products) ? products : ((products as { content?: Product[] }).content ?? []);
      setResults(list);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to search products');
    } finally {
      setIsSearching(false);
    }
  };

  const addToCart = (product: Product) => {
    setCompleted(null);
    setCart((current) => {
      const existing = current.find((line) => line.productId === product.id);
      if (existing) {
        return current.map((line) =>
          line.productId === product.id
            ? { ...line, quantity: String(Number(line.quantity) + 1) }
            : line
        );
      }
      return [...current, { productId: product.id, sku: product.sku, name: product.name, quantity: '1' }];
    });
  };

  const onComplete = async (event: FormEvent) => {
    event.preventDefault();
    setIsSubmitting(true);
    setError(null);
    try {
      const sale = await salesApi.create(
        {
          storeId,
          terminalId,
          registerId,
          registerSessionId,
          customerId: customerId || null,
          items: cart.map((line) => ({ productId: line.productId, quantity: Number(line.quantity) })),
          payments: [{ paymentMethodId, amount: 0 }],
        },
        crypto.randomUUID()
      );
      setCompleted(sale);
      setCart([]);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to complete sale');
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div style={{ padding: 'var(--space-6)', maxWidth: 'var(--layout-max-width)', margin: '0 auto' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', gap: 'var(--space-4)', marginBottom: 'var(--space-4)' }}>
        <h1>POS Checkout</h1>
        <p>Register session {registerSessionId || 'not set'}</p>
      </div>

      {error && (
        <div role="alert" style={{ marginBottom: 'var(--space-4)', padding: 'var(--space-4)', background: 'var(--color-error-surface)', color: 'var(--color-error)', borderRadius: 'var(--radius-md)' }}>
          {error}
        </div>
      )}

      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 'var(--space-6)' }}>
        <section>
          <Input id="pos-search" label="Product / barcode" value={query} onChange={(e) => setQuery(e.target.value)} />
          <Button type="button" onClick={() => void search()} isLoading={isSearching} disabled={isSearching}>Search</Button>
          {results.map((product) => (
            <Button key={product.id} type="button" variant="secondary" onClick={() => addToCart(product)} style={{ display: 'block', marginTop: 'var(--space-2)' }}>
              {product.sku} — {product.name}
            </Button>
          ))}
        </section>

        <section>
          <h2 style={{ fontSize: 'var(--font-size-heading-sm)' }}>Current cart</h2>
          {cart.length === 0 ? (
            <div style={{ padding: 'var(--space-6)', textAlign: 'center', backgroundColor: 'var(--color-surface-sunken)', borderRadius: 'var(--radius-md)' }}>
              Cart is empty.
            </div>
          ) : (
            <Table>
              <Thead>
                <Tr>
                  <Th>Item</Th>
                  <Th>Qty</Th>
                  <Th> </Th>
                </Tr>
              </Thead>
              <Tbody>
                {cart.map((line) => (
                  <Tr key={line.productId}>
                    <Td>{line.sku} — {line.name}</Td>
                    <Td>
                      <Input
                        id={'qty-' + line.productId}
                        label="Quantity"
                        type="number"
                        min="0.0001"
                        step="any"
                        value={line.quantity}
                        onChange={(e) => setCart((current) =>
                          current.map((item) => item.productId === line.productId ? { ...item, quantity: e.target.value } : item)
                        )}
                      />
                    </Td>
                    <Td>
                      <Button type="button" variant="secondary" onClick={() => setCart((current) => current.filter((item) => item.productId !== line.productId))}>
                        Remove
                      </Button>
                    </Td>
                  </Tr>
                ))}
              </Tbody>
            </Table>
          )}

          <form onSubmit={onComplete}>
            <Input id="pos-store" label="Store" value={storeId} onChange={(e) => setStoreId(e.target.value)} required />
            <Input id="pos-terminal" label="Terminal" value={terminalId} onChange={(e) => setTerminalId(e.target.value)} required />
            <Input id="pos-register" label="Register" value={registerId} onChange={(e) => setRegisterId(e.target.value)} required />
            <Input id="pos-session" label="Register session" value={registerSessionId} onChange={(e) => setRegisterSessionId(e.target.value)} required />
            <Input id="pos-customer" label="Customer" value={customerId} onChange={(e) => setCustomerId(e.target.value)} />
            <Input id="pos-method" label="Cash payment method" value={paymentMethodId} onChange={(e) => setPaymentMethodId(e.target.value)} required />
            <Button type="submit" isLoading={isSubmitting} disabled={isSubmitting || cart.length === 0}>
              Complete sale
            </Button>
          </form>
        </section>
      </div>

      {completed && (
        <section style={{ marginTop: 'var(--space-8)' }}>
          <h2 style={{ fontSize: 'var(--font-size-heading-sm)' }}>Receipt {completed.receiptNumber}</h2>
          <p>Subtotal: {completed.subtotal}</p>
          <p>Discount: {completed.discountTotal}</p>
          <p>Tax: {completed.taxTotal}</p>
          <p>Total: {completed.grandTotal}</p>
        </section>
      )}
    </div>
  );
}

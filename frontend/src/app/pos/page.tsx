'use client';

import { useCallback, useEffect, useRef, useState } from 'react';
import Link from 'next/link';
import { useAuth } from '@/features/auth/AuthContext';
import { useStoreContext } from '@/features/session/StoreContext';
import { getCategories, searchProducts } from '@/lib/api/catalog';
import { Category, Product } from '@/types/catalog';
import { Customer } from '@/lib/api/customers';
import {
  PaymentMethod,
  Sale,
  SalePaymentRequest,
  SaleReceipt,
  paymentMethodsApi,
  salesApi,
} from '@/lib/api/sales';
import { ApiError } from '@/lib/api/http';
import { useDebounced } from '@/hooks/useDebounced';
import { errorMessage, formatMoney, formatQuantity, initials } from '@/lib/format';
import { P, hasPermission } from '@/lib/permissions';
import { Button } from '@/components/ui/Button';
import { SearchInput } from '@/components/ui/Field';
import { Icon } from '@/components/ui/Icon';
import { Badge } from '@/components/ui/Badge';
import { ConfirmDialog } from '@/components/ui/Modal';
import { Alert, EmptyState, LoadingState, PermissionRequired } from '@/components/ui/States';
import { useToast } from '@/components/ui/Toast';
import { useCart } from '@/features/pos/useCart';
import { PaymentDialog } from '@/features/pos/PaymentDialog';
import { CustomerPicker } from '@/features/pos/CustomerPicker';
import { ReceiptDialog } from '@/features/pos/ReceiptDialog';

/**
 * The till.
 *
 * Full screen, two panes, scan-first (UI/UX Specification 6.1). The cashier never types an
 * identifier: store, terminal, register and session all come from the open register session.
 *
 * Settlement is deliberately two steps. Pressing Pay creates the sale with no payments, which the
 * server prices — applying promotions and tax — and returns as HELD. The payment dialog then
 * tenders against that authoritative total, and the sale is settled by resuming it. Tendering
 * against the till's own running estimate would let a promotion make the change wrong.
 */
export default function PointOfSalePage() {
  const { user } = useAuth();
  const { session, activeStore, isLoading: contextLoading, refresh } = useStoreContext();
  const toast = useToast();
  const cart = useCart();

  const canSell = hasPermission(user?.permissions, P.SALE_CREATE);
  const canDiscount = hasPermission(user?.permissions, P.SALE_DISCOUNT);

  const [term, setTerm] = useState('');
  const [categoryId, setCategoryId] = useState('');
  const [categories, setCategories] = useState<Category[]>([]);
  const [results, setResults] = useState<Product[]>([]);
  const [isSearching, setIsSearching] = useState(false);
  const [searchError, setSearchError] = useState<string | null>(null);

  const [customer, setCustomer] = useState<Customer | null>(null);
  const [methods, setMethods] = useState<PaymentMethod[]>([]);

  const [pricedSale, setPricedSale] = useState<Sale | null>(null);
  const [isPricing, setIsPricing] = useState(false);
  const [paymentError, setPaymentError] = useState<string | null>(null);
  const [isSettling, setIsSettling] = useState(false);

  const [receipt, setReceipt] = useState<SaleReceipt | null>(null);
  const [changeDue, setChangeDue] = useState<number | null>(null);

  const [customerOpen, setCustomerOpen] = useState(false);
  const [clearPrompt, setClearPrompt] = useState(false);

  const scanRef = useRef<HTMLInputElement>(null);
  const debouncedTerm = useDebounced(term, 200);

  const focusScan = useCallback(() => {
    scanRef.current?.focus();
    scanRef.current?.select();
  }, []);

  useEffect(() => {
    if (!canSell) return;
    paymentMethodsApi
      .list()
      .then((list) => setMethods(list.filter((method) => method.active)))
      .catch(() => setMethods([]));
    getCategories()
      .then((all) => setCategories(all.filter((category) => category.active)))
      .catch(() => setCategories([]));
  }, [canSell]);

  // Search results double as the product grid: no term means "show me what we sell".
  useEffect(() => {
    if (!canSell) return;
    let cancelled = false;
    setIsSearching(true);
    setSearchError(null);
    searchProducts({
      query: debouncedTerm || undefined,
      categoryId: categoryId || undefined,
      isActive: true,
      size: 48,
      sort: 'name,asc',
    })
      .then((page) => {
        if (!cancelled) setResults(page.content);
      })
      .catch((caught) => {
        if (!cancelled) setSearchError(errorMessage(caught));
      })
      .finally(() => {
        if (!cancelled) setIsSearching(false);
      });
    return () => {
      cancelled = true;
    };
  }, [canSell, debouncedTerm, categoryId]);

  /*
   * A scanner types the barcode and presses Enter. When exactly one active product matches, it
   * goes straight into the cart and the field clears, ready for the next item — the cashier never
   * takes a hand off the scanner.
   */
  const onScanSubmit = useCallback(async () => {
    const scanned = term.trim();
    if (!scanned) return;
    try {
      const page = await searchProducts({ query: scanned, isActive: true, size: 5 });
      if (page.content.length === 1) {
        cart.add(page.content[0]);
        setTerm('');
        focusScan();
      } else if (page.content.length === 0) {
        toast.error(`Nothing found for "${scanned}".`);
      }
      // Several matches: leave them on screen for the cashier to pick from.
    } catch (caught) {
      toast.error(errorMessage(caught));
    }
  }, [term, cart, focusScan, toast]);

  // F2 returns to the scan field and F4 takes payment, from anywhere on the screen.
  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'F2') {
        event.preventDefault();
        focusScan();
      }
      if (event.key === 'F4' && cart.lines.length > 0 && !pricedSale) {
        event.preventDefault();
        void startPayment();
      }
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  });

  const startPayment = async () => {
    if (!session || cart.lines.length === 0) return;
    setIsPricing(true);
    setPaymentError(null);
    try {
      const held = await salesApi.create(
        {
          storeId: session.storeId,
          terminalId: session.terminalId,
          registerId: session.registerId,
          registerSessionId: session.id,
          customerId: customer?.id ?? null,
          items: cart.toItemRequests(),
        },
        crypto.randomUUID()
      );
      setPricedSale(held);
    } catch (caught) {
      toast.error(errorMessage(caught));
    } finally {
      setIsPricing(false);
    }
  };

  const settle = async (payments: SalePaymentRequest[], cashTendered: number | null) => {
    if (!pricedSale || !session) return;
    setIsSettling(true);
    setPaymentError(null);
    try {
      const completed = await salesApi.resume(pricedSale.id, { registerSessionId: session.id, payments });
      const cashPaid = payments
        .filter((payment) => methods.find((method) => method.id === payment.paymentMethodId)?.code === 'CASH')
        .reduce((sum, payment) => sum + payment.amount, 0);

      setChangeDue(cashTendered === null ? null : Math.max(0, cashTendered - cashPaid));
      const settledReceipt = await salesApi.receipt(completed.id).catch(() => null);
      setReceipt(settledReceipt);
      // RECEIPT_READ is a separate permission, so confirm the sale even when the receipt is denied.
      if (!settledReceipt) {
        toast.success(`Sale ${completed.receiptNumber} completed for ${formatMoney(completed.grandTotal)}.`);
        focusScan();
      }
      setPricedSale(null);
      cart.clear();
      setCustomer(null);
      setTerm('');
    } catch (caught) {
      setPaymentError(errorMessage(caught));
      if (caught instanceof ApiError && caught.code === 'REGISTER_SESSION_REQUIRED') {
        await refresh();
      }
    } finally {
      setIsSettling(false);
    }
  };

  /*
   * Backing out of payment leaves the priced sale parked rather than deleting it: the API has no
   * void, and a held sale is recoverable from Held sales. The cart is kept so the cashier can
   * carry on, and abandoning it costs at most one parked receipt.
   */
  const cancelPayment = () => {
    setPricedSale(null);
    setPaymentError(null);
    toast.info('Payment cancelled. The sale is parked under Held sales.');
    focusScan();
  };

  const hold = async () => {
    if (!session || cart.lines.length === 0) return;
    setIsPricing(true);
    try {
      const held = await salesApi.create(
        {
          storeId: session.storeId,
          terminalId: session.terminalId,
          registerId: session.registerId,
          registerSessionId: session.id,
          customerId: customer?.id ?? null,
          items: cart.toItemRequests(),
        },
        crypto.randomUUID()
      );
      toast.success(`Sale ${held.receiptNumber} held. Resume it from Held sales.`);
      cart.clear();
      setCustomer(null);
      setTerm('');
      focusScan();
    } catch (caught) {
      toast.error(errorMessage(caught));
    } finally {
      setIsPricing(false);
    }
  };

  if (!canSell) {
    return (
      <div className="page">
        <PermissionRequired permission={P.SALE_CREATE} action="Selling at the till" />
      </div>
    );
  }

  if (contextLoading) {
    return <LoadingState label="Opening the till…" />;
  }

  if (!session) {
    return <NoOpenRegister />;
  }

  return (
    <main className="pos">
      <header className="pos__topbar">
        <div className="row">
          <Link href="/" className="btn btn--ghost btn--sm" aria-label="Leave the till">
            <Icon name="arrow-left" size={18} />
            Exit
          </Link>
          <span className="context-chip">
            <Icon name="store" size={16} />
            <span className="context-chip__value">{activeStore?.name ?? 'Store'}</span>
          </span>
          <Badge variant="success">Till open</Badge>
        </div>
        <div className="row">
          <span className="text-small text-muted">F2 search · F4 pay</span>
          <span className="user-menu__avatar" title={`${user?.firstName} ${user?.lastName}`}>
            {initials(user?.firstName, user?.lastName)}
          </span>
        </div>
      </header>

      <div className="pos__body">
        <section className="pos__catalog" aria-label="Products">
          <form
            className="pos__scan"
            onSubmit={(event) => {
              event.preventDefault();
              void onScanSubmit();
            }}
          >
            <SearchInput
              id="pos-scan"
              ref={scanRef}
              placeholder="Scan a barcode, or search by name or SKU"
              value={term}
              onChange={(event) => setTerm(event.target.value)}
              autoFocus
              autoComplete="off"
            />
          </form>

          {categories.length > 0 && (
            <div className="pos__categories" role="group" aria-label="Categories">
              <button
                type="button"
                className="pos__category"
                aria-pressed={categoryId === ''}
                onClick={() => setCategoryId('')}
              >
                All
              </button>
              {categories.map((category) => (
                <button
                  key={category.id}
                  type="button"
                  className="pos__category"
                  aria-pressed={categoryId === category.id}
                  onClick={() => setCategoryId(category.id)}
                >
                  {category.name}
                </button>
              ))}
            </div>
          )}

          <div className="pos__grid">
            {searchError ? (
              <Alert tone="error">{searchError}</Alert>
            ) : isSearching && results.length === 0 ? (
              <LoadingState label="Loading products…" />
            ) : results.length === 0 ? (
              <EmptyState
                icon="search"
                title={term ? 'Nothing matches that' : 'No products available'}
                body={
                  term
                    ? 'Check the barcode or try part of the product name.'
                    : 'Add products from the Products screen before selling.'
                }
              />
            ) : (
              results.map((product) => (
                <button
                  key={product.id}
                  type="button"
                  className="product-tile"
                  onClick={() => {
                    cart.add(product);
                    focusScan();
                  }}
                >
                  <span className="product-tile__name">{product.name}</span>
                  <span className="product-tile__sku">{product.sku}</span>
                  <span className="product-tile__price">{formatMoney(product.sellingPrice)}</span>
                </button>
              ))
            )}
          </div>
        </section>

        <section className="pos__cart" aria-label="Current sale">
          <header className="pos__cart-header">
            <span className="pos__cart-title">
              Cart {cart.lines.length > 0 && <span className="text-muted">({formatQuantity(cart.estimate.itemCount)})</span>}
            </span>
            {cart.lines.length > 0 && (
              <Button variant="ghost" size="sm" icon="trash" onClick={() => setClearPrompt(true)}>
                Clear
              </Button>
            )}
          </header>

          <div className="pos__cart-lines">
            {cart.lines.length === 0 ? (
              <EmptyState
                icon="pos"
                title="Cart is empty"
                body="Scan an item or tap a product to start the sale."
              />
            ) : (
              cart.lines.map((line) => (
                <div className="cart-line" key={line.productId}>
                  <div>
                    <p className="cart-line__name">{line.name}</p>
                    <p className="cart-line__meta">
                      {formatMoney(line.unitPrice)} each
                      {line.discount > 0 && ` · less ${formatMoney(line.discount)}`}
                    </p>
                  </div>
                  <span className="cart-line__total">
                    {formatMoney(line.unitPrice * line.quantity - line.discount)}
                  </span>
                  <div className="cart-line__controls">
                    <div className="qty-stepper">
                      <button
                        type="button"
                        className="qty-stepper__button"
                        onClick={() => cart.setQuantity(line.productId, line.quantity - 1)}
                        aria-label={`Reduce quantity of ${line.name}`}
                      >
                        <Icon name="minus" size={16} />
                      </button>
                      <input
                        className="qty-stepper__input"
                        type="number"
                        min="0"
                        step="any"
                        value={line.quantity}
                        aria-label={`Quantity of ${line.name}`}
                        onChange={(event) => cart.setQuantity(line.productId, Number(event.target.value))}
                      />
                      <button
                        type="button"
                        className="qty-stepper__button"
                        onClick={() => cart.setQuantity(line.productId, line.quantity + 1)}
                        aria-label={`Increase quantity of ${line.name}`}
                      >
                        <Icon name="plus" size={16} />
                      </button>
                    </div>
                    {canDiscount && (
                      <input
                        className="control"
                        style={{ width: '7rem', height: '2.25rem', minHeight: 0 }}
                        type="number"
                        min="0"
                        step="0.01"
                        placeholder="Discount"
                        value={line.discount || ''}
                        aria-label={`Discount on ${line.name}`}
                        onChange={(event) => cart.setDiscount(line.productId, Number(event.target.value))}
                      />
                    )}
                    <Button
                      variant="ghost"
                      size="sm"
                      icon="trash"
                      aria-label={`Remove ${line.name}`}
                      onClick={() => cart.remove(line.productId)}
                    />
                  </div>
                </div>
              ))
            )}
          </div>

          <div className="pos__totals">
            <button type="button" className="btn btn--secondary btn--block" onClick={() => setCustomerOpen(true)}>
              <Icon name="customers" size={16} />
              {customer ? customer.name : 'Add customer (optional)'}
            </button>

            <div className="total-row">
              <span className="total-row__label">Subtotal</span>
              <span className="total-row__value">{formatMoney(cart.estimate.subtotal)}</span>
            </div>
            {cart.estimate.discountTotal > 0 && (
              <div className="total-row">
                <span className="total-row__label">Discount</span>
                <span className="total-row__value">−{formatMoney(cart.estimate.discountTotal)}</span>
              </div>
            )}
            <div className="total-row">
              <span className="total-row__label">Tax</span>
              <span className="total-row__value">{formatMoney(cart.estimate.taxTotal)}</span>
            </div>
            <div className="total-row total-row--grand">
              <span className="total-row__label">Total</span>
              <span className="total-row__value">{formatMoney(cart.estimate.grandTotal)}</span>
            </div>

            <div className="pos__actions">
              <Button
                variant="primary"
                size="lg"
                className="btn--pay"
                disabled={cart.lines.length === 0}
                isLoading={isPricing}
                onClick={() => void startPayment()}
              >
                Pay {cart.lines.length > 0 && formatMoney(cart.estimate.grandTotal)}
              </Button>
              <Button variant="secondary" disabled={cart.lines.length === 0 || isPricing} onClick={() => void hold()}>
                Hold
              </Button>
              <Link className="btn btn--secondary" href="/sales/held">
                Held sales
              </Link>
            </div>
          </div>
        </section>
      </div>

      <PaymentDialog
        open={pricedSale !== null}
        total={pricedSale?.grandTotal ?? 0}
        methods={methods}
        hasCustomer={customer !== null}
        isSubmitting={isSettling}
        error={paymentError}
        onCancel={cancelPayment}
        onConfirm={(payments, cashTendered) => void settle(payments, cashTendered)}
      />

      <CustomerPicker open={customerOpen} onClose={() => setCustomerOpen(false)} onSelect={setCustomer} />

      <ReceiptDialog
        receipt={receipt}
        changeDue={changeDue}
        onClose={() => {
          setReceipt(null);
          setChangeDue(null);
        }}
        onNextSale={() => {
          setReceipt(null);
          setChangeDue(null);
          focusScan();
        }}
      />

      <ConfirmDialog
        open={clearPrompt}
        title="Clear the cart?"
        description="Every item will be removed. This cannot be undone."
        confirmLabel="Clear cart"
        destructive
        onCancel={() => setClearPrompt(false)}
        onConfirm={() => {
          cart.clear();
          setCustomer(null);
          setClearPrompt(false);
          focusScan();
        }}
      />
    </main>
  );
}

/** The one thing standing between a cashier and a sale, said plainly with the way out. */
function NoOpenRegister() {
  return (
    <div className="page page-narrow">
      <div className="empty-state">
        <span className="empty-state__icon">
          <Icon name="register" size={40} />
        </span>
        <h1 className="empty-state__title">No till is open</h1>
        <p className="empty-state__body">
          A register has to be open before you can take payment. Opening one records the cash you start the shift with,
          so the drawer can be counted at the end.
        </p>
        <Link className="btn btn--primary btn--lg" href="/register">
          Open a register
        </Link>
      </div>
    </div>
  );
}

import { cleanup, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { PaymentDialog } from './PaymentDialog';
import type { PaymentMethod } from '@/lib/api/sales';

const METHODS: PaymentMethod[] = [
  { id: 'cash', code: 'CASH', name: 'Cash', type: 'CASH', active: true },
  { id: 'card', code: 'CARD', name: 'Card', type: 'CARD', active: true },
  { id: 'credit', code: 'STORE_CREDIT', name: 'Store credit', type: 'CREDIT', active: true },
];

function setup(props: Partial<React.ComponentProps<typeof PaymentDialog>> = {}) {
  const onConfirm = vi.fn();
  const onCancel = vi.fn();
  render(
    <PaymentDialog
      open
      total={18.5}
      methods={METHODS}
      hasCustomer={false}
      isSubmitting={false}
      error={null}
      onCancel={onCancel}
      onConfirm={onConfirm}
      {...props}
    />
  );
  return { onConfirm, onCancel };
}

describe('PaymentDialog', () => {
  afterEach(() => {
    cleanup();
    vi.resetAllMocks();
  });

  it('shows the amount due from the priced sale', () => {
    setup();
    expect(screen.getAllByText('$18.50').length).toBeGreaterThan(0);
  });

  it('works out change from cash tendered', async () => {
    const user = userEvent.setup();
    setup();

    await user.type(screen.getByLabelText('Cash received'), '20');

    expect(screen.getByText('Change due')).toBeTruthy();
    expect(screen.getByText('$1.50')).toBeTruthy();
  });

  it('refuses to complete when the cash tendered is short', async () => {
    const user = userEvent.setup();
    const { onConfirm } = setup();

    await user.type(screen.getByLabelText('Cash received'), '10');

    expect(screen.getByText('Still owing')).toBeTruthy();
    await user.click(screen.getByRole('button', { name: 'Complete sale' }));
    expect(onConfirm).not.toHaveBeenCalled();
  });

  it('books a cash payment at the sale total, and reports the tender separately for change', async () => {
    /*
     * The server records a lone CASH payment at the sale total whatever amount is sent, so the
     * payment must carry the total and the tendered figure must travel beside it.
     */
    const user = userEvent.setup();
    const { onConfirm } = setup();

    await user.type(screen.getByLabelText('Cash received'), '20');
    await user.click(screen.getByRole('button', { name: 'Complete sale' }));

    expect(onConfirm).toHaveBeenCalledWith([{ paymentMethodId: 'cash', amount: 18.5 }], 20);
  });

  it('treats a blank tender as exact money', async () => {
    const user = userEvent.setup();
    const { onConfirm } = setup();

    await user.click(screen.getByRole('button', { name: 'Complete sale' }));

    expect(onConfirm).toHaveBeenCalledWith([{ paymentMethodId: 'cash', amount: 18.5 }], 18.5);
  });

  it('blocks store credit until the sale has a customer', async () => {
    const user = userEvent.setup();
    const { onConfirm } = setup({ hasCustomer: false });

    await user.click(screen.getByRole('button', { name: 'Store credit' }));

    expect(screen.getByText(/Store credit needs a customer/)).toBeTruthy();
    await user.click(screen.getByRole('button', { name: 'Complete sale' }));
    expect(onConfirm).not.toHaveBeenCalled();
  });

  it('will not submit a split whose amounts do not add up to the total', async () => {
    const user = userEvent.setup();
    const { onConfirm } = setup();

    await user.click(screen.getByRole('button', { name: 'Split across methods' }));
    const first = screen.getByLabelText('Amount', { selector: '#split-amount-0' });
    await user.clear(first);
    await user.type(first, '5');

    expect(screen.getByText('$13.50 still to allocate')).toBeTruthy();
    await user.click(screen.getByRole('button', { name: 'Complete sale' }));
    expect(onConfirm).not.toHaveBeenCalled();
  });

  it('submits a split once the amounts balance', async () => {
    const user = userEvent.setup();
    const { onConfirm } = setup();

    await user.click(screen.getByRole('button', { name: 'Split across methods' }));
    const first = screen.getByLabelText('Amount', { selector: '#split-amount-0' });
    await user.clear(first);
    await user.type(first, '10');
    const second = screen.getByLabelText('Amount', { selector: '#split-amount-1' });
    await user.clear(second);
    await user.type(second, '8.50');

    await user.click(screen.getByRole('button', { name: 'Complete sale' }));

    expect(onConfirm).toHaveBeenCalledWith(
      [
        { paymentMethodId: 'cash', amount: 10 },
        { paymentMethodId: 'card', amount: 8.5 },
      ],
      10
    );
  });
});

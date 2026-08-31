'use client';

import { useEffect, useState } from 'react';
import { Customer, customersApi } from '@/lib/api/customers';
import { useDebounced } from '@/hooks/useDebounced';
import { errorMessage, formatMoney } from '@/lib/format';
import { Modal } from '@/components/ui/Modal';
import { Button } from '@/components/ui/Button';
import { SearchInput } from '@/components/ui/Field';
import { Table, Tbody, Td, Th, Thead, Tr } from '@/components/ui/Table';
import { EmptyState, ErrorState, LoadingState } from '@/components/ui/States';

/** Attaching a customer to a sale — by name, phone or account code, never by pasting a UUID. */
export function CustomerPicker({
  open,
  onClose,
  onSelect,
}: {
  open: boolean;
  onClose: () => void;
  onSelect: (customer: Customer | null) => void;
}) {
  const [term, setTerm] = useState('');
  const [customers, setCustomers] = useState<Customer[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const debounced = useDebounced(term);

  useEffect(() => {
    if (!open) return;
    let cancelled = false;
    setError(null);
    customersApi
      .list({ query: debounced || undefined, isActive: true, size: 15 })
      .then((page) => {
        if (!cancelled) setCustomers(page.content);
      })
      .catch((caught) => {
        if (!cancelled) {
          setError(errorMessage(caught));
          setCustomers([]);
        }
      });
    return () => {
      cancelled = true;
    };
  }, [open, debounced]);

  return (
    <Modal
      open={open}
      onClose={onClose}
      title="Add a customer"
      description="Optional. Needed for store credit and for the customer's purchase history."
      wide
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>
            Cancel
          </Button>
          <Button
            variant="ghost"
            onClick={() => {
              onSelect(null);
              onClose();
            }}
          >
            Sell without a customer
          </Button>
        </>
      }
    >
      <div className="stack">
        <SearchInput
          id="pos-customer-search"
          label="Search"
          placeholder="Name, phone or customer code"
          value={term}
          onChange={(event) => setTerm(event.target.value)}
          autoFocus
        />
        {error ? (
          <ErrorState message={error} />
        ) : customers === null ? (
          <LoadingState label="Searching…" />
        ) : customers.length === 0 ? (
          <EmptyState
            icon="customers"
            title={term ? 'No customers match' : 'No customers yet'}
            body={term ? 'Try a different name or phone number.' : 'Customers can be added from the Customers screen.'}
          />
        ) : (
          <Table clickable>
            <Thead>
              <Tr>
                <Th>Customer</Th>
                <Th>Phone</Th>
                <Th className="table__num">Credit limit</Th>
                <Th className="table__actions" />
              </Tr>
            </Thead>
            <Tbody>
              {customers.map((customer) => (
                <Tr key={customer.id}>
                  <Td>
                    <span className="table__primary">{customer.name}</span>
                    <div className="table__secondary mono">{customer.customerCode}</div>
                  </Td>
                  <Td>{customer.phone ?? <span className="text-muted">—</span>}</Td>
                  <Td className="table__num">{formatMoney(customer.creditLimit)}</Td>
                  <Td className="table__actions">
                    <Button
                      size="sm"
                      onClick={() => {
                        onSelect(customer);
                        onClose();
                      }}
                    >
                      Select
                    </Button>
                  </Td>
                </Tr>
              ))}
            </Tbody>
          </Table>
        )}
      </div>
    </Modal>
  );
}

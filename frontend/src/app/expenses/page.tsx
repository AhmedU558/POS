'use client';

import { useCallback, useEffect, useState } from 'react';
import { useAuth } from '@/features/auth/AuthContext';
import { useStoreContext } from '@/features/session/StoreContext';
import { EXPENSE_CATEGORIES, Expense, expensesApi } from '@/lib/api/expenses';
import { Page, emptyPage } from '@/lib/api/http';
import { errorMessage, formatDate, formatMoney } from '@/lib/format';
import { P, hasPermission } from '@/lib/permissions';
import { PageHeader } from '@/components/ui/PageHeader';
import { Card, Metric } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Input, Select, Textarea } from '@/components/ui/Field';
import { Badge } from '@/components/ui/Badge';
import { Pagination, Table, Tbody, Td, Th, Thead, Tr } from '@/components/ui/Table';
import { Modal } from '@/components/ui/Modal';
import { Alert, EmptyState, ErrorState, PermissionRequired, TableSkeleton } from '@/components/ui/States';
import { useToast } from '@/components/ui/Toast';

const PAGE_SIZE = 20;

/** Money going out of the business that is not stock — rent, wages, utilities. */
export default function ExpensesPage() {
  const { user } = useAuth();
  const { activeStoreId, activeStore } = useStoreContext();
  const toast = useToast();

  const canRead = hasPermission(user?.permissions, P.EXPENSE_READ);
  const canWrite = hasPermission(user?.permissions, P.EXPENSE_WRITE);

  const [page, setPage] = useState(0);
  const [result, setResult] = useState<Page<Expense>>(emptyPage(PAGE_SIZE));
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [editing, setEditing] = useState<Expense | 'new' | null>(null);
  const [category, setCategory] = useState(EXPENSE_CATEGORIES[0]);
  const [amount, setAmount] = useState('');
  const [expenseDate, setExpenseDate] = useState(new Date().toISOString().slice(0, 10));
  const [description, setDescription] = useState('');
  const [formError, setFormError] = useState<string | null>(null);
  const [isSaving, setIsSaving] = useState(false);

  const load = useCallback(async () => {
    setIsLoading(true);
    setError(null);
    try {
      setResult(await expensesApi.list({ page, size: PAGE_SIZE, sort: 'expenseDate,desc' }));
    } catch (caught) {
      setError(errorMessage(caught));
    } finally {
      setIsLoading(false);
    }
  }, [page]);

  useEffect(() => {
    if (canRead) void load();
  }, [canRead, load]);

  if (!canRead) {
    return (
      <div className="page">
        <PermissionRequired permission={P.EXPENSE_READ} action="Viewing expenses" />
      </div>
    );
  }

  const open = (expense: Expense | 'new') => {
    setEditing(expense);
    setFormError(null);
    setCategory(expense === 'new' ? EXPENSE_CATEGORIES[0] : expense.category);
    setAmount(expense === 'new' ? '' : String(expense.amount));
    setExpenseDate(expense === 'new' ? new Date().toISOString().slice(0, 10) : expense.expenseDate);
    setDescription(expense === 'new' ? '' : (expense.description ?? ''));
  };

  const save = async () => {
    const value = Number(amount);
    if (!activeStoreId) {
      setFormError('No store is selected to record this against.');
      return;
    }
    if (!category.trim()) {
      setFormError('Choose a category.');
      return;
    }
    if (amount.trim() === '' || !Number.isFinite(value) || value <= 0) {
      setFormError('Enter an amount greater than zero.');
      return;
    }
    if (!expenseDate) {
      setFormError('Enter the date the money was spent.');
      return;
    }

    setIsSaving(true);
    setFormError(null);
    try {
      const body = {
        storeId: activeStoreId,
        category: category.trim(),
        amount: value,
        expenseDate,
        description: description.trim() || null,
      };
      if (editing === 'new') {
        await expensesApi.create(body);
        toast.success('Expense recorded.');
      } else if (editing) {
        await expensesApi.update(editing.id, body);
        toast.success('Expense updated.');
      }
      setEditing(null);
      await load();
    } catch (caught) {
      setFormError(errorMessage(caught));
    } finally {
      setIsSaving(false);
    }
  };

  const total = result.content.reduce((sum, expense) => sum + Number(expense.amount ?? 0), 0);

  return (
    <div className="page">
      <PageHeader
        title="Expenses"
        description={`Running costs for ${activeStore?.name ?? 'the store'} — anything you spend that is not stock. Categories feed the budget variance report, so keep them consistent.`}
        actions={
          canWrite && (
            <Button icon="plus" onClick={() => open('new')}>
              Record an expense
            </Button>
          )
        }
      />

      {result.content.length > 0 && (
        <div className="metric-grid" style={{ marginBottom: 'var(--space-6)' }}>
          <Metric label="On this page" value={formatMoney(total)} meta={`${result.content.length} expenses`} />
          <Metric label="Recorded in total" value={result.totalElements} meta="All expenses on file" />
        </div>
      )}

      <Card flush>
        {error ? (
          <ErrorState message={error} onRetry={() => void load()} />
        ) : isLoading && result.content.length === 0 ? (
          <TableSkeleton rows={5} columns={4} />
        ) : result.content.length === 0 ? (
          <EmptyState
            icon="expenses"
            title="No expenses recorded"
            body="Record what the business spends outside of stock — rent, wages, utilities — so the budget variance report has something to compare against."
            action={canWrite ? { label: 'Record an expense', onClick: () => open('new') } : undefined}
          />
        ) : (
          <>
            <Table>
              <Thead>
                <Tr>
                  <Th>Date</Th>
                  <Th>Category</Th>
                  <Th>Description</Th>
                  <Th className="table__num">Amount</Th>
                  {canWrite && <Th className="table__actions">Actions</Th>}
                </Tr>
              </Thead>
              <Tbody>
                {result.content.map((expense) => (
                  <Tr key={expense.id}>
                    <Td>{formatDate(expense.expenseDate)}</Td>
                    <Td>
                      <Badge variant="pending">{expense.category}</Badge>
                    </Td>
                    <Td>{expense.description ?? <span className="text-muted">—</span>}</Td>
                    <Td className="table__num">
                      <span className="money">{formatMoney(expense.amount)}</span>
                    </Td>
                    {canWrite && (
                      <Td className="table__actions">
                        <Button variant="secondary" size="sm" onClick={() => open(expense)}>
                          Edit
                        </Button>
                      </Td>
                    )}
                  </Tr>
                ))}
              </Tbody>
            </Table>
            <Pagination
              page={result.number}
              totalPages={result.totalPages}
              totalElements={result.totalElements}
              pageSize={result.size || PAGE_SIZE}
              onPageChange={setPage}
              isLoading={isLoading}
            />
          </>
        )}
      </Card>

      <Modal
        open={editing !== null}
        onClose={() => setEditing(null)}
        title={editing === 'new' ? 'Record an expense' : 'Edit expense'}
        busy={isSaving}
        footer={
          <>
            <Button variant="secondary" onClick={() => setEditing(null)} disabled={isSaving}>
              Cancel
            </Button>
            <Button onClick={() => void save()} isLoading={isSaving}>
              Save
            </Button>
          </>
        }
      >
        <div className="stack">
          {formError && <Alert tone="error">{formError}</Alert>}
          <div className="form-grid form-grid--2">
            <Select
              id="expense-category"
              label="Category"
              required
              placeholder={null}
              value={category}
              onChange={(event) => setCategory(event.target.value)}
              options={EXPENSE_CATEGORIES.map((option) => ({ value: option, label: option }))}
            />
            <Input
              id="expense-amount"
              label="Amount"
              required
              type="number"
              min="0"
              step="0.01"
              inputMode="decimal"
              value={amount}
              onChange={(event) => setAmount(event.target.value)}
            />
          </div>
          <Input
            id="expense-date"
            label="Date"
            required
            type="date"
            value={expenseDate}
            onChange={(event) => setExpenseDate(event.target.value)}
          />
          <Textarea
            id="expense-description"
            label="Description"
            rows={2}
            value={description}
            hint="Optional. What the money was for."
            onChange={(event) => setDescription(event.target.value)}
          />
        </div>
      </Modal>
    </div>
  );
}

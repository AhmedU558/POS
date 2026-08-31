import React from 'react';
import { Button } from './Button';
import { Icon } from './Icon';

export function Table({ children, clickable }: { children: React.ReactNode; clickable?: boolean }) {
  return (
    <div className="table-wrap">
      <table className={['table', clickable ? 'table--clickable' : ''].filter(Boolean).join(' ')}>{children}</table>
    </div>
  );
}

export function Thead({ children }: { children: React.ReactNode }) {
  return <thead>{children}</thead>;
}

export function Tbody({ children }: { children: React.ReactNode }) {
  return <tbody>{children}</tbody>;
}

export function Tr({
  children,
  onClick,
  className,
}: {
  children: React.ReactNode;
  onClick?: () => void;
  className?: string;
}) {
  return (
    <tr className={className} onClick={onClick}>
      {children}
    </tr>
  );
}

type CellProps = {
  children?: React.ReactNode;
  className?: string;
  colSpan?: number;
  scope?: 'col' | 'row';
};

export function Th({ children, className, colSpan, scope = 'col' }: CellProps) {
  return (
    <th className={className} colSpan={colSpan} scope={scope}>
      {children}
    </th>
  );
}

export function Td({ children, className, colSpan }: CellProps) {
  return (
    <td className={className} colSpan={colSpan}>
      {children}
    </td>
  );
}

export interface PaginationProps {
  page: number;
  totalPages: number;
  totalElements: number;
  pageSize: number;
  onPageChange: (page: number) => void;
  isLoading?: boolean;
}

/**
 * Page numbers are zero-based on the wire (Spring Data) and one-based on screen. The conversion
 * happens here so no screen has to remember which convention it is holding.
 */
export function Pagination({ page, totalPages, totalElements, pageSize, onPageChange, isLoading }: PaginationProps) {
  if (totalElements === 0) {
    return null;
  }
  const first = page * pageSize + 1;
  const last = Math.min((page + 1) * pageSize, totalElements);

  return (
    <div className="pagination">
      <p className="pagination__status" role="status">
        {first}–{last} of {totalElements}
      </p>
      <div className="pagination__controls">
        <Button
          variant="secondary"
          size="sm"
          onClick={() => onPageChange(page - 1)}
          disabled={page <= 0 || isLoading}
          aria-label="Previous page"
        >
          <Icon name="chevron-left" size={16} />
          Previous
        </Button>
        <span className="pagination__status">
          Page {page + 1} of {Math.max(totalPages, 1)}
        </span>
        <Button
          variant="secondary"
          size="sm"
          onClick={() => onPageChange(page + 1)}
          disabled={page + 1 >= totalPages || isLoading}
          aria-label="Next page"
        >
          Next
          <Icon name="chevron-right" size={16} />
        </Button>
      </div>
    </div>
  );
}

import React from 'react';

export function Table({ children }: { children: React.ReactNode }) {
  return (
    <div style={{ overflowX: 'auto', width: '100%' }}>
      <table style={{
        width: '100%',
        borderCollapse: 'collapse',
        textAlign: 'left',
        fontSize: 'var(--font-size-body)'
      }}>
        {children}
      </table>
    </div>
  );
}

export function Thead({ children }: { children: React.ReactNode }) {
  return (
    <thead style={{
      borderBottom: '2px solid var(--color-border)',
      backgroundColor: 'var(--color-surface-raised)'
    }}>
      {children}
    </thead>
  );
}

export function Tbody({ children }: { children: React.ReactNode }) {
  return <tbody>{children}</tbody>;
}

export function Tr({ children }: { children: React.ReactNode }) {
  return (
    <tr style={{
      borderBottom: '1px solid var(--color-border)',
      height: 'var(--table-row-height)'
    }}>
      {children}
    </tr>
  );
}

export function Th({ children, style }: { children: React.ReactNode, style?: React.CSSProperties }) {
  return (
    <th style={{
      padding: 'var(--space-2) var(--space-3)',
      fontWeight: 'var(--font-weight-semibold)',
      color: 'var(--color-foreground-muted)',
      ...style
    }}>
      {children}
    </th>
  );
}

export function Td({ children, style }: { children: React.ReactNode, style?: React.CSSProperties }) {
  return (
    <td style={{
      padding: 'var(--space-2) var(--space-3)',
      ...style
    }}>
      {children}
    </td>
  );
}

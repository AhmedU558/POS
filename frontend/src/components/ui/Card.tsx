import React from 'react';

export function Card({
  children,
  flush,
  className,
}: {
  children: React.ReactNode;
  /** Removes body padding so a table can sit flush inside the card. */
  flush?: boolean;
  className?: string;
}) {
  return <section className={['card', flush ? 'card--flush' : '', className ?? ''].filter(Boolean).join(' ')}>{children}</section>;
}

export function CardHeader({ title, actions }: { title: React.ReactNode; actions?: React.ReactNode }) {
  return (
    <header className="card__header">
      <h2 className="card__title">{title}</h2>
      {actions && <div className="row">{actions}</div>}
    </header>
  );
}

export function CardBody({ children, className }: { children: React.ReactNode; className?: string }) {
  return <div className={['card__body', className ?? ''].filter(Boolean).join(' ')}>{children}</div>;
}

export function CardFooter({ children }: { children: React.ReactNode }) {
  return <footer className="card__footer">{children}</footer>;
}

export function Metric({
  label,
  value,
  meta,
}: {
  label: string;
  value: React.ReactNode;
  meta?: React.ReactNode;
}) {
  return (
    <div className="metric">
      <span className="metric__label">{label}</span>
      <span className="metric__value">{value}</span>
      {meta && <span className="metric__meta">{meta}</span>}
    </div>
  );
}

export function DetailList({ children }: { children: React.ReactNode }) {
  return <dl className="detail-list">{children}</dl>;
}

export function DetailItem({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="detail-list__item">
      <dt className="detail-list__label">{label}</dt>
      <dd className="detail-list__value">{children}</dd>
    </div>
  );
}

'use client';

import { useEffect, useRef, useState } from 'react';
import { searchProducts } from '@/lib/api/catalog';
import { Product } from '@/types/catalog';
import { useDebounced } from '@/hooks/useDebounced';
import { errorMessage, formatMoney } from '@/lib/format';
import { Field } from '@/components/ui/Field';
import { Icon } from '@/components/ui/Icon';
import { Button } from '@/components/ui/Button';

/**
 * Picks a product by typing or scanning.
 *
 * The screens that need one — receiving, adjustments, purchase orders — previously offered a
 * dropdown of the first fifty products, which stops working the moment a shop has fifty-one.
 */
export function ProductPicker({
  id,
  label = 'Product',
  hint,
  required,
  selected,
  onSelect,
  disabled,
  error,
  autoFocus,
}: {
  id: string;
  label?: string;
  hint?: string;
  required?: boolean;
  selected: Product | null;
  onSelect: (product: Product | null) => void;
  disabled?: boolean;
  error?: string;
  autoFocus?: boolean;
}) {
  const [term, setTerm] = useState('');
  const [results, setResults] = useState<Product[]>([]);
  const [isOpen, setIsOpen] = useState(false);
  const [searchError, setSearchError] = useState<string | null>(null);
  const containerRef = useRef<HTMLDivElement>(null);
  const debounced = useDebounced(term, 250);

  useEffect(() => {
    if (!isOpen) return;
    let cancelled = false;
    searchProducts({ query: debounced || undefined, isActive: true, size: 10, sort: 'name,asc' })
      .then((page) => {
        if (!cancelled) {
          setResults(page.content);
          setSearchError(null);
        }
      })
      .catch((caught) => {
        if (!cancelled) setSearchError(errorMessage(caught));
      });
    return () => {
      cancelled = true;
    };
  }, [isOpen, debounced]);

  useEffect(() => {
    const onDown = (event: MouseEvent) => {
      if (containerRef.current && !containerRef.current.contains(event.target as Node)) {
        setIsOpen(false);
      }
    };
    document.addEventListener('mousedown', onDown);
    return () => document.removeEventListener('mousedown', onDown);
  }, []);

  if (selected) {
    return (
      <Field id={id} label={label} hint={hint} error={error} required={required}>
        <div className="row" style={{ border: '1px solid var(--color-border)', borderRadius: 'var(--radius-md)', padding: 'var(--space-2) var(--space-3)', minHeight: 'var(--control-height)' }}>
          <div className="grow">
            <span className="table__primary">{selected.name}</span>
            <div className="table__secondary mono">
              {selected.sku} · {formatMoney(selected.sellingPrice)}
            </div>
          </div>
          {!disabled && (
            <Button
              variant="ghost"
              size="sm"
              icon="close"
              aria-label="Choose a different product"
              onClick={() => {
                onSelect(null);
                setTerm('');
                setIsOpen(true);
              }}
            />
          )}
        </div>
      </Field>
    );
  }

  return (
    <div ref={containerRef} style={{ position: 'relative' }}>
      <Field id={id} label={label} hint={hint} error={error} required={required}>
        <span className="search-input">
          <span className="search-input__icon">
            <Icon name="search" size={16} />
          </span>
          <input
            id={id}
            className={['control', error ? 'control--invalid' : ''].filter(Boolean).join(' ')}
            type="search"
            autoComplete="off"
            autoFocus={autoFocus}
            disabled={disabled}
            placeholder="Scan a barcode, or search by name or SKU"
            value={term}
            onFocus={() => setIsOpen(true)}
            onChange={(event) => {
              setTerm(event.target.value);
              setIsOpen(true);
            }}
            onKeyDown={(event) => {
              // A scanner ends its input with Enter; one match means the choice is unambiguous.
              if (event.key === 'Enter') {
                event.preventDefault();
                if (results.length === 1) {
                  onSelect(results[0]);
                  setIsOpen(false);
                }
              }
              if (event.key === 'Escape') setIsOpen(false);
            }}
          />
        </span>
      </Field>

      {isOpen && (
        <div className="user-menu__panel" style={{ left: 0, right: 0, maxHeight: '18rem', overflowY: 'auto' }}>
          {searchError ? (
            <p className="user-menu__item text-muted">{searchError}</p>
          ) : results.length === 0 ? (
            <p className="user-menu__item text-muted">{term ? 'No products match.' : 'Start typing to search.'}</p>
          ) : (
            results.map((product) => (
              <button
                key={product.id}
                type="button"
                className="user-menu__item"
                onClick={() => {
                  onSelect(product);
                  setIsOpen(false);
                  setTerm('');
                }}
              >
                <span className="grow" style={{ textAlign: 'left' }}>
                  <span className="table__primary">{product.name}</span>
                  <span className="table__secondary mono" style={{ display: 'block' }}>
                    {product.sku}
                  </span>
                </span>
                <span className="money">{formatMoney(product.sellingPrice)}</span>
              </button>
            ))
          )}
        </div>
      )}
    </div>
  );
}

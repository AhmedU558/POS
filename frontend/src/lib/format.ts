/*
 * Presentation helpers.
 *
 * These format values the API has already computed. The frontend never computes authoritative
 * money, expected cash or variance (UI/UX Specification 11.3 and 14) — arithmetic here is limited
 * to what a cashier does in their head at the drawer, such as change from an amount tendered,
 * which is display-only and is re-derived by the backend when the sale is settled.
 */

/** The API serialises BigDecimal as a JSON number or a string, depending on the endpoint. */
export type Decimal = number | string | null | undefined;

export function toNumber(value: Decimal): number {
  if (value === null || value === undefined || value === '') {
    return 0;
  }
  const parsed = typeof value === 'number' ? value : Number(value);
  return Number.isFinite(parsed) ? parsed : 0;
}

let activeCurrency = 'PKR';

/** Set once from the active store so every screen shows the same currency (SRS store settings). */
export function setActiveCurrency(code: string | null | undefined) {
  if (code && /^[A-Z]{3}$/.test(code)) {
    activeCurrency = code;
  }
}

export function getActiveCurrency(): string {
  return activeCurrency;
}

/**
 * Currency symbol overrides. Intl.NumberFormat may display "PKR" or "Rs" depending on locale;
 * we normalise to the symbol the business wants on receipts and screens.
 */
const CURRENCY_SYMBOLS: Record<string, string> = {
  PKR: 'Rs.',
  USD: '$',
  EUR: '€',
  GBP: '£',
};

/** Returns the display symbol for a currency code, falling back to the code itself. */
export function currencySymbol(code: string = activeCurrency): string {
  return CURRENCY_SYMBOLS[code] ?? code;
}

export function formatMoney(value: Decimal, currency: string = activeCurrency): string {
  const num = toNumber(value);
  const symbol = CURRENCY_SYMBOLS[currency];
  if (symbol) {
    // Use our own symbol + Intl number formatting (without currency style) for consistent display.
    const formatted = new Intl.NumberFormat(undefined, {
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    }).format(num);
    return `${symbol}\u00A0${formatted}`;
  }
  try {
    return new Intl.NumberFormat(undefined, {
      style: 'currency',
      currency,
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    }).format(num);
  } catch {
    return num.toFixed(2);
  }
}

/**
 * Quantities are decimal because a unit may allow fractions. Trailing zeros are dropped so a
 * whole number of items reads as "3", not "3.0000".
 */
export function formatQuantity(value: Decimal): string {
  const parsed = toNumber(value);
  if (Number.isInteger(parsed)) {
    return String(parsed);
  }
  return String(Number(parsed.toFixed(4)));
}

/** Tax rates are stored as a fraction (0.15) and read by humans as a percentage. */
export function formatPercent(rate: Decimal): string {
  const parsed = toNumber(rate);
  const percent = parsed * 100;
  return `${Number(percent.toFixed(4))}%`;
}

export function formatDateTime(value: string | null | undefined): string {
  if (!value) return '—';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '—';
  return date.toLocaleString(undefined, {
    year: 'numeric',
    month: 'short',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  });
}

export function formatDate(value: string | null | undefined): string {
  if (!value) return '—';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '—';
  return date.toLocaleDateString(undefined, { year: 'numeric', month: 'short', day: '2-digit' });
}

export function formatTime(value: string | null | undefined): string {
  if (!value) return '—';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '—';
  return date.toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit' });
}

/** Start and end of the local day, as the ISO instants the sales API expects. */
export function todayRange(): { from: string; to: string } {
  const start = new Date();
  start.setHours(0, 0, 0, 0);
  const end = new Date(start);
  end.setDate(end.getDate() + 1);
  return { from: start.toISOString(), to: end.toISOString() };
}

export function initials(firstName?: string, lastName?: string, fallback = '?'): string {
  const letters = `${firstName?.[0] ?? ''}${lastName?.[0] ?? ''}`.trim();
  return letters ? letters.toUpperCase() : fallback;
}

/**
 * Turns anything thrown by an API client into a sentence a shop-floor user can act on.
 * Network failures in particular otherwise surface as the browser's own "Failed to fetch".
 */
export function errorMessage(error: unknown, fallback = 'Something went wrong. Please try again.'): string {
  if (error instanceof Error) {
    if (error.message === 'Failed to fetch' || error.message.includes('NetworkError')) {
      return 'Cannot reach the server. Check the connection and try again.';
    }
    return error.message || fallback;
  }
  return fallback;
}

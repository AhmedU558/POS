'use client';

import { useEffect, useState } from 'react';

/**
 * Delays a fast-changing value so a search box does not fire a request per keystroke.
 *
 * The list screens previously re-queried on every character, which on a real catalogue means a
 * dozen in-flight requests racing to write the same state.
 */
export function useDebounced<T>(value: T, delayMs = 300): T {
  const [settled, setSettled] = useState(value);

  useEffect(() => {
    const timer = window.setTimeout(() => setSettled(value), delayMs);
    return () => window.clearTimeout(timer);
  }, [value, delayMs]);

  return settled;
}

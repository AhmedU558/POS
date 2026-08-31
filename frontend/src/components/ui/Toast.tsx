'use client';

import React, { createContext, useCallback, useContext, useMemo, useRef, useState } from 'react';
import { Icon, IconName } from './Icon';

export type ToastTone = 'success' | 'error' | 'info';

interface Toast {
  id: number;
  tone: ToastTone;
  message: string;
}

interface ToastApi {
  success: (message: string) => void;
  error: (message: string) => void;
  info: (message: string) => void;
}

const ToastContext = createContext<ToastApi | null>(null);

const ICONS: Record<ToastTone, IconName> = {
  success: 'check-circle',
  error: 'error',
  info: 'info',
};

const DISMISS_AFTER_MS = 5000;

/**
 * Transient confirmation for actions whose result is not otherwise visible on screen.
 *
 * Errors persist until dismissed. A cashier who missed the reason a sale failed cannot recover
 * it, so the message waits for them rather than the other way round.
 */
export function ToastProvider({ children }: { children: React.ReactNode }) {
  const [toasts, setToasts] = useState<Toast[]>([]);
  const nextId = useRef(1);

  const dismiss = useCallback((id: number) => {
    setToasts((current) => current.filter((toast) => toast.id !== id));
  }, []);

  const push = useCallback(
    (tone: ToastTone, message: string) => {
      const id = nextId.current++;
      setToasts((current) => [...current, { id, tone, message }]);
      if (tone !== 'error') {
        window.setTimeout(() => dismiss(id), DISMISS_AFTER_MS);
      }
    },
    [dismiss]
  );

  const api = useMemo<ToastApi>(
    () => ({
      success: (message: string) => push('success', message),
      error: (message: string) => push('error', message),
      info: (message: string) => push('info', message),
    }),
    [push]
  );

  return (
    <ToastContext.Provider value={api}>
      {children}
      <div className="toast-region" role="region" aria-label="Notifications">
        {toasts.map((toast) => (
          <div
            key={toast.id}
            className={`toast toast--${toast.tone}`}
            role={toast.tone === 'error' ? 'alert' : 'status'}
          >
            <span className="toast__icon">
              <Icon name={ICONS[toast.tone]} size={18} />
            </span>
            <span className="toast__message">{toast.message}</span>
            <button type="button" className="toast__dismiss" onClick={() => dismiss(toast.id)} aria-label="Dismiss">
              <Icon name="close" size={16} />
            </button>
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  );
}

/**
 * Usable outside a provider so that a component can be unit-tested in isolation without every
 * test having to reconstruct the application tree.
 */
export function useToast(): ToastApi {
  const context = useContext(ToastContext);
  return context ?? NO_OP;
}

const NO_OP: ToastApi = {
  success: () => {},
  error: () => {},
  info: () => {},
};

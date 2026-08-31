'use client';

import React, { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { useAuth } from '@/features/auth/AuthContext';
import { Store, storesApi } from '@/lib/api/organization';
import { RegisterSession, registerSessionsApi } from '@/lib/api/register-sessions';
import { setActiveCurrency } from '@/lib/format';
import { hasAnyPermission, P } from '@/lib/permissions';

/*
 * Operating context: which store the user is working in, and which till they have open.
 *
 * Screens used to demand this by hand — the POS asked the cashier to type four raw UUIDs before
 * they could ring up a sale. Both facts are resolvable from the API, so they are resolved once
 * here and read everywhere else.
 */

const ACTIVE_STORE_KEY = 'pos.activeStoreId';

interface StoreContextValue {
  /** Stores the signed-in user is assigned to. */
  stores: Store[];
  activeStore: Store | null;
  activeStoreId: string | null;
  setActiveStoreId: (id: string) => void;
  /** The user's open register session, or null when no till is open. */
  session: RegisterSession | null;
  isLoading: boolean;
  /** Reload stores and session — call after opening or closing a till, or creating a store. */
  refresh: () => Promise<void>;
}

const StoreContext = createContext<StoreContextValue | null>(null);

export function StoreProvider({ children }: { children: React.ReactNode }) {
  const { user, isAuthenticated } = useAuth();
  const [stores, setStores] = useState<Store[]>([]);
  const [activeStoreId, setActiveStoreIdState] = useState<string | null>(null);
  const [session, setSession] = useState<RegisterSession | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  const canReadStores = hasAnyPermission(user?.permissions, [P.STORE_READ]);
  const canSeeSession = hasAnyPermission(user?.permissions, [P.SALE_CREATE, P.REGISTER_OPEN, P.REGISTER_READ]);

  const load = useCallback(async () => {
    if (!isAuthenticated || !user) {
      setStores([]);
      setSession(null);
      setIsLoading(false);
      return;
    }
    setIsLoading(true);

    /*
     * A failure here must not blank the application. Store scope and till state are context, not
     * content: if either lookup fails the screens still render and say so in their own terms.
     */
    const [storeResult, sessionResult] = await Promise.allSettled([
      canReadStores ? storesApi.list() : Promise.resolve<Store[]>([]),
      canSeeSession ? registerSessionsApi.current() : Promise.resolve(null),
    ]);

    const loadedStores = storeResult.status === 'fulfilled' ? storeResult.value : [];
    setStores(loadedStores);
    setSession(sessionResult.status === 'fulfilled' ? sessionResult.value : null);

    setActiveStoreIdState((current) => {
      const remembered = current ?? readRememberedStoreId();
      /*
       * The open till wins over a remembered choice: a cashier standing at a register in store B
       * must not have management screens quietly reporting store A.
       */
      const openSessionStore = sessionResult.status === 'fulfilled' ? sessionResult.value?.storeId : undefined;
      const candidates = [openSessionStore, remembered, user.storeIds?.[0], loadedStores[0]?.id];
      const resolved =
        candidates.find((id) => id && (loadedStores.length === 0 || loadedStores.some((store) => store.id === id))) ??
        null;
      if (resolved) {
        rememberStoreId(resolved);
      }
      return resolved;
    });

    setIsLoading(false);
  }, [canReadStores, canSeeSession, isAuthenticated, user]);

  useEffect(() => {
    void load();
  }, [load]);

  const activeStore = useMemo(
    () => stores.find((store) => store.id === activeStoreId) ?? null,
    [stores, activeStoreId]
  );

  // Money is formatted in the store's own currency rather than a hardcoded one.
  useEffect(() => {
    setActiveCurrency(activeStore?.currencyCode);
  }, [activeStore]);

  const setActiveStoreId = useCallback((id: string) => {
    rememberStoreId(id);
    setActiveStoreIdState(id);
  }, []);

  const value = useMemo<StoreContextValue>(
    () => ({ stores, activeStore, activeStoreId, setActiveStoreId, session, isLoading, refresh: load }),
    [stores, activeStore, activeStoreId, setActiveStoreId, session, isLoading, load]
  );

  return <StoreContext.Provider value={value}>{children}</StoreContext.Provider>;
}

const FALLBACK: StoreContextValue = {
  stores: [],
  activeStore: null,
  activeStoreId: null,
  setActiveStoreId: () => {},
  session: null,
  isLoading: false,
  refresh: async () => {},
};

/** Returns an inert context outside a provider so components stay unit-testable in isolation. */
export function useStoreContext(): StoreContextValue {
  return useContext(StoreContext) ?? FALLBACK;
}

function readRememberedStoreId(): string | null {
  if (typeof window === 'undefined') return null;
  try {
    return window.localStorage.getItem(ACTIVE_STORE_KEY);
  } catch {
    return null;
  }
}

function rememberStoreId(id: string) {
  if (typeof window === 'undefined') return;
  try {
    window.localStorage.setItem(ACTIVE_STORE_KEY, id);
  } catch {
    // Private browsing or a storage quota: the choice simply does not survive a reload.
  }
}

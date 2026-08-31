'use client';

import React, { useEffect, useRef, useState } from 'react';
import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { useAuth } from '@/features/auth/AuthContext';
import { StoreProvider, useStoreContext } from '@/features/session/StoreContext';
import { Icon } from '@/components/ui/Icon';
import { Badge } from '@/components/ui/Badge';
import { initials } from '@/lib/format';
import { hasAnyPermission } from '@/lib/permissions';
import { NAV_SECTIONS, isBareRoute, isNavItemActive } from './navigation';

/**
 * The frame every management screen sits in.
 *
 * Its job is to answer "where am I and where can I go" without the user reading a URL. Screens
 * that own the whole viewport — login, forced rotation, and the till itself — opt out, because a
 * cashier mid-sale should not be one stray click away from the purchase-order list.
 */
export function AppShell({ children }: { children: React.ReactNode }) {
  const pathname = usePathname() ?? '/';
  const { isAuthenticated } = useAuth();

  if (!isAuthenticated || isBareRoute(pathname)) {
    return <StoreProvider>{children}</StoreProvider>;
  }

  return (
    <StoreProvider>
      <ShellFrame pathname={pathname}>{children}</ShellFrame>
    </StoreProvider>
  );
}

function ShellFrame({ pathname, children }: { pathname: string; children: React.ReactNode }) {
  const { user } = useAuth();
  const [navOpen, setNavOpen] = useState(false);

  // On a phone the sidebar is an overlay; leaving it open across a navigation hides the page.
  useEffect(() => {
    setNavOpen(false);
  }, [pathname]);

  const sections = NAV_SECTIONS.map((section) => ({
    ...section,
    items: section.items.filter(
      (item) => item.permissions.length === 0 || hasAnyPermission(user?.permissions, item.permissions)
    ),
  })).filter((section) => section.items.length > 0);

  return (
    <div className="shell">
      {navOpen && <div className="shell__scrim" onClick={() => setNavOpen(false)} aria-hidden="true" />}

      <aside className={['shell__sidebar', navOpen ? 'shell__sidebar--open' : ''].filter(Boolean).join(' ')}>
        <Link href="/" className="shell__brand">
          <span className="shell__brand-mark" aria-hidden="true">
            PO
          </span>
          <span className="shell__brand-name">POS Manager</span>
        </Link>
        <nav className="shell__nav" aria-label="Main">
          {sections.map((section) => (
            <div className="nav-group" key={section.label}>
              <p className="nav-group__label">{section.label}</p>
              {section.items.map((item) => (
                <Link
                  key={item.href}
                  href={item.href}
                  className="nav-item"
                  aria-current={isNavItemActive(item, pathname) ? 'page' : undefined}
                >
                  <span className="nav-item__icon">
                    <Icon name={item.icon} size={18} />
                  </span>
                  {item.label}
                </Link>
              ))}
            </div>
          ))}
        </nav>
      </aside>

      <div className="shell__main">
        <header className="shell__topbar">
          <div className="row">
            <button
              type="button"
              className="btn btn--ghost btn--sm shell__menu-button"
              onClick={() => setNavOpen((open) => !open)}
              aria-label="Toggle navigation"
              aria-expanded={navOpen}
            >
              <Icon name="menu" size={20} />
            </button>
            <OperatingContext />
          </div>
          <UserMenu />
        </header>
        <main className="shell__content">{children}</main>
      </div>
    </div>
  );
}

/** Store, and till state, kept permanently in view (UI/UX Specification 6.1 header row). */
function OperatingContext() {
  const { stores, activeStore, activeStoreId, setActiveStoreId, session, isLoading } = useStoreContext();

  if (isLoading) {
    return <span className="text-small text-subtle">Loading workspace…</span>;
  }

  return (
    <div className="shell__context">
      {stores.length > 1 ? (
        <label className="context-chip">
          <Icon name="store" size={16} />
          <span className="context-chip__label">Store</span>
          <select
            className="control"
            style={{ height: 'var(--control-height-sm)', minHeight: 0, width: 'auto' }}
            value={activeStoreId ?? ''}
            onChange={(event) => setActiveStoreId(event.target.value)}
            aria-label="Active store"
          >
            {stores.map((store) => (
              <option key={store.id} value={store.id}>
                {store.name}
              </option>
            ))}
          </select>
        </label>
      ) : (
        activeStore && (
          <span className="context-chip">
            <Icon name="store" size={16} />
            <span className="context-chip__label">Store</span>
            <span className="context-chip__value">{activeStore.name}</span>
          </span>
        )
      )}

      <span className="context-chip">
        <Icon name="register" size={16} />
        <span className="context-chip__label">Register</span>
        {session ? <Badge variant="success">Open</Badge> : <Badge variant="pending">Closed</Badge>}
      </span>
    </div>
  );
}

function UserMenu() {
  const { user, logout } = useAuth();
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!open) return;
    const onDocumentDown = (event: MouseEvent) => {
      if (ref.current && !ref.current.contains(event.target as Node)) {
        setOpen(false);
      }
    };
    const onEscape = (event: KeyboardEvent) => {
      if (event.key === 'Escape') setOpen(false);
    };
    document.addEventListener('mousedown', onDocumentDown);
    document.addEventListener('keydown', onEscape);
    return () => {
      document.removeEventListener('mousedown', onDocumentDown);
      document.removeEventListener('keydown', onEscape);
    };
  }, [open]);

  if (!user) {
    return null;
  }

  return (
    <div className="user-menu" ref={ref}>
      <button
        type="button"
        className="user-menu__trigger"
        onClick={() => setOpen((current) => !current)}
        aria-expanded={open}
        aria-haspopup="menu"
      >
        <span className="user-menu__avatar" aria-hidden="true">
          {initials(user.firstName, user.lastName)}
        </span>
        <span className="text-small">{user.firstName}</span>
        <Icon name="chevron-down" size={14} />
      </button>
      {open && (
        <div className="user-menu__panel" role="menu">
          <div className="user-menu__identity">
            <p className="user-menu__name">
              {user.firstName} {user.lastName}
            </p>
            <p className="text-small text-muted">{user.username}</p>
          </div>
          <button type="button" className="user-menu__item" role="menuitem" onClick={() => void logout()}>
            <Icon name="logout" size={16} />
            Sign out
          </button>
        </div>
      )}
    </div>
  );
}

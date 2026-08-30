"use client";

import { useAuth } from "@/features/auth/AuthContext";
import Link from "next/link";
import { Button } from "@/components/ui/Button";

export default function Home() {
  const { user, logout } = useAuth();

  return (
    <main
      style={{
        padding: "var(--space-8)",
        maxWidth: "var(--layout-max-width)",
        margin: "0 auto",
      }}
    >
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <h1>POS Management System</h1>
        <button 
          onClick={() => logout()}
          style={{
            padding: 'var(--space-2) var(--space-4)',
            backgroundColor: 'transparent',
            border: '1px solid var(--color-border-strong)',
            borderRadius: 'var(--radius-sm)',
            cursor: 'pointer'
          }}
        >
          Logout
        </button>
      </div>
      
      <p style={{ color: "var(--color-foreground-muted)", marginTop: "var(--space-2)" }}>
        Integrated POS, Inventory &amp; Business Management System.
      </p>

      {user && (
        <div style={{ marginTop: 'var(--space-8)', padding: 'var(--space-4)', backgroundColor: 'var(--color-surface)', borderRadius: 'var(--radius-md)', border: '1px solid var(--color-border)' }}>
          <h2 style={{ fontSize: 'var(--font-size-heading-sm)', marginBottom: 'var(--space-4)' }}>Welcome, {user.firstName} {user.lastName}</h2>
          <p><strong>Username:</strong> {user.username}</p>
          <p><strong>Email:</strong> {user.email}</p>
          <p><strong>Permissions:</strong> {user.permissions.join(', ')}</p>
        </div>
      )}

      {user?.permissions?.includes('PURCHASE_READ') && (
        <div style={{ marginTop: 'var(--space-8)', padding: 'var(--space-4)', backgroundColor: 'var(--color-surface)', borderRadius: 'var(--radius-md)', border: '1px solid var(--color-border)' }}>
          <h2 style={{ fontSize: 'var(--font-size-heading-sm)', marginBottom: 'var(--space-4)' }}>Purchasing</h2>
          <Link href="/purchase-orders" style={{ textDecoration: 'none' }}>
            <Button variant="secondary">Purchase Orders</Button>
          </Link>
        </div>
      )}

      {user?.permissions?.includes('SUPPLIER_READ') && (
        <div style={{ marginTop: 'var(--space-8)', padding: 'var(--space-4)', backgroundColor: 'var(--color-surface)', borderRadius: 'var(--radius-md)', border: '1px solid var(--color-border)' }}>
          <h2 style={{ fontSize: 'var(--font-size-heading-sm)', marginBottom: 'var(--space-4)' }}>Suppliers</h2>
          <Link href="/suppliers" style={{ textDecoration: 'none' }}>
            <Button variant="secondary">Suppliers</Button>
          </Link>
        </div>
      )}

      {user?.permissions?.includes('CUSTOMER_READ') && (
        <div style={{ marginTop: 'var(--space-8)', padding: 'var(--space-4)', backgroundColor: 'var(--color-surface)', borderRadius: 'var(--radius-md)', border: '1px solid var(--color-border)' }}>
          <h2 style={{ fontSize: 'var(--font-size-heading-sm)', marginBottom: 'var(--space-4)' }}>Customers</h2>
          <Link href="/customers" style={{ textDecoration: 'none' }}>
            <Button variant="secondary">Customers</Button>
          </Link>
        </div>
      )}

      {user?.permissions?.includes('PRODUCT_READ') && (
        <div style={{ marginTop: 'var(--space-8)', padding: 'var(--space-4)', backgroundColor: 'var(--color-surface)', borderRadius: 'var(--radius-md)', border: '1px solid var(--color-border)' }}>
          <h2 style={{ fontSize: 'var(--font-size-heading-sm)', marginBottom: 'var(--space-4)' }}>Catalog Management</h2>
          <div style={{ display: 'flex', gap: 'var(--space-4)' }}>
            <Link href="/categories" style={{ textDecoration: 'none' }}>
              <Button variant="secondary">Categories</Button>
            </Link>
            <Link href="/brands" style={{ textDecoration: 'none' }}>
              <Button variant="secondary">Brands</Button>
            </Link>
            <Link href="/units" style={{ textDecoration: 'none' }}>
              <Button variant="secondary">Units</Button>
            </Link>
          </div>
        </div>
      )}
    </main>
  );
}
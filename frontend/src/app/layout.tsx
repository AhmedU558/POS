import type { Metadata } from "next";
import { Inter } from "next/font/google";
import "./globals.css";
import { AuthProvider } from "@/features/auth/AuthContext";
import { ProtectedRoute } from "@/components/auth/ProtectedRoute";
import { ToastProvider } from "@/components/ui/Toast";
import { AppShell } from "@/components/layout/AppShell";

const inter = Inter({ subsets: ["latin"], variable: "--font-inter" });

export const metadata: Metadata = {
  title: "POS Management System",
  description: "Point of Sale and Business Management System",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    /*
     * The Inter variable must land on the same element that declares --font-sans (:root).
     * Declared on <body> instead, --font-sans resolves against :root, where --font-inter is
     * undefined; the substitution is then guaranteed-invalid and every screen falls back to
     * the browser default serif.
     */
    <html lang="en" className={inter.variable}>
      <body>
        <AuthProvider>
          <ToastProvider>
            <ProtectedRoute>
              <AppShell>{children}</AppShell>
            </ProtectedRoute>
          </ToastProvider>
        </AuthProvider>
      </body>
    </html>
  );
}

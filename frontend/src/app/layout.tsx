import type { Metadata } from "next";
import { Inter } from "next/font/google";
import "./globals.css";

// UI/UX Specification section 7.2 names Inter (or a system sans-serif) as the font token.
const inter = Inter({
  variable: "--font-inter",
  subsets: ["latin"],
  display: "swap",
});

export const metadata: Metadata = {
  title: "POS Management System",
  description: "Integrated POS, Inventory & Business Management System",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="en" className={inter.variable}>
      <body>{children}</body>
    </html>
  );
}

import type { Metadata } from "next";
import { Geist } from "next/font/google";
import "./globals.css";

const font = Geist({ subsets: ["latin"], variable: "--font-geist-sans" });

export const metadata: Metadata = {
  title: process.env.NEXT_PUBLIC_APP_NAME ?? "Application",
};

// html/body 골격 — lang은 [locale]/layout.tsx의 metadata에서 처리
export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html suppressHydrationWarning>
      <body className={`${font.variable} font-sans antialiased`}>
        {children}
      </body>
    </html>
  );
}

import type { Metadata } from "next";
import "./globals.css";
import "./ui-refresh.css";
import "./ui-enhance.css";
import "./ui-mono.css";
import "./landing.css";
import { Toasts } from "@/components/toasts";
import { ConfirmHost } from "@/components/confirm-dialog";
import { LocaleProvider } from "@/i18n/context";

export const metadata: Metadata = {
  title: "RAG Craft · Knowledge Workspace",
  description: "Document-grounded assistants with traceable sources.",
};

export default function RootLayout({
  children,
}: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="en">
      <head>
        <link rel="preconnect" href="https://fonts.googleapis.com" />
        <link rel="preconnect" href="https://fonts.gstatic.com" crossOrigin="anonymous" />
        {/* Root layout applies to every route, so the font loads once. */}
        {/* eslint-disable-next-line @next/next/no-page-custom-font */}
        <link
          href="https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600;700;800&family=JetBrains+Mono:wght@400;500;600&display=swap"
          rel="stylesheet"
        />
      </head>
      <body>
        <LocaleProvider>
          {children}
          <Toasts />
          <ConfirmHost />
        </LocaleProvider>
      </body>
    </html>
  );
}

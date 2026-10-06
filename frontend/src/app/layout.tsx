import type { Metadata } from "next";
import "./globals.css";
import "./ui-refresh.css";
import "./ui-enhance.css";
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

'use client';

import { ReactNode } from 'react';
import Link from 'next/link';
import { ShieldCheck } from 'lucide-react';
import { ToastProvider } from '@/hooks/useToast';

/**
 * The platform portal deliberately sits outside `/dashboard`: it is operator
 * surface, not tenant surface, so it does not inherit the tenant chrome (or its
 * tenant-scoped navigation). It still needs its own ToastProvider.
 */
export default function AdminLayout({ children }: { children: ReactNode }) {
  return (
    <ToastProvider>
      <div className="min-h-screen bg-gray-50">
        <header className="border-b border-gray-200 bg-white">
          <div className="mx-auto flex max-w-7xl items-center justify-between px-6 py-3">
            <div className="flex items-center gap-2">
              <span className="flex h-7 w-7 items-center justify-center rounded-lg bg-gray-900">
                <ShieldCheck className="h-4 w-4 text-white" />
              </span>
              <span className="font-semibold text-gray-900">ComplyAI Platform</span>
              <span className="rounded-full bg-gray-100 px-2 py-0.5 text-[10px] font-medium uppercase tracking-wide text-gray-600">
                Operator
              </span>
            </div>
            <Link href="/dashboard" className="text-sm text-gray-500 hover:text-gray-900">
              Back to workspace
            </Link>
          </div>
        </header>

        {children}
      </div>
    </ToastProvider>
  );
}

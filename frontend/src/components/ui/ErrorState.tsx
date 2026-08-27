'use client';

import { AlertTriangle, RefreshCw } from 'lucide-react';
import { Button } from '@/components/ui/Button';

interface ErrorStateProps {
  title?: string;
  /** What the user was trying to do, e.g. "load the review queue". */
  action?: string;
  detail?: string;
  onRetry?: () => void;
}

/**
 * Shown when data could not be loaded.
 *
 * The point is that this exists at all: the API client used to answer a failed
 * request with realistic fixture data, so an outage looked like a working page
 * full of security answers that were never real. An empty, honest error is the
 * only acceptable outcome for a compliance product.
 */
export function ErrorState({ title, action, detail, onRetry }: ErrorStateProps) {
  return (
    <div className="flex flex-col items-center justify-center rounded-xl border border-red-200 bg-red-50 px-6 py-12 text-center">
      <AlertTriangle className="mb-3 h-8 w-8 text-red-600" />

      <h2 className="text-base font-semibold text-red-900">
        {title ?? 'Could not load this data'}
      </h2>

      <p className="mt-1 max-w-md text-sm text-red-800">
        {detail ??
          `The ComplyAI API could not be reached${action ? ` while trying to ${action}` : ''}. ` +
            'Nothing is shown rather than showing data that might be wrong.'}
      </p>

      {onRetry && (
        <Button variant="outline" size="sm" onClick={onRetry} className="mt-5">
          <RefreshCw className="mr-1.5 h-4 w-4" />
          Try again
        </Button>
      )}
    </div>
  );
}

'use client';

import { useEffect } from 'react';
import { ErrorState } from '@/components/ui/ErrorState';

/**
 * Safety net for anything that throws while rendering the platform portal.
 *
 * Most failures are now API failures, because the client throws rather than
 * substituting fixture data. Whatever the cause, the user must see that
 * something went wrong instead of a plausible-looking page.
 */
export default function AdminError({
  error,
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  useEffect(() => {
    console.error('Platform admin error:', error);
  }, [error]);

  return (
    <div className="py-10">
      <ErrorState
        title="Something went wrong"
        detail={
          error.name === 'ApiUnavailableError'
            ? 'The Quenaire API could not be reached. Check that the backend is running, then try again.'
            : error.message || 'An unexpected error occurred.'
        }
        onRetry={reset}
      />
    </div>
  );
}

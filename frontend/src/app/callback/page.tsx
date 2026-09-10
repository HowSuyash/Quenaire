'use client';

import { Suspense, useEffect, useState } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import { completeLogin } from '@/lib/oidc';
import { setToken } from '@/lib/auth';

/**
 * Where Keycloak sends the browser back after signing in.
 *
 * The redirect existed on the login page before this route did, so signing in
 * arrived at a 404.
 */
function CallbackHandler() {
  const router = useRouter();
  const params = useSearchParams();
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const code = params.get('code');
    const state = params.get('state');
    const denied = params.get('error');

    if (denied) {
      setError(
        params.get('error_description') || 'Sign-in was cancelled.'
      );
      return;
    }
    if (!code || !state) {
      setError('This page is only reached after signing in.');
      return;
    }

    completeLogin(code, state)
      .then((tokens) => {
        setToken(tokens.access_token, tokens.refresh_token);
        // replace, not push: the code is spent, and going back would retry it.
        router.replace('/dashboard');
      })
      .catch((e: Error) => setError(e.message));
  }, [params, router]);

  return (
    <main className="flex min-h-screen items-center justify-center p-6">
      {error ? (
        <div className="max-w-md text-center">
          <h1 className="text-lg font-semibold text-slate-900">Could not sign you in</h1>
          <p className="mt-2 text-sm text-slate-600">{error}</p>
          <a
            href="/login"
            className="mt-6 inline-block rounded-md bg-slate-900 px-4 py-2 text-sm text-white"
          >
            Back to sign in
          </a>
        </div>
      ) : (
        <p className="text-sm text-slate-600">Signing you in…</p>
      )}
    </main>
  );
}

/**
 * useSearchParams suspends, and Next prerenders this route at build time; without a boundary
 * the build fails outright rather than at runtime.
 */
export default function CallbackPage() {
  return (
    <Suspense
      fallback={
        <main className="flex min-h-screen items-center justify-center p-6">
          <p className="text-sm text-slate-600">Signing you in…</p>
        </main>
      }
    >
      <CallbackHandler />
    </Suspense>
  );
}

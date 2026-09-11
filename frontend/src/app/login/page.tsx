'use client';

import { useState } from 'react';
import { beginLogin } from '@/lib/oidc';
import { Button } from '@/components/ui/Button';
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/Card';
import { Lock, AlertCircle } from 'lucide-react';
import { QuenaireMark } from '@/components/brand/QuenaireMark';

/**
 * Sign-in.
 *
 * There is no password field here, and no sign-in endpoint on the backend, on purpose:
 * authentication belongs to the identity provider and this app only ever receives the token it
 * issues. The redirect uses PKCE — see lib/oidc.ts.
 *
 * What used to be here: a form posting to /api/auth/login, which no controller served, and a
 * fallback that granted a demo administrator session whenever that call failed — so a wrong
 * password, a missing account, or an unreachable backend all let the caller in. The fallback was
 * later confined to development builds; it is now gone, along with the form that could not work.
 */
export default function LoginPage() {
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const signIn = () => {
    setError('');
    setLoading(true);
    beginLogin().catch((e: Error) => {
      setError(e.message);
      setLoading(false);
    });
  };

  return (
    <div className="min-h-screen flex items-center justify-center bg-gray-50 px-4">
      <div className="w-full max-w-md">
        <div className="text-center mb-8">
          <QuenaireMark className="mx-auto mb-4 h-16 w-16" />
          <h1 className="text-3xl font-bold text-gray-900">Quenaire</h1>
          <p className="text-gray-500 mt-1">Security Questionnaire Automation</p>
        </div>

        <Card>
          <CardHeader className="text-center">
            <CardTitle>Sign in to your account</CardTitle>
            <CardDescription>You will be taken to your identity provider</CardDescription>
          </CardHeader>
          <CardContent className="space-y-4">
            {error && (
              <div className="flex items-start gap-2 rounded-md bg-red-50 p-3 text-sm text-red-700">
                <AlertCircle className="h-4 w-4 mt-0.5 shrink-0" />
                <span>{error}</span>
              </div>
            )}

            <Button onClick={signIn} className="w-full" disabled={loading}>
              <Lock className="h-4 w-4 mr-2" />
              {loading ? 'Redirecting…' : 'Sign in'}
            </Button>

            <p className="text-center text-sm text-gray-500">
              Your password is handled by the identity provider, never by Quenaire.
            </p>

            <p className="text-center text-sm text-gray-500">
              Don&apos;t have an account?{' '}
              <a href="/register" className="text-primary-600 hover:text-primary-700 font-medium">
                Sign up
              </a>
            </p>
          </CardContent>
        </Card>

        <p className="text-center text-xs text-gray-400 mt-6">
          By signing in, you agree to our{' '}
          <a href="#" className="text-primary-600 hover:underline">Terms of Service</a>{' '}
          and{' '}
          <a href="#" className="text-primary-600 hover:underline">Privacy Policy</a>
        </p>
      </div>
    </div>
  );
}

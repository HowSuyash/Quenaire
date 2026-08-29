'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/Card';
import { setToken, setUser } from '@/lib/auth';
import { api } from '@/lib/api';
import { Shield, Lock, AlertCircle } from 'lucide-react';

/**
 * Whether a demo session may stand in for a real one.
 *
 * Compiled out of production builds the same way the offline fixtures are: both operands are
 * constant at build time, so this branch is not in the deployed bundle at all.
 */
const DEMO_LOGIN_ALLOWED =
  process.env.NODE_ENV !== 'production' &&
  process.env.NEXT_PUBLIC_ALLOW_DEMO_LOGIN === 'true';

export default function LoginPage() {
  const router = useRouter();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const loginAsDemo = (userRole = 'ADMIN') => {
    const mockUser = {
      id: 'usr-1',
      email: email || 'alex.chen@acmesecurity.com',
      firstName: 'Alex',
      lastName: 'Chen',
      role: userRole,
      tenantId: 'tenant-demo-1',
    };
    setToken('mock-jwt-token-demo', 'mock-refresh-token');
    setUser(mockUser);
    router.push('/dashboard');
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setLoading(true);

    try {
      const response = await fetch('/api/auth/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email, password }),
      }).catch(() => null);

      if (response && response.ok) {
        const data = await response.json();
        setToken(data.accessToken, data.refreshToken);
        setUser(data.user);
        router.push('/dashboard');
        return;
      }

      // A failed sign-in used to fall through to loginAsDemo('ADMIN'), so a wrong
      // password, an account that did not exist, or an unreachable backend all
      // granted an administrator session. Anyone who found the page was inside.
      if (DEMO_LOGIN_ALLOWED) {
        loginAsDemo('ADMIN');
        return;
      }

      setError(
        response && response.status === 401
          ? 'That email and password do not match an account.'
          : 'Sign-in is unavailable right now. Please try again shortly.'
      );
    } catch (err) {
      if (DEMO_LOGIN_ALLOWED) {
        loginAsDemo('ADMIN');
        return;
      }
      setError('Sign-in is unavailable right now. Please try again shortly.');
    } finally {
      setLoading(false);
    }
  };

  const handleKeycloakLogin = () => {
    // Redirect to Keycloak login
    const keycloakUrl = process.env.NEXT_PUBLIC_KEYCLOAK_URL || 'http://localhost:8081';
    const clientId = process.env.NEXT_PUBLIC_KEYCLOAK_CLIENT_ID || 'complyai-frontend';
    const redirectUri = `${window.location.origin}/callback`;
    
    window.location.href = `${keycloakUrl}/realms/complyai/protocol/openid-connect/auth?client_id=${clientId}&redirect_uri=${encodeURIComponent(redirectUri)}&response_type=code&scope=openid profile email`;
  };

  return (
    <div className="min-h-screen flex items-center justify-center bg-gray-50 px-4">
      <div className="w-full max-w-md">
        {/* Logo */}
        <div className="text-center mb-8">
          <div className="inline-flex items-center justify-center w-16 h-16 rounded-2xl bg-primary-600 mb-4">
            <Shield className="h-8 w-8 text-white" />
          </div>
          <h1 className="text-3xl font-bold text-gray-900">ComplyAI</h1>
          <p className="text-gray-500 mt-1">Security Questionnaire Automation</p>
        </div>

        <Card>
          <CardHeader className="text-center">
            <CardTitle>Sign in to your account</CardTitle>
            <CardDescription>Enter your credentials to access the dashboard</CardDescription>
          </CardHeader>
          <CardContent className="space-y-4">
            {/* Keycloak SSO Button */}
            <Button 
              variant="outline" 
              onClick={handleKeycloakLogin}
              className="w-full"
              disabled={loading}
            >
              <Lock className="h-4 w-4 mr-2" />
              Sign in with Keycloak (SSO)
            </Button>

            <div className="relative">
              <div className="absolute inset-0 flex items-center">
                <span className="w-full border-t border-gray-300" />
              </div>
              <div className="relative flex justify-center text-sm">
                <span className="bg-white px-2 text-gray-500">Or continue with email</span>
              </div>
            </div>

            {/* Email/Password Form */}
            <form onSubmit={handleSubmit} className="space-y-4">
              {error && (
                <div className="flex items-center gap-2 p-3 bg-red-50 border border-red-100 rounded-lg text-red-700 text-sm">
                  <AlertCircle className="h-4 w-4 flex-shrink-0" />
                  <span>{error}</span>
                </div>
              )}

              <Input
                label="Email"
                type="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                placeholder="you@company.com"
                required
                disabled={loading}
                autoComplete="email"
              />

              <Input
                label="Password"
                type="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                placeholder="••••••••"
                required
                disabled={loading}
                autoComplete="current-password"
              />

              <Button type="submit" className="w-full" loading={loading}>
                Sign in
              </Button>
            </form>

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
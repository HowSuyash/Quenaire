'use client';

import { useCallback, useEffect, useState } from 'react';
import Link from 'next/link';
import { QuenaireMark } from '@/components/brand/QuenaireMark';
import { useRouter } from 'next/navigation';
import { AlertCircle, Check, CheckCircle, Loader2, Shield, X } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/Card';
import {
  checkWorkspaceAvailable, getSignupStatus, registerWorkspace, slugify,
} from '@/lib/auth-api';
import { RegisterResult, SignupStatus } from '@/types/auth';

const MIN_PASSWORD_LENGTH = 12;

/** Mirrors the server's rule so the form never submits a doomed value. */
const EMAIL_RE = /^[^@\s]+@[^@\s.]+\.[^@\s]{2,}$/;

type Availability = 'idle' | 'checking' | 'available' | 'taken';

export default function RegisterPage() {
  const router = useRouter();

  const [status, setStatus] = useState<SignupStatus | null>(null);
  const [companyName, setCompanyName] = useState('');
  const [firstName, setFirstName] = useState('');
  const [lastName, setLastName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');

  // Blank until the user edits it: an untouched field follows the company name.
  const [slugOverride, setSlugOverride] = useState('');
  const [availability, setAvailability] = useState<Availability>('idle');

  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [result, setResult] = useState<RegisterResult | null>(null);

  const slug = slugOverride ? slugify(slugOverride) : slugify(companyName);

  useEffect(() => {
    getSignupStatus().then(setStatus);
  }, []);

  // Debounced availability check so typing does not fire a request per keystroke.
  useEffect(() => {
    if (!slug) {
      setAvailability('idle');
      return;
    }

    setAvailability('checking');
    const timer = setTimeout(async () => {
      const available = await checkWorkspaceAvailable(slug);
      setAvailability(available === null ? 'idle' : available ? 'available' : 'taken');
    }, 400);

    return () => clearTimeout(timer);
  }, [slug]);

  const passwordTooShort = password.length > 0 && password.length < MIN_PASSWORD_LENGTH;
  const passwordsMismatch = confirmPassword.length > 0 && password !== confirmPassword;
  const emailInvalid = email.length > 0 && !EMAIL_RE.test(email);

  const canSubmit =
    companyName.trim().length > 0 &&
    EMAIL_RE.test(email) &&
    password.length >= MIN_PASSWORD_LENGTH &&
    password === confirmPassword &&
    !!slug &&
    availability !== 'taken' &&
    !submitting;

  const handleSubmit = useCallback(async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setSubmitting(true);

    const response = await registerWorkspace({
      companyName: companyName.trim(),
      firstName: firstName.trim(),
      lastName: lastName.trim(),
      email: email.trim(),
      password,
      workspaceSlug: slug,
    });

    setSubmitting(false);

    if (!response.success) {
      setError(response.error ?? 'Could not create the workspace.');
      return;
    }
    setResult(response);
  }, [companyName, firstName, lastName, email, password, slug]);

  // ---- Success ------------------------------------------------------------

  if (result?.success) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-gray-50 px-4">
        <div className="w-full max-w-md">
          <Card>
            <CardContent className="px-6 pb-8 pt-8 text-center">
              <div className="mx-auto mb-4 inline-flex h-16 w-16 items-center justify-center rounded-2xl bg-green-100">
                <CheckCircle className="h-8 w-8 text-green-600" />
              </div>

              <h1 className="text-2xl font-bold text-gray-900">Workspace created</h1>
              <p className="mt-2 text-sm text-gray-600">{result.message}</p>

              <dl className="mt-5 space-y-2 rounded-lg bg-gray-50 p-4 text-left text-sm">
                <div className="flex justify-between gap-3">
                  <dt className="text-gray-500">Workspace</dt>
                  <dd className="font-mono text-gray-900">{result.workspaceSlug}</dd>
                </div>
                <div className="flex justify-between gap-3">
                  <dt className="text-gray-500">Owner</dt>
                  <dd className="truncate text-gray-900">{email}</dd>
                </div>
                <div className="flex justify-between gap-3">
                  <dt className="text-gray-500">Plan</dt>
                  <dd className="text-gray-900">Free · 14-day trial</dd>
                </div>
              </dl>

              {result.credentialsProvisioned ? (
                <Button className="mt-6 w-full" size="lg" onClick={() => router.push('/login')}>
                  Continue to sign in
                </Button>
              ) : (
                <>
                  <div className="mt-5 flex items-start gap-2 rounded-lg border border-amber-200 bg-amber-50 p-3 text-left">
                    <AlertCircle className="mt-0.5 h-4 w-4 shrink-0 text-amber-600" />
                    <p className="text-xs text-amber-800">
                      Sign-in is handled by Keycloak, which is not configured on this instance,
                      so no password was set. The workspace exists and an administrator can
                      finish provisioning the account.
                    </p>
                  </div>
                  <Button
                    variant="outline"
                    className="mt-4 w-full"
                    size="lg"
                    onClick={() => router.push('/login')}
                  >
                    Back to sign in
                  </Button>
                </>
              )}
            </CardContent>
          </Card>
        </div>
      </div>
    );
  }

  // ---- Signup disabled ----------------------------------------------------

  if (status && !status.signupEnabled) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-gray-50 px-4">
        <Card className="w-full max-w-md">
          <CardContent className="px-6 pb-8 pt-8 text-center">
            <div className="mx-auto mb-4 inline-flex h-16 w-16 items-center justify-center rounded-2xl bg-gray-100">
              <Shield className="h-8 w-8 text-gray-500" />
            </div>
            <h1 className="text-xl font-semibold text-gray-900">Signup is closed</h1>
            <p className="mt-2 text-sm text-gray-500">
              This instance does not accept self-serve registration. Ask your administrator
              for an invitation.
            </p>
            <Link href="/login" className="mt-5 inline-block text-sm text-primary-600 hover:underline">
              Back to sign in
            </Link>
          </CardContent>
        </Card>
      </div>
    );
  }

  // ---- Form ---------------------------------------------------------------

  return (
    <main className="min-h-screen bg-gray-50 px-4 py-12">
      <div className="mx-auto w-full max-w-lg">
        <div className="mb-8 text-center">
          {/* aria-label because the link's only content is a graphic: a screen
              reader announced it as an unlabelled link. */}
          <Link
            href="/"
            aria-label="Quenaire home"
            className="inline-flex items-center justify-center"
          >
            <QuenaireMark className="h-14 w-14" />
          </Link>
          <h1 className="mt-4 text-3xl font-bold text-gray-900">Create your workspace</h1>
          <p className="mt-1 text-gray-500">
            Start answering security questionnaires in hours, not weeks.
          </p>
        </div>

        <Card>
          <CardHeader>
            <CardTitle as="h2">Get started</CardTitle>
            <CardDescription>Free for 14 days. No card required.</CardDescription>
          </CardHeader>

          <CardContent>
            <form onSubmit={handleSubmit} className="space-y-4" noValidate>
              <Input
                label="Company name"
                value={companyName}
                onChange={(e) => setCompanyName(e.target.value)}
                placeholder="Acme Corp"
                autoComplete="organization"
                required
              />

              {/* Workspace address, derived from the company name until edited */}
              <div>
                <label htmlFor="workspace" className="mb-1 block text-sm font-medium text-gray-700">
                  Workspace address
                </label>
                <div className="flex items-center gap-2">
                  <span className="shrink-0 text-sm text-gray-600">/trust/</span>
                  <div className="relative flex-1">
                    <input
                      id="workspace"
                      value={slugOverride || slugify(companyName)}
                      onChange={(e) => setSlugOverride(e.target.value)}
                      placeholder="acme-corp"
                      className="h-10 w-full rounded-lg border border-gray-300 bg-white px-3 pr-9 text-sm focus:border-transparent focus:outline-none focus:ring-2 focus:ring-primary-500"
                      aria-describedby="workspace-hint"
                    />
                    <span className="absolute inset-y-0 right-2.5 flex items-center">
                      {availability === 'checking' && (
                        <Loader2 className="h-4 w-4 animate-spin text-gray-300" />
                      )}
                      {availability === 'available' && <Check className="h-4 w-4 text-green-600" />}
                      {availability === 'taken' && <X className="h-4 w-4 text-red-600" />}
                    </span>
                  </div>
                </div>
                <p
                  id="workspace-hint"
                  className={`mt-1 text-xs ${
                    availability === 'taken' ? 'text-red-600' : 'text-gray-500'
                  }`}
                >
                  {availability === 'taken'
                    ? 'That address is already taken.'
                    : 'Your public Trust Center will live here.'}
                </p>
              </div>

              <div className="grid gap-4 sm:grid-cols-2">
                <Input
                  label="First name"
                  value={firstName}
                  onChange={(e) => setFirstName(e.target.value)}
                  placeholder="Alex"
                  autoComplete="given-name"
                />
                <Input
                  label="Last name"
                  value={lastName}
                  onChange={(e) => setLastName(e.target.value)}
                  placeholder="Chen"
                  autoComplete="family-name"
                />
              </div>

              <Input
                label="Work email"
                type="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                placeholder="alex@acme.com"
                autoComplete="email"
                error={emailInvalid ? 'Enter a valid email address' : undefined}
                required
              />

              <Input
                label="Password"
                type="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                autoComplete="new-password"
                error={passwordTooShort ? `At least ${MIN_PASSWORD_LENGTH} characters` : undefined}
                helperText={
                  !passwordTooShort ? `At least ${MIN_PASSWORD_LENGTH} characters` : undefined
                }
                required
              />

              <Input
                label="Confirm password"
                type="password"
                value={confirmPassword}
                onChange={(e) => setConfirmPassword(e.target.value)}
                autoComplete="new-password"
                error={passwordsMismatch ? 'Passwords do not match' : undefined}
                required
              />

              {status && !status.identityProviderConfigured && (
                <div className="flex items-start gap-2 rounded-lg border border-amber-200 bg-amber-50 p-3">
                  <AlertCircle className="mt-0.5 h-4 w-4 shrink-0 text-amber-600" />
                  <p className="text-xs text-amber-800">
                    Keycloak is not configured on this instance. Your workspace will be created,
                    but the password will not be set and sign-in must be finished by an
                    administrator.
                  </p>
                </div>
              )}

              {error && (
                <div className="flex items-start gap-2 rounded-lg border border-red-200 bg-red-50 p-3">
                  <AlertCircle className="mt-0.5 h-4 w-4 shrink-0 text-red-600" />
                  <p className="text-sm text-red-700">{error}</p>
                </div>
              )}

              <Button type="submit" size="lg" className="w-full" disabled={!canSubmit}>
                {submitting ? (
                  <>
                    <Loader2 className="mr-2 h-5 w-5 animate-spin" />
                    Creating workspace…
                  </>
                ) : (
                  'Create workspace'
                )}
              </Button>

              <p className="text-center text-xs text-gray-600">
                By creating a workspace you agree to the terms of service.
              </p>
            </form>
          </CardContent>
        </Card>

        <p className="mt-6 text-center text-sm text-gray-500">
          Already have an account?{' '}
          <Link href="/login" className="font-medium text-primary-600 hover:underline">
            Sign in
          </Link>
        </p>
      </div>
    </main>
  );
}

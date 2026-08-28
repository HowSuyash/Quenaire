import { RegisterPayload, RegisterResult, SignupStatus } from '@/types/auth';

// Same origin; see the note in api.ts.
const API_URL = process.env.NEXT_PUBLIC_API_URL ?? '';

/**
 * Signup talks to the API directly with plain `fetch` rather than the shared
 * axios client: there is no token to attach yet, and these endpoints are
 * deliberately public.
 */
export async function getSignupStatus(): Promise<SignupStatus> {
  try {
    const response = await fetch(`${API_URL}/api/v1/auth/signup-status`, { cache: 'no-store' });
    if (!response.ok) throw new Error('unavailable');
    return await response.json();
  } catch {
    // Assume signup is open so the form still renders when the API is down;
    // submitting will surface the real error.
    return { signupEnabled: true, identityProviderConfigured: false };
  }
}

export async function registerWorkspace(payload: RegisterPayload): Promise<RegisterResult> {
  try {
    const response = await fetch(`${API_URL}/api/v1/auth/register`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    });

    const data = await response.json().catch(() => ({}));

    if (!response.ok || data.success === false) {
      return { success: false, error: data.error ?? 'Could not create the workspace.' };
    }
    return data as RegisterResult;
  } catch {
    return { success: false, error: 'Could not reach the server. Is the API running?' };
  }
}

export async function checkWorkspaceAvailable(slug: string): Promise<boolean | null> {
  if (!slug) return null;
  try {
    const response = await fetch(
      `${API_URL}/api/v1/auth/workspace-available?slug=${encodeURIComponent(slug)}`,
      { cache: 'no-store' }
    );
    if (!response.ok) return null;
    const data = await response.json();
    return Boolean(data.available);
  } catch {
    return null;
  }
}

/** Mirrors the server's slug normalisation so the preview matches what is stored. */
export function slugify(raw: string): string {
  return raw
    .trim()
    .toLowerCase()
    .replace(/[\s_]+/g, '-')
    .replace(/[^a-z0-9-]/g, '')
    .replace(/-{2,}/g, '-')
    .replace(/^-|-$/g, '')
    .slice(0, 63)
    .replace(/-$/, '');
}

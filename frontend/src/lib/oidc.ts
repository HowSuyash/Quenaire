'use client';

/**
 * The browser half of sign-in.
 *
 * Keycloak owns the login page and the password; this app only ever handles the
 * authorization code and the token that comes back. That is why there is no
 * login endpoint on the backend: credentials never pass through it.
 *
 * Sign-in lives under /auth on this app's own origin, proxied to Keycloak, so a
 * deployment needs one hostname rather than two.
 */

const VERIFIER_KEY = 'complyai_pkce_verifier';
const STATE_KEY = 'complyai_oidc_state';

export const REALM_PATH = '/auth/realms/complyai/protocol/openid-connect';

function clientId(): string {
  return process.env.NEXT_PUBLIC_KEYCLOAK_CLIENT_ID || 'complyai-frontend';
}

function redirectUri(): string {
  return `${window.location.origin}/callback`;
}

function randomString(bytes = 32): string {
  const buffer = new Uint8Array(bytes);
  crypto.getRandomValues(buffer);
  return base64Url(buffer);
}

function base64Url(bytes: Uint8Array): string {
  let binary = '';
  bytes.forEach((b) => {
    binary += String.fromCharCode(b);
  });
  return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

async function challengeFor(verifier: string): Promise<string> {
  const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(verifier));
  return base64Url(new Uint8Array(digest));
}

/**
 * Sends the browser to Keycloak to sign in.
 *
 * PKCE is not optional here. This is a public client, so it holds no secret, and
 * the authorization code comes back through the address bar -- anything that can
 * read it could redeem it. The verifier stays in this tab and never travels with
 * the code.
 */
export async function beginLogin(): Promise<void> {
  const verifier = randomString();
  const state = randomString(16);

  sessionStorage.setItem(VERIFIER_KEY, verifier);
  sessionStorage.setItem(STATE_KEY, state);

  const params = new URLSearchParams({
    client_id: clientId(),
    redirect_uri: redirectUri(),
    response_type: 'code',
    scope: 'openid profile email',
    state,
    code_challenge: await challengeFor(verifier),
    code_challenge_method: 'S256',
  });

  window.location.href = `${REALM_PATH}/auth?${params.toString()}`;
}

export interface TokenSet {
  access_token: string;
  refresh_token?: string;
  expires_in?: number;
}

/**
 * Exchanges the code Keycloak sent back for a token.
 *
 * @throws if `state` does not match the one this tab generated, which is how a
 *   code injected by another site is refused.
 */
export async function completeLogin(code: string, state: string): Promise<TokenSet> {
  const verifier = sessionStorage.getItem(VERIFIER_KEY);
  const expectedState = sessionStorage.getItem(STATE_KEY);

  sessionStorage.removeItem(VERIFIER_KEY);
  sessionStorage.removeItem(STATE_KEY);

  if (!verifier) {
    throw new Error('This sign-in did not start in this tab. Please sign in again.');
  }
  if (!expectedState || state !== expectedState) {
    throw new Error('Sign-in could not be verified. Please sign in again.');
  }

  const response = await fetch(`${REALM_PATH}/token`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({
      grant_type: 'authorization_code',
      client_id: clientId(),
      code,
      redirect_uri: redirectUri(),
      code_verifier: verifier,
    }),
  });

  if (!response.ok) {
    throw new Error('Sign-in failed. Please try again.');
  }

  return response.json();
}

/** Ends the Keycloak session as well as this app's, so a second login is a real one. */
export function logoutUrl(): string {
  const params = new URLSearchParams({
    client_id: clientId(),
    post_logout_redirect_uri: window.location.origin,
  });
  return `${REALM_PATH}/logout?${params.toString()}`;
}

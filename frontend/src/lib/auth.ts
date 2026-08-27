'use client';

import Cookies from 'js-cookie';

const TOKEN_KEY = 'complyai_token';
const REFRESH_TOKEN_KEY = 'complyai_refresh_token';
const USER_KEY = 'complyai_user';

export function getToken(): string | undefined {
  if (typeof window === 'undefined') return undefined;
  return Cookies.get(TOKEN_KEY);
}

export function getRefreshToken(): string | undefined {
  if (typeof window === 'undefined') return undefined;
  return Cookies.get(REFRESH_TOKEN_KEY);
}

export function setToken(token: string, refreshToken?: string): void {
  if (typeof window === 'undefined') return;
  Cookies.set(TOKEN_KEY, token, { expires: 1, secure: process.env.NODE_ENV === 'production', sameSite: 'lax' });
  if (refreshToken) {
    Cookies.set(REFRESH_TOKEN_KEY, refreshToken, { expires: 30, secure: process.env.NODE_ENV === 'production', sameSite: 'lax' });
  }
}

export function removeToken(): void {
  if (typeof window === 'undefined') return;
  Cookies.remove(TOKEN_KEY);
  Cookies.remove(REFRESH_TOKEN_KEY);
  Cookies.remove(USER_KEY);
}

export function getUser(): User | null {
  if (typeof window === 'undefined') return null;
  const userStr = Cookies.get(USER_KEY);
  if (!userStr) return null;
  try {
    return JSON.parse(userStr);
  } catch {
    return null;
  }
}

export function setUser(user: User): void {
  if (typeof window === 'undefined') return;
  Cookies.set(USER_KEY, JSON.stringify(user), { expires: 1, secure: process.env.NODE_ENV === 'production', sameSite: 'lax' });
}

export function isAuthenticated(): boolean {
  return !!getToken();
}

export interface User {
  id: string;
  email: string;
  firstName?: string;
  lastName?: string;
  role: string;
  tenantId: string;
}
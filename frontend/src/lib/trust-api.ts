import { TrustLeadPayload, TrustProfile } from '@/types/trust';

// Same origin; see the note in api.ts.
const API_URL = process.env.NEXT_PUBLIC_API_URL ?? '';

/**
 * Trust Center reads happen unauthenticated and often server-side, so this
 * deliberately uses plain `fetch` rather than the token-attaching axios client.
 */
export async function fetchTrustProfile(slug: string): Promise<TrustProfile | null> {
  try {
    const response = await fetch(`${API_URL}/api/vanta/trust/${encodeURIComponent(slug)}`, {
      // Match the 5-minute Cache-Control the API sends.
      next: { revalidate: 300 },
    });
    if (!response.ok) return null;
    return (await response.json()) as TrustProfile;
  } catch {
    return null;
  }
}

export async function submitTrustLead(
  slug: string,
  payload: TrustLeadPayload
): Promise<{ success: boolean; message: string }> {
  try {
    const response = await fetch(`${API_URL}/api/vanta/trust/${encodeURIComponent(slug)}/leads`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    });

    const data = await response.json().catch(() => ({}));
    return {
      success: response.ok && data.success !== false,
      message: data.message ?? (response.ok ? 'Request received.' : 'Could not send your request.'),
    };
  } catch {
    return { success: false, message: 'Could not reach the security team. Please try again.' };
  }
}

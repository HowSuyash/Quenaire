import { NextRequest, NextResponse } from 'next/server';
import { readBackendJson } from '../../../../../../lib/proxy';

/**
 * Same-origin proxy for the SME review queue.
 *
 * The dashboard normally talks to the backend directly through `lib/api.ts`;
 * this route exists for deployments that keep the API off-origin and need the
 * browser to stay on the Next.js origin. Previously it returned a hardcoded
 * fixture in a shape the backend never produced.
 */
export async function GET(request: NextRequest) {
  const { searchParams } = new URL(request.url);
  const backendUrl = process.env.BACKEND_URL || 'http://localhost:8080';

  const target = new URL(`${backendUrl}/api/vanta/agent/sme/queue`);
  for (const key of ['questionnaireId', 'smeUserId', 'status', 'confidence', 'search', 'overdueOnly', 'mine']) {
    const value = searchParams.get(key);
    if (value) target.searchParams.set(key, value);
  }

  try {
    const response = await fetch(target, {
      headers: {
        Authorization: request.headers.get('Authorization') || '',
      },
      cache: 'no-store',
    });

    const data = await readBackendJson(response);
    return NextResponse.json(data, { status: response.status });
  } catch (error) {
    console.error('Review queue proxy failed:', error);
    return NextResponse.json(
      { items: [], stats: null, error: 'Review queue is unavailable' },
      { status: 502 }
    );
  }
}

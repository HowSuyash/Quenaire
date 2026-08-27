import { NextRequest, NextResponse } from 'next/server';

/** Same-origin proxy for review queue counters. See ../route.ts. */
export async function GET(request: NextRequest) {
  const { searchParams } = new URL(request.url);
  const backendUrl = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080';

  const target = new URL(`${backendUrl}/api/vanta/agent/sme/queue/stats`);
  const questionnaireId = searchParams.get('questionnaireId');
  if (questionnaireId) target.searchParams.set('questionnaireId', questionnaireId);

  try {
    const response = await fetch(target, {
      headers: {
        Authorization: request.headers.get('Authorization') || '',
      },
      cache: 'no-store',
    });

    const data = await response.json();
    return NextResponse.json(data, { status: response.status });
  } catch (error) {
    console.error('Review queue stats proxy failed:', error);
    return NextResponse.json({ error: 'Review queue stats are unavailable' }, { status: 502 });
  }
}

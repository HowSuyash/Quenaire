import { NextRequest, NextResponse } from 'next/server';
import { readBackendJson } from '../../../../../../../lib/proxy';

export async function POST(
  request: NextRequest,
  { params }: { params: { id: string } }
) {
  try {
    const { id } = params;
    const body = await request.json();
    
    // In production, forward to backend API
    const backendUrl = process.env.BACKEND_URL || 'http://localhost:8080';
    const response = await fetch(`${backendUrl}/api/vanta/agent/sme/queue/${id}/approve`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        // Forward auth headers
        'Authorization': request.headers.get('Authorization') || '',
      },
      body: JSON.stringify(body),
    });

    if (!response.ok) {
      const error = await readBackendJson(response);
      return NextResponse.json(
        { success: false, error: error?.error || 'Failed to approve' },
        { status: response.status }
      );
    }

    const data = await readBackendJson(response);
    return NextResponse.json(data);
  } catch (error) {
    console.error('Approve answer error:', error);
    return NextResponse.json(
      { success: false, error: 'Failed to approve answer' },
      { status: 500 }
    );
  }
}
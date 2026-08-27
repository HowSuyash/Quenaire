import { NextRequest, NextResponse } from 'next/server';

export async function GET(
  request: NextRequest,
  { params }: { params: { token: string } }
) {
  const token = params.token;
  
  // In production, validate against database
  // For now, return mock data for demo
  return NextResponse.json({
    valid: true,
    token,
    expiresAt: new Date(Date.now() + 30 * 24 * 60 * 60 * 1000).toISOString(),
    vendorName: 'Demo Vendor',
  });
}
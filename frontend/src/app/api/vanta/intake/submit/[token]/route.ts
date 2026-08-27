import { NextRequest, NextResponse } from 'next/server';

export const runtime = 'nodejs';
export const maxDuration = 60;

export async function POST(
  request: NextRequest,
  { params }: { params: { token: string } }
) {
  try {
    const token = params.token;
    
    // In production, validate the token against database
    // For now, accept any token for demo
    if (!token) {
      return NextResponse.json(
        { success: false, error: 'Invalid intake token' },
        { status: 400 }
      );
    }

    const formData = await request.formData();
    const file = formData.get('file') as File | null;
    const vendorName = formData.get('vendorName') as string;
    const vendorEmail = formData.get('vendorEmail') as string;
    const portalUrl = formData.get('portalUrl') as string | null;

    if (!file) {
      return NextResponse.json(
        { success: false, error: 'No file uploaded' },
        { status: 400 }
      );
    }

    if (!vendorName || !vendorEmail) {
      return NextResponse.json(
        { success: false, error: 'Vendor name and email are required' },
        { status: 400 }
      );
    }

    // Validate file type
    const validTypes = [
      'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
      'application/vnd.ms-excel',
      'application/pdf',
      'application/msword',
      'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
    ];

    if (!validTypes.includes(file.type)) {
      return NextResponse.json(
        { success: false, error: 'Invalid file type. Please upload Excel, PDF, or Word files.' },
        { status: 400 }
      );
    }

    if (file.size > 50 * 1024 * 1024) {
      return NextResponse.json(
        { success: false, error: 'File too large. Maximum size is 50MB.' },
        { status: 400 }
      );
    }

    // Convert file to buffer
    const arrayBuffer = await file.arrayBuffer();
    const buffer = Buffer.from(arrayBuffer);

    // Forward to backend API
    const backendUrl = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080';
    
    const backendFormData = new FormData();
    backendFormData.append('file', new Blob([buffer]), file.name);
    backendFormData.append('vendorName', vendorName);
    backendFormData.append('vendorEmail', vendorEmail);
    if (portalUrl) {
      backendFormData.append('portalUrl', portalUrl);
    }

    try {
      const response = await fetch(`${backendUrl}/api/vanta/intake/submit/${token}`, {
        method: 'POST',
        body: backendFormData,
        headers: {
          // Forward any auth headers if needed
        },
      });

      if (!response.ok) {
        const error = await response.json();
        return NextResponse.json(
          { success: false, error: error.error || 'Failed to submit to backend' },
          { status: response.status }
        );
      }

      const data = await response.json();
      return NextResponse.json(data);
    } catch (error) {
      console.error('Intake submission error:', error);
      return NextResponse.json(
        { success: false, error: 'Failed to submit questionnaire' },
        { status: 500 }
      );
    }
  } catch (error) {
    console.error('Intake submission error:', error);
    return NextResponse.json(
      { success: false, error: 'Failed to submit questionnaire' },
      { status: 500 }
    );
  }
}

export async function GET(
  request: NextRequest,
  { params }: { params: { token: string } }
) {
  // Allow checking token status via GET
  const token = params.token;
  
  if (!token) {
    return NextResponse.json(
      { valid: false, error: 'Token required' },
      { status: 400 }
    );
  }

  // In production, validate against database
  return NextResponse.json({
    valid: true,
    token,
    expiresAt: new Date(Date.now() + 30 * 24 * 60 * 60 * 1000).toISOString(),
    vendorName: 'Demo Vendor',
  });
}
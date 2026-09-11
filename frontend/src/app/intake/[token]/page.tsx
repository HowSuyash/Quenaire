'use client';

import { useState, useEffect, useCallback, useRef } from 'react';
import { QuenaireMark } from '@/components/brand/QuenaireMark';
import { useParams } from 'next/navigation';
import { Upload, FileText, AlertCircle, Loader2, Shield, X } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/Card';
import { Badge } from '@/components/ui/Badge';

interface IntakeStatus {
  valid: boolean;
  token: string;
  expiresAt: string;
  vendorName: string;
  questionnaireId?: string;
  status: 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED';
  progress: number;
  currentStage: string;
  error?: string;
}

const VALID_MIME_TYPES = [
  'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
  'application/vnd.ms-excel',
  'application/pdf',
  'application/msword',
  'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
];

const MAX_FILE_BYTES = 50 * 1024 * 1024;

function statusBadgeClass(status: string): string {
  switch (status) {
    case 'COMPLETED': return 'bg-green-100 text-green-800';
    case 'PROCESSING': return 'bg-blue-100 text-blue-800';
    case 'PENDING': return 'bg-yellow-100 text-yellow-800';
    case 'FAILED': return 'bg-red-100 text-red-800';
    default: return 'bg-gray-100 text-gray-800';
  }
}

export default function IntakePage() {
  const params = useParams();
  const token = params.token as string;

  const [status, setStatus] = useState<IntakeStatus | null>(null);
  const [loading, setLoading] = useState(true);
  const [uploading, setUploading] = useState(false);
  const [vendorName, setVendorName] = useState('');
  const [vendorEmail, setVendorEmail] = useState('');
  const [portalUrl, setPortalUrl] = useState('');
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [uploadProgress, setUploadProgress] = useState(0);
  const [dragActive, setDragActive] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);

  const pollRef = useRef<ReturnType<typeof setInterval> | null>(null);

  const checkTokenStatus = useCallback(async () => {
    try {
      const response = await fetch(`/api/vanta/intake/links/${token}/status`);
      if (!response.ok) throw new Error('invalid');
      const data = await response.json();
      setStatus({
        valid: data.valid !== false,
        token: data.token ?? token,
        expiresAt: data.expiresAt ?? '',
        vendorName: data.vendorName ?? '',
        questionnaireId: data.questionnaireId,
        status: data.status || 'PENDING',
        progress: data.progress ?? 0,
        currentStage: data.currentStage || 'Waiting for submission',
        error: data.error,
      });
    } catch {
      setStatus({
        valid: false,
        token,
        expiresAt: '',
        vendorName: '',
        status: 'FAILED',
        progress: 0,
        currentStage: 'Invalid or expired link',
        error: 'This intake link is invalid or has expired.',
      });
    } finally {
      setLoading(false);
    }
  }, [token]);

  useEffect(() => {
    checkTokenStatus();
  }, [checkTokenStatus]);

  // Clean up any in-flight poll when the component unmounts.
  useEffect(() => () => {
    if (pollRef.current) clearInterval(pollRef.current);
  }, []);

  const pollForStatus = useCallback((questionnaireId: string) => {
    if (pollRef.current) clearInterval(pollRef.current);

    pollRef.current = setInterval(async () => {
      try {
        const response = await fetch(
          `/api/vanta/agent/questionnaires/${questionnaireId}/orchestration/status`
        );
        if (!response.ok) return;

        const data = await response.json();
        setStatus((prev) => (prev ? {
          ...prev,
          status: data.status ?? prev.status,
          progress: data.progress ?? prev.progress,
          currentStage: data.currentStage ?? prev.currentStage,
        } : prev));

        if (data.status === 'COMPLETED' || data.status === 'FAILED') {
          if (pollRef.current) clearInterval(pollRef.current);
          pollRef.current = null;
        }
      } catch (error) {
        console.error('Polling failed:', error);
      }
    }, 5000);
  }, []);

  const validateFile = (file: File): boolean => {
    if (!VALID_MIME_TYPES.includes(file.type)) {
      setFormError('Invalid file type. Please upload an Excel, PDF, or Word file.');
      return false;
    }
    if (file.size > MAX_FILE_BYTES) {
      setFormError('File too large. Maximum size is 50MB.');
      return false;
    }
    setFormError(null);
    return true;
  };

  const handleDrag = (e: React.DragEvent) => {
    e.preventDefault();
    e.stopPropagation();
    if (e.type === 'dragenter' || e.type === 'dragover') {
      setDragActive(true);
    } else if (e.type === 'dragleave') {
      setDragActive(false);
    }
  };

  const handleDrop = (e: React.DragEvent) => {
    e.preventDefault();
    e.stopPropagation();
    setDragActive(false);
    const file = e.dataTransfer.files?.[0];
    if (file && validateFile(file)) setSelectedFile(file);
  };

  const handleFileSelect = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (file && validateFile(file)) setSelectedFile(file);
  };

  const removeFile = () => {
    setSelectedFile(null);
    setFormError(null);
  };

  const handleSubmit = async () => {
    if (!selectedFile) {
      setFormError('Please select a file to upload.');
      return;
    }
    if (!vendorName.trim()) {
      setFormError('Please enter a vendor name.');
      return;
    }
    if (!vendorEmail.trim()) {
      setFormError('Please enter a vendor email.');
      return;
    }

    setFormError(null);
    setUploading(true);
    setUploadProgress(0);

    const progressInterval = setInterval(() => {
      setUploadProgress((prev) => Math.min(prev + 10, 90));
    }, 200);

    try {
      const formData = new FormData();
      formData.append('file', selectedFile);
      formData.append('vendorName', vendorName);
      formData.append('vendorEmail', vendorEmail);
      if (portalUrl.trim()) formData.append('portalUrl', portalUrl);

      const response = await fetch(`/api/vanta/intake/submit/${token}`, {
        method: 'POST',
        body: formData,
      });

      const data = await response.json();
      setUploadProgress(100);

      if (!data.success) {
        setFormError(data.error || 'Upload failed. Please try again.');
        return;
      }

      setStatus((prev) => (prev ? {
        ...prev,
        status: 'PROCESSING',
        progress: 10,
        currentStage: 'Questionnaire received, processing started',
        questionnaireId: data.questionnaireId,
      } : prev));

      setSelectedFile(null);
      setVendorName('');
      setVendorEmail('');
      setPortalUrl('');

      if (data.questionnaireId) pollForStatus(data.questionnaireId);
    } catch (error) {
      console.error('Upload failed:', error);
      setFormError('Upload failed. Please try again.');
    } finally {
      clearInterval(progressInterval);
      setUploading(false);
      setUploadProgress(0);
    }
  };

  if (loading) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-gray-50">
        <div className="animate-spin rounded-full h-12 w-12 border-4 border-primary-600 border-t-transparent" />
      </div>
    );
  }

  if (!status || !status.valid) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-gray-50 px-4">
        <div className="w-full max-w-md">
          <Card>
            <CardContent className="pt-8 pb-8 px-6 text-center">
              <div className="inline-flex items-center justify-center w-16 h-16 rounded-2xl bg-red-100 mb-4">
                <AlertCircle className="h-8 w-8 text-red-600" />
              </div>
              <h1 className="text-2xl font-bold text-gray-900">Invalid or Expired Link</h1>
              <p className="text-gray-500 mt-2">
                {status?.error || 'This intake link is invalid or has expired.'}
              </p>
              <p className="text-sm text-gray-400 mt-4">
                Please contact the sender for a new intake link.
              </p>
            </CardContent>
          </Card>
        </div>
      </div>
    );
  }

  const isExpired = status.expiresAt ? new Date(status.expiresAt) < new Date() : false;
  const canSubmit = !!selectedFile && !!vendorName.trim() && !!vendorEmail.trim() && !uploading;
  const showUploadForm = !isExpired && status.status !== 'COMPLETED';

  return (
    <div className="min-h-screen bg-gray-50 py-12 px-4">
      <div className="max-w-3xl mx-auto">
        <div className="text-center mb-8">
          <div className="inline-flex items-center justify-center w-16 h-16 rounded-2xl bg-primary-600 mb-4">
            <Shield className="h-8 w-8 text-white" />
          </div>
          <QuenaireMark className="mx-auto mb-3 h-12 w-12" />
          <h1 className="text-3xl font-bold text-gray-900">Quenaire</h1>
          <p className="text-gray-500 mt-1">Security Questionnaire Intake Portal</p>
        </div>

        <Card>
          <CardHeader>
            <div className="flex items-center justify-between">
              <div>
                <CardTitle className="text-xl">{status.vendorName || 'Vendor Intake'}</CardTitle>
                <CardDescription>Security Questionnaire Intake</CardDescription>
              </div>
              <Badge className={statusBadgeClass(status.status)}>{status.status}</Badge>
            </div>
          </CardHeader>
          <CardContent>
            <div className="space-y-4">
              <div>
                <div className="flex justify-between text-sm mb-1">
                  <span className="text-gray-600">Progress</span>
                  <span className="font-medium">{status.progress}%</span>
                </div>
                <div className="h-2 bg-gray-200 rounded-full overflow-hidden">
                  <div
                    className="h-full bg-primary-600 transition-all duration-300"
                    style={{ width: `${status.progress}%` }}
                  />
                </div>
                <p className="text-sm text-gray-500 mt-1">{status.currentStage}</p>
              </div>

              <div className="grid grid-cols-2 gap-4 text-sm">
                <div>
                  <p className="text-gray-500">Vendor</p>
                  <p className="font-medium">{status.vendorName || '—'}</p>
                </div>
                <div>
                  <p className="text-gray-500">Link Expires</p>
                  <p className="font-medium">
                    {status.expiresAt ? new Date(status.expiresAt).toLocaleDateString() : '—'}
                  </p>
                </div>
                {status.questionnaireId && (
                  <div className="col-span-2">
                    <p className="text-gray-500">Questionnaire ID</p>
                    <p className="font-medium font-mono text-xs">{status.questionnaireId}</p>
                  </div>
                )}
              </div>

              {status.error && (
                <div className="p-3 bg-red-50 border border-red-200 rounded-lg text-red-700 text-sm">
                  {status.error}
                </div>
              )}
            </div>
          </CardContent>
        </Card>

        {showUploadForm && (
          <Card className="mt-6">
            <CardHeader>
              <CardTitle>Upload Questionnaire</CardTitle>
              <CardDescription>
                Upload your security questionnaire (Excel, PDF, or Word). Maximum file size: 50MB.
              </CardDescription>
            </CardHeader>
            <CardContent>
              <div className="space-y-4">
                <div className="grid grid-cols-2 gap-4">
                  <Input
                    label="Vendor Name"
                    value={vendorName}
                    onChange={(e) => setVendorName(e.target.value)}
                    placeholder="Acme Corp"
                    disabled={uploading}
                  />
                  <Input
                    label="Vendor Email"
                    type="email"
                    value={vendorEmail}
                    onChange={(e) => setVendorEmail(e.target.value)}
                    placeholder="security@acme.com"
                    disabled={uploading}
                  />
                </div>

                <Input
                  label="Portal URL (optional)"
                  value={portalUrl}
                  onChange={(e) => setPortalUrl(e.target.value)}
                  placeholder="https://onetrust.com/questionnaire/..."
                  disabled={uploading}
                />

                <div
                  className={`border-2 border-dashed rounded-xl p-8 text-center transition-colors ${
                    dragActive
                      ? 'border-primary-500 bg-primary-50'
                      : 'border-gray-300 hover:border-primary-400'
                  }`}
                  onDragEnter={handleDrag}
                  onDragLeave={handleDrag}
                  onDragOver={handleDrag}
                  onDrop={handleDrop}
                >
                  <input
                    type="file"
                    id="questionnaire-file"
                    className="hidden"
                    accept=".xlsx,.xls,.pdf,.doc,.docx"
                    onChange={handleFileSelect}
                    disabled={uploading}
                  />
                  <label htmlFor="questionnaire-file" className="cursor-pointer block">
                    <Upload className="h-12 w-12 mx-auto text-gray-400 mb-4" />
                    {selectedFile ? (
                      <div className="space-y-1">
                        <p className="font-medium text-gray-900">{selectedFile.name}</p>
                        <p className="text-sm text-gray-500">
                          {(selectedFile.size / 1024 / 1024).toFixed(2)} MB
                        </p>
                      </div>
                    ) : (
                      <>
                        <p className="text-lg font-medium text-gray-900 mb-1">
                          Drag &amp; drop your questionnaire here, or click to browse
                        </p>
                        <p className="text-sm text-gray-500">
                          Supports .xlsx, .xls, .pdf, .doc, .docx (max 50MB)
                        </p>
                      </>
                    )}
                  </label>

                  {selectedFile && (
                    <Button
                      variant="ghost"
                      size="sm"
                      onClick={removeFile}
                      disabled={uploading}
                      className="mt-3 text-red-600 hover:text-red-700"
                    >
                      <X className="h-4 w-4 mr-1" />
                      Remove file
                    </Button>
                  )}
                </div>

                {formError && (
                  <div className="p-3 bg-red-50 border border-red-200 rounded-lg text-red-700 text-sm">
                    {formError}
                  </div>
                )}

                {uploading && (
                  <div className="space-y-2">
                    <div className="h-2 bg-gray-200 rounded-full overflow-hidden">
                      <div
                        className="h-full bg-primary-600 transition-all duration-300"
                        style={{ width: `${uploadProgress}%` }}
                      />
                    </div>
                    <p className="text-sm text-gray-600 text-center">
                      Uploading... {uploadProgress}%
                    </p>
                  </div>
                )}

                <Button
                  onClick={handleSubmit}
                  disabled={!canSubmit}
                  className="w-full"
                  size="lg"
                >
                  {uploading ? (
                    <>
                      <Loader2 className="h-5 w-5 animate-spin mr-2" />
                      Processing...
                    </>
                  ) : (
                    <>
                      <FileText className="h-5 w-5 mr-2" />
                      Submit Questionnaire
                    </>
                  )}
                </Button>

                {status.questionnaireId && status.status === 'PROCESSING' && (
                  <div className="text-center text-sm text-gray-500">
                    <p>Processing in background — you can close this page and check back later.</p>
                    <p className="mt-1">
                      <a
                        href={`/dashboard/questionnaires/${status.questionnaireId}`}
                        className="text-primary-600 hover:underline"
                      >
                        View in Dashboard →
                      </a>
                    </p>
                  </div>
                )}
              </div>
            </CardContent>
          </Card>
        )}

        <Card className="mt-6 border-blue-200 bg-blue-50">
          <CardContent className="pt-6">
            <div className="flex items-start gap-3">
              <Shield className="h-5 w-5 text-blue-600 mt-0.5 shrink-0" />
              <div>
                <h3 className="font-medium text-blue-900 mb-1">Secure &amp; Private</h3>
                <p className="text-sm text-blue-700">
                  Your questionnaire data never leaves your infrastructure. Quenaire processes
                  everything within your VPC using local LLMs via Ollama. No data is sent to
                  external AI providers.
                </p>
              </div>
            </div>
          </CardContent>
        </Card>

        <div className="mt-8 text-center text-sm text-gray-500">
          <p>
            Powered by{' '}
            <a
              href="https://quenaire.io"
              target="_blank"
              rel="noreferrer"
              className="text-primary-600 hover:underline"
            >
              Quenaire
            </a>
          </p>
          <p>Self-hosted • AI-Powered • Enterprise Ready</p>
        </div>
      </div>
    </div>
  );
}

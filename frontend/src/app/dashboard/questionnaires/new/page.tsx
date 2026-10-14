'use client';

import { useState } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ArrowLeft, Check, Copy, FileSpreadsheet, Link2, Trash2, Upload } from 'lucide-react';
import { api } from '@/lib/api';
import { IntakeLink } from '@/types/api';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Textarea } from '@/components/ui/Textarea';
import { Badge } from '@/components/ui/Badge';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/Card';
import { formatDateTime } from '@/lib/utils';

const XLSX_MIME_TYPE = 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet';

/**
 * The server's own explanation when it gave one. ApiClient wraps failures in
 * ApiUnavailableError, so the HTTP response may be one level down, on `cause`.
 */
function errorMessage(error: unknown, fallback: string): string {
  type WithResponse = { response?: { data?: { error?: string } }; cause?: unknown };
  const direct = (error as WithResponse)?.response?.data?.error;
  const wrapped = ((error as WithResponse)?.cause as WithResponse)?.response?.data?.error;
  return direct || wrapped || fallback;
}

const STATE_BADGE: Record<IntakeLink['state'], string> = {
  PENDING: 'bg-yellow-100 text-yellow-800',
  SUBMITTED: 'bg-green-100 text-green-800',
  EXPIRED: 'bg-gray-100 text-gray-700',
};

/**
 * Where a questionnaire starts. The "New Questionnaire" button has always linked
 * here, and there was no page: the URL fell through to the questionnaire detail
 * route with an id of "new". Customers had no way to upload a questionnaire from
 * the dashboard at all.
 */
export default function NewQuestionnairePage() {
  const router = useRouter();
  const queryClient = useQueryClient();

  const [file, setFile] = useState<File | null>(null);
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [uploadError, setUploadError] = useState<string | null>(null);

  const [vendorName, setVendorName] = useState('');
  const [vendorEmail, setVendorEmail] = useState('');
  const [expiresInDays, setExpiresInDays] = useState(30);
  const [linkError, setLinkError] = useState<string | null>(null);
  const [createdUrl, setCreatedUrl] = useState<string | null>(null);
  const [copied, setCopied] = useState(false);

  const upload = useMutation({
    mutationFn: () => {
      const form = new FormData();
      form.append('file', file as File);
      if (title.trim()) form.append('title', title.trim());
      if (description.trim()) form.append('description', description.trim());
      return api.createQuestionnaire(form);
    },
    onSuccess: (questionnaire) => {
      queryClient.invalidateQueries({ queryKey: ['questionnaires'] });
      router.push(`/dashboard/questionnaires/${questionnaire.id}`);
    },
    onError: (error) => {
      setUploadError(
        errorMessage(error, 'The questionnaire could not be uploaded. Check the file and try again.')
      );
    },
  });

  const { data: links } = useQuery({
    queryKey: ['intake-links'],
    queryFn: () => api.getIntakeLinks(),
  });

  const createLink = useMutation({
    mutationFn: () =>
      api.createIntakeLink({
        vendorName: vendorName.trim(),
        vendorEmail: vendorEmail.trim(),
        expiresInDays,
      }),
    onSuccess: ({ intakeUrl }) => {
      setCreatedUrl(intakeUrl);
      setCopied(false);
      setVendorName('');
      setVendorEmail('');
      queryClient.invalidateQueries({ queryKey: ['intake-links'] });
    },
    onError: (error) => setLinkError(errorMessage(error, 'The link could not be created.')),
  });

  const revokeLink = useMutation({
    mutationFn: (id: string) => api.revokeIntakeLink(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['intake-links'] }),
  });

  const chooseFile = (chosen: File | undefined) => {
    if (!chosen) return;
    // Some browsers report no MIME type for a workbook, so the extension decides too.
    if (chosen.type !== XLSX_MIME_TYPE && !chosen.name.toLowerCase().endsWith('.xlsx')) {
      setUploadError('Questionnaires are read from Excel workbooks (.xlsx).');
      setFile(null);
      return;
    }
    setUploadError(null);
    setFile(chosen);
    if (!title.trim()) setTitle(chosen.name.replace(/\.xlsx$/i, ''));
  };

  const copyUrl = async () => {
    if (!createdUrl) return;
    try {
      await navigator.clipboard.writeText(createdUrl);
      setCopied(true);
    } catch {
      // Clipboard access can be refused; the URL is on screen to copy by hand.
    }
  };

  return (
    <div className="space-y-6">
      <div>
        <Link
          href="/dashboard/questionnaires"
          className="inline-flex items-center text-sm text-gray-500 hover:text-gray-700"
        >
          <ArrowLeft className="h-4 w-4 mr-1" />
          Questionnaires
        </Link>
        <h1 className="text-3xl font-bold text-gray-900 mt-2">New questionnaire</h1>
        <p className="text-gray-500 mt-1">
          Upload the workbook you were sent, or send a link to whoever has it.
        </p>
      </div>

      <div className="grid gap-6 lg:grid-cols-2">
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <Upload className="h-5 w-5" />
              Upload a workbook
            </CardTitle>
            <CardDescription>
              An Excel workbook (.xlsx), up to 50MB. Questions are found from the column headers.
            </CardDescription>
          </CardHeader>
          <CardContent>
            <form
              className="space-y-4"
              onSubmit={(e) => {
                e.preventDefault();
                if (!file) {
                  setUploadError('Choose a workbook to upload.');
                  return;
                }
                setUploadError(null);
                upload.mutate();
              }}
            >
              <label
                htmlFor="questionnaire-file"
                className="flex flex-col items-center justify-center border-2 border-dashed border-gray-300 rounded-xl p-6 text-center cursor-pointer hover:border-primary-400"
              >
                <FileSpreadsheet className="h-10 w-10 text-gray-500 mb-2" />
                {file ? (
                  <span className="font-medium text-gray-900">{file.name}</span>
                ) : (
                  <span className="text-gray-600">Click to choose a .xlsx file</span>
                )}
                <input
                  id="questionnaire-file"
                  type="file"
                  className="hidden"
                  accept={`.xlsx,${XLSX_MIME_TYPE}`}
                  onChange={(e) => chooseFile(e.target.files?.[0])}
                  disabled={upload.isPending}
                />
              </label>

              <Input
                label="Title"
                value={title}
                onChange={(e) => setTitle(e.target.value)}
                placeholder="Northbridge vendor assessment"
                disabled={upload.isPending}
              />
              <Textarea
                label="Notes (optional)"
                value={description}
                onChange={(e) => setDescription(e.target.value)}
                placeholder="Who sent it, when it is due"
                rows={3}
                disabled={upload.isPending}
              />

              {uploadError && (
                <p className="text-sm text-red-600" role="alert">
                  {uploadError}
                </p>
              )}

              <Button type="submit" className="w-full" loading={upload.isPending} disabled={!file}>
                {upload.isPending ? 'Reading questions…' : 'Upload and read questions'}
              </Button>
            </form>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <Link2 className="h-5 w-5" />
              Request it with a link
            </CardTitle>
            <CardDescription>
              The recipient uploads the workbook without an account and it appears here. Each link
              works once.
            </CardDescription>
          </CardHeader>
          <CardContent className="space-y-4">
            <form
              className="space-y-4"
              onSubmit={(e) => {
                e.preventDefault();
                setLinkError(null);
                createLink.mutate();
              }}
            >
              <div className="grid gap-4 sm:grid-cols-2">
                <Input
                  label="Company"
                  value={vendorName}
                  onChange={(e) => setVendorName(e.target.value)}
                  placeholder="Northbridge Bank"
                  required
                />
                <Input
                  label="Contact email"
                  type="email"
                  value={vendorEmail}
                  onChange={(e) => setVendorEmail(e.target.value)}
                  placeholder="vendor-risk@northbridge.example"
                  required
                />
              </div>
              <div>
                <label htmlFor="expires-in" className="block text-sm font-medium text-gray-700 mb-1">
                  Expires after
                </label>
                <select
                  id="expires-in"
                  value={expiresInDays}
                  onChange={(e) => setExpiresInDays(Number(e.target.value))}
                  className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm"
                >
                  {[7, 14, 30, 90].map((days) => (
                    <option key={days} value={days}>
                      {days} days
                    </option>
                  ))}
                </select>
              </div>

              {linkError && (
                <p className="text-sm text-red-600" role="alert">
                  {linkError}
                </p>
              )}

              <Button type="submit" variant="outline" className="w-full" loading={createLink.isPending}>
                Create link
              </Button>
            </form>

            {createdUrl && (
              <div className="rounded-lg border border-green-200 bg-green-50 p-3 space-y-2">
                <p className="text-sm text-green-900">
                  Send this link now. It is shown only once; only a fingerprint of it is stored.
                </p>
                <div className="flex gap-2">
                  <input
                    readOnly
                    value={createdUrl}
                    onFocus={(e) => e.target.select()}
                    className="flex-1 min-w-0 rounded-md border border-green-300 bg-white px-2 py-1 text-xs font-mono"
                    aria-label="Intake link"
                  />
                  <Button type="button" size="sm" variant="outline" onClick={copyUrl}>
                    {copied ? <Check className="h-4 w-4" /> : <Copy className="h-4 w-4" />}
                  </Button>
                </div>
              </div>
            )}

            {links && links.length > 0 && (
              <div className="pt-2">
                <h3 className="text-sm font-medium text-gray-700 mb-2">Links you have sent</h3>
                <ul className="divide-y divide-gray-100">
                  {links.map((link) => (
                    <li key={link.id} className="flex items-center justify-between gap-3 py-2">
                      <div className="min-w-0">
                        <p className="text-sm font-medium text-gray-900 truncate">{link.vendorName}</p>
                        <p className="text-xs text-gray-500 truncate">
                          {link.state === 'SUBMITTED' && link.submittedAt
                            ? `Received ${formatDateTime(link.submittedAt)}`
                            : `Expires ${formatDateTime(link.expiresAt)}`}
                        </p>
                      </div>
                      <div className="flex items-center gap-2 shrink-0">
                        <Badge className={STATE_BADGE[link.state]}>{link.state}</Badge>
                        {link.state === 'SUBMITTED' && link.questionnaireId ? (
                          <Link
                            href={`/dashboard/questionnaires/${link.questionnaireId}`}
                            className="text-sm text-primary-600 hover:underline"
                          >
                            Open
                          </Link>
                        ) : (
                          link.state === 'PENDING' && (
                            <Button
                              type="button"
                              size="icon"
                              variant="ghost"
                              aria-label={`Revoke the link sent to ${link.vendorName}`}
                              onClick={() => revokeLink.mutate(link.id)}
                              disabled={revokeLink.isPending}
                            >
                              <Trash2 className="h-4 w-4 text-gray-500" />
                            </Button>
                          )
                        )}
                      </div>
                    </li>
                  ))}
                </ul>
              </div>
            )}
          </CardContent>
        </Card>
      </div>
    </div>
  );
}

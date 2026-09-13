'use client';

import { useState } from 'react';
import { CheckCircle, Loader2, Send } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Textarea } from '@/components/ui/Textarea';
import { submitTrustLead } from '@/lib/trust-api';
import { TrustCta, TrustItem } from '@/types/trust';

interface LeadCaptureFormProps {
  slug: string;
  cta: TrustCta;
  /** Documents a visitor can ask for, offered as a dropdown. */
  requestableItems: TrustItem[];
  accentColor: string;
}

export function LeadCaptureForm({ slug, cta, requestableItems, accentColor }: LeadCaptureFormProps) {
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [company, setCompany] = useState('');
  const [roleTitle, setRoleTitle] = useState('');
  const [message, setMessage] = useState('');
  const [requestedItemId, setRequestedItemId] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [result, setResult] = useState<{ success: boolean; message: string } | null>(null);

  if (!cta.enabled) return null;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setSubmitting(true);
    setResult(null);

    const response = await submitTrustLead(slug, {
      name,
      email,
      company: company || undefined,
      roleTitle: roleTitle || undefined,
      message: message || undefined,
      requestedItemId: requestedItemId || undefined,
      sourceUrl: typeof window !== 'undefined' ? window.location.href : undefined,
    });

    setResult(response);
    setSubmitting(false);

    if (response.success) {
      setName('');
      setEmail('');
      setCompany('');
      setRoleTitle('');
      setMessage('');
      setRequestedItemId('');
    }
  };

  if (result?.success) {
    return (
      <div className="rounded-2xl border border-green-200 bg-green-50 p-8 text-center">
        <CheckCircle className="mx-auto mb-3 h-10 w-10 text-green-600" />
        <h3 className="text-lg font-semibold text-green-900">Request sent</h3>
        <p className="mt-1 text-sm text-green-800">{result.message}</p>
      </div>
    );
  }

  return (
    <form
      onSubmit={handleSubmit}
      className="rounded-2xl border border-gray-200 bg-white p-6 shadow-sm sm:p-8"
      id="request-access"
    >
      <h3 className="text-xl font-semibold text-gray-900">
        {cta.heading || 'Need our full security package?'}
      </h3>
      {cta.body && <p className="mt-1.5 text-sm text-gray-600">{cta.body}</p>}

      <div className="mt-5 grid gap-4 sm:grid-cols-2">
        <Input
          label="Your name"
          value={name}
          onChange={(e) => setName(e.target.value)}
          required
          disabled={submitting}
          placeholder="Jordan Ellis"
        />
        <Input
          label="Work email"
          type="email"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          required
          disabled={submitting}
          placeholder="jordan@company.com"
        />
        <Input
          label="Company"
          value={company}
          onChange={(e) => setCompany(e.target.value)}
          disabled={submitting}
          placeholder="Company Inc."
        />
        <Input
          label="Role"
          value={roleTitle}
          onChange={(e) => setRoleTitle(e.target.value)}
          disabled={submitting}
          placeholder="Security Analyst"
        />
      </div>

      {requestableItems.length > 0 && (
        <div className="mt-4">
          <label
            htmlFor="requested-item"
            className="mb-1 block text-sm font-medium text-gray-700"
          >
            What do you need?
          </label>
          <select
            id="requested-item"
            value={requestedItemId}
            onChange={(e) => setRequestedItemId(e.target.value)}
            disabled={submitting}
            className="h-10 w-full rounded-lg border border-gray-300 bg-white px-3 text-sm focus:border-transparent focus:outline-none focus:ring-2 focus:ring-primary-500"
          >
            <option value="">Anything relevant to our review</option>
            {requestableItems.map((item) => (
              <option key={item.id} value={item.id}>
                {item.title}
              </option>
            ))}
          </select>
        </div>
      )}

      <div className="mt-4">
        <Textarea
          label="Anything else?"
          value={message}
          onChange={(e) => setMessage(e.target.value)}
          disabled={submitting}
          rows={3}
          placeholder="We are evaluating you as a sub-processor and need your SOC 2 report."
        />
      </div>

      {result && !result.success && (
        <p className="mt-3 rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-700">
          {result.message}
        </p>
      )}

      <Button
        type="submit"
        size="lg"
        className="mt-5 w-full"
        disabled={submitting}
        style={{ backgroundColor: accentColor }}
      >
        {submitting ? (
          <>
            <Loader2 className="mr-2 h-5 w-5 animate-spin" />
            Sending…
          </>
        ) : (
          <>
            <Send className="mr-2 h-4 w-4" />
            {cta.buttonLabel || 'Request access'}
          </>
        )}
      </Button>

      <p className="mt-3 text-center text-xs text-gray-600">
        We use your details only to respond to this request.
      </p>
    </form>
  );
}

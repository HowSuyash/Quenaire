import { notFound } from 'next/navigation';
import type { Metadata } from 'next';
import {
  ShieldCheck, FileText, Lock, ExternalLink, Activity, Mail, CalendarClock, AlertTriangle,
} from 'lucide-react';
import { LeadCaptureForm } from '@/components/trust/LeadCaptureForm';
import { fetchTrustProfile } from '@/lib/trust-api';
import { TrustItem } from '@/types/trust';

const DEFAULT_ACCENT = '#2563eb';

interface PageProps {
  params: { slug: string };
}

export async function generateMetadata({ params }: PageProps): Promise<Metadata> {
  const profile = await fetchTrustProfile(params.slug);
  if (!profile) {
    return { title: 'Trust Center' };
  }

  const description =
    profile.headline || `Security, compliance and privacy posture for ${profile.companyName}.`;

  return {
    title: `${profile.companyName} — Trust Center`,
    description,
    openGraph: {
      title: `${profile.companyName} — Trust Center`,
      description,
      type: 'website',
    },
  };
}

function formatDate(value?: string): string | null {
  if (!value) return null;
  return new Date(value).toLocaleDateString('en-US', {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
  });
}

function CertificationCard({ item, accent }: { item: TrustItem; accent: string }) {
  const validUntil = formatDate(item.validUntil);

  return (
    <div className="rounded-xl border border-gray-200 bg-white p-5 shadow-sm">
      <div className="flex items-start justify-between gap-3">
        <div className="flex items-center gap-2.5">
          <span
            className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg"
            style={{ backgroundColor: `${accent}15`, color: accent }}
          >
            <ShieldCheck className="h-5 w-5" />
          </span>
          <div>
            <p className="font-semibold text-gray-900">{item.title}</p>
            {item.issuer && <p className="text-xs text-gray-500">Audited by {item.issuer}</p>}
          </div>
        </div>

        {item.expired ? (
          <span className="inline-flex shrink-0 items-center gap-1 rounded-full bg-amber-100 px-2.5 py-0.5 text-xs font-medium text-amber-800">
            <AlertTriangle className="h-3 w-3" />
            Renewing
          </span>
        ) : (
          <span className="shrink-0 rounded-full bg-green-100 px-2.5 py-0.5 text-xs font-medium text-green-700">
            Current
          </span>
        )}
      </div>

      {item.body && <p className="mt-3 text-sm text-gray-600">{item.body}</p>}

      {validUntil && (
        <p className="mt-3 flex items-center gap-1.5 text-xs text-gray-500">
          <CalendarClock className="h-3.5 w-3.5" />
          Valid through {validUntil}
        </p>
      )}
    </div>
  );
}

function DocumentRow({ item, accent }: { item: TrustItem; accent: string }) {
  const requestOnly = item.accessLevel === 'REQUEST_ONLY' || !item.externalUrl;

  return (
    <li className="flex items-center justify-between gap-4 border-b border-gray-100 py-3 last:border-0">
      <div className="flex min-w-0 items-center gap-3">
        <FileText className="h-4 w-4 shrink-0 text-gray-400" />
        <div className="min-w-0">
          <p className="truncate font-medium text-gray-900">{item.title}</p>
          {item.body && <p className="truncate text-xs text-gray-500">{item.body}</p>}
        </div>
      </div>

      {requestOnly ? (
        <a
          href="#request-access"
          className="inline-flex shrink-0 items-center gap-1 rounded-lg border border-gray-300 px-3 py-1.5 text-xs font-medium text-gray-700 hover:bg-gray-50"
        >
          <Lock className="h-3 w-3" />
          Request
        </a>
      ) : (
        <a
          href={item.externalUrl ?? '#'}
          target="_blank"
          rel="noreferrer"
          className="inline-flex shrink-0 items-center gap-1 rounded-lg px-3 py-1.5 text-xs font-medium text-white"
          style={{ backgroundColor: accent }}
        >
          <ExternalLink className="h-3 w-3" />
          View
        </a>
      )}
    </li>
  );
}

export default async function TrustCenterPage({ params }: PageProps) {
  const profile = await fetchTrustProfile(params.slug);

  // An unpublished profile is indistinguishable from a missing one, by design.
  if (!profile) notFound();

  const accent = profile.primaryColor || DEFAULT_ACCENT;
  const requestableItems = profile.documents.filter(
    (item) => item.accessLevel === 'REQUEST_ONLY' || !item.externalUrl
  );

  return (
    <main className="min-h-screen bg-gray-50">
      {/* Hero */}
      <header className="border-b border-gray-200 bg-white">
        <div className="mx-auto max-w-5xl px-4 py-12 sm:py-16">
          <div className="flex flex-col gap-6 sm:flex-row sm:items-center">
            {profile.logoUrl ? (
              // eslint-disable-next-line @next/next/no-img-element
              <img
                src={profile.logoUrl}
                alt={`${profile.companyName} logo`}
                className="h-14 w-14 rounded-xl object-contain"
              />
            ) : (
              <span
                className="flex h-14 w-14 items-center justify-center rounded-xl text-white"
                style={{ backgroundColor: accent }}
              >
                <ShieldCheck className="h-7 w-7" />
              </span>
            )}

            <div className="min-w-0">
              <p className="text-sm font-medium uppercase tracking-wide" style={{ color: accent }}>
                Trust Center
              </p>
              <h1 className="mt-1 text-3xl font-bold text-gray-900 sm:text-4xl">
                {profile.companyName}
              </h1>
              {profile.headline && (
                <p className="mt-2 max-w-2xl text-lg text-gray-600">{profile.headline}</p>
              )}
            </div>
          </div>

          {profile.summary && (
            <p className="mt-6 max-w-3xl whitespace-pre-wrap text-gray-600">{profile.summary}</p>
          )}

          <div className="mt-6 flex flex-wrap gap-3">
            <a
              href="#request-access"
              className="inline-flex items-center gap-2 rounded-lg px-4 py-2 text-sm font-medium text-white"
              style={{ backgroundColor: accent }}
            >
              {profile.cta.buttonLabel || 'Request access'}
            </a>
            {profile.statusPageUrl && (
              <a
                href={profile.statusPageUrl}
                target="_blank"
                rel="noreferrer"
                className="inline-flex items-center gap-2 rounded-lg border border-gray-300 px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50"
              >
                <Activity className="h-4 w-4" />
                System status
              </a>
            )}
            {profile.contactEmail && (
              <a
                href={`mailto:${profile.contactEmail}`}
                className="inline-flex items-center gap-2 rounded-lg border border-gray-300 px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50"
              >
                <Mail className="h-4 w-4" />
                Contact security
              </a>
            )}
          </div>
        </div>
      </header>

      <div className="mx-auto max-w-5xl space-y-12 px-4 py-12">
        {/* Certifications */}
        {profile.certifications.length > 0 && (
          <section>
            <h2 className="mb-4 text-xl font-semibold text-gray-900">Certifications &amp; audits</h2>
            <div className="grid gap-4 sm:grid-cols-2">
              {profile.certifications.map((item) => (
                <CertificationCard key={item.id} item={item} accent={accent} />
              ))}
            </div>
          </section>
        )}

        {/* Controls */}
        {profile.controls.length > 0 && (
          <section>
            <h2 className="mb-4 text-xl font-semibold text-gray-900">How we protect your data</h2>
            <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
              {profile.controls.map((item) => (
                <div key={item.id} className="rounded-xl border border-gray-200 bg-white p-5">
                  <Lock className="mb-2.5 h-5 w-5" style={{ color: accent }} />
                  <p className="font-medium text-gray-900">{item.title}</p>
                  {item.body && <p className="mt-1 text-sm text-gray-600">{item.body}</p>}
                </div>
              ))}
            </div>
          </section>
        )}

        {/* Documents */}
        {profile.documents.length > 0 && (
          <section>
            <h2 className="mb-1 text-xl font-semibold text-gray-900">Documentation</h2>
            <p className="mb-4 text-sm text-gray-500">
              Items marked <span className="font-medium">Request</span> are shared under NDA.
            </p>
            <ul className="rounded-xl border border-gray-200 bg-white px-5">
              {profile.documents.map((item) => (
                <DocumentRow key={item.id} item={item} accent={accent} />
              ))}
            </ul>
          </section>
        )}

        {/* FAQ */}
        {profile.faqs.length > 0 && (
          <section>
            <h2 className="mb-4 text-xl font-semibold text-gray-900">Common questions</h2>
            <div className="space-y-3">
              {profile.faqs.map((item) => (
                <details
                  key={item.id}
                  className="group rounded-xl border border-gray-200 bg-white p-5 [&_summary::-webkit-details-marker]:hidden"
                >
                  <summary className="flex cursor-pointer items-center justify-between gap-4 font-medium text-gray-900">
                    {item.title}
                    <span className="text-gray-400 transition-transform group-open:rotate-45">+</span>
                  </summary>
                  {item.body && (
                    <p className="mt-3 whitespace-pre-wrap text-sm text-gray-600">{item.body}</p>
                  )}
                </details>
              ))}
            </div>
          </section>
        )}

        {/* Lead capture */}
        <section>
          <LeadCaptureForm
            slug={profile.slug}
            cta={profile.cta}
            requestableItems={requestableItems}
            accentColor={accent}
          />
        </section>
      </div>

      <footer className="border-t border-gray-200 bg-white">
        <div className="mx-auto flex max-w-5xl flex-col items-center gap-1 px-4 py-8 text-center text-sm text-gray-500">
          {profile.securityPageUrl && (
            <a
              href={profile.securityPageUrl}
              target="_blank"
              rel="noreferrer"
              className="hover:underline"
              style={{ color: accent }}
            >
              Full security documentation
            </a>
          )}
          <p>
            Trust Center powered by{' '}
            <a href="https://quenaire.io" target="_blank" rel="noreferrer" className="hover:underline">
              Quenaire
            </a>
          </p>
        </div>
      </footer>
    </main>
  );
}

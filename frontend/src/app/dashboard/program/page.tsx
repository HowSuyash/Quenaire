'use client';

import { useCallback, useEffect, useState } from 'react';
import {
  AlertTriangle, CheckCircle, Circle, Cloud, FileText, Github, Loader2,
  RefreshCw, ShieldCheck, Ticket, XCircle,
} from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { Badge } from '@/components/ui/Badge';
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/Card';
import { ErrorState } from '@/components/ui/ErrorState';
import { useToast } from '@/hooks/useToast';
import { api } from '@/lib/api';
import { ControlHealth, CoverageSummary, ProgramHealth, ProgramSignal, SignalSource } from '@/types/program';
import { cn, formatRelativeTime } from '@/lib/utils';

const SOURCE_ICONS: Record<SignalSource, typeof Cloud> = {
  AWS: Cloud,
  GITHUB: Github,
  JIRA: Ticket,
  CONFLUENCE: FileText,
  MANUAL: ShieldCheck,
};

/**
 * Status colors ship with an icon and a label, never color alone — red and green
 * are indistinguishable to a deuteranopic reader.
 */
const HEALTH_STYLES: Record<ControlHealth, { icon: typeof CheckCircle; tone: string; label: string }> = {
  PASSING: { icon: CheckCircle, tone: 'text-green-700 bg-green-50 border-green-200', label: 'Passing' },
  WARNING: { icon: AlertTriangle, tone: 'text-amber-800 bg-amber-50 border-amber-200', label: 'Warning' },
  FAILING: { icon: XCircle, tone: 'text-red-700 bg-red-50 border-red-200', label: 'Failing' },
  INFORMATIONAL: { icon: Circle, tone: 'text-blue-700 bg-blue-50 border-blue-200', label: 'Info' },
  UNKNOWN: { icon: Circle, tone: 'text-gray-500 bg-gray-50 border-gray-200', label: 'No evidence' },
};

function frameworkLabel(framework: string): string {
  return framework.replace(/_/g, ' ');
}

export default function ProgramPage() {
  const { toast } = useToast();

  const [health, setHealth] = useState<ProgramHealth | null>(null);
  const [coverage, setCoverage] = useState<CoverageSummary[]>([]);
  const [signals, setSignals] = useState<ProgramSignal[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [activeFramework, setActiveFramework] = useState<string | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoadError(null);
    try {
      const [healthResult, coverageResult, signalsResult] = await Promise.all([
        api.getProgramHealth(),
        api.getProgramCoverage(),
        api.getProgramSignals(),
      ]);

      setHealth(healthResult);
      setCoverage(coverageResult);
      setSignals(signalsResult);
      setActiveFramework((current) => current ?? coverageResult[0]?.framework ?? null);
    } catch (error) {
      // The client throws rather than inventing evidence. Showing nothing is
      // correct here: fabricated control coverage would be worse than useless.
      console.error('Could not load program evidence:', error);
      setLoadError(error instanceof Error ? error.message : 'Unknown error');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { load(); }, [load]);

  const refresh = async () => {
    setRefreshing(true);
    const result = await api.refreshProgram();
    setRefreshing(false);

    if (result?.success === false) {
      toast({ title: 'Refresh failed', description: result.error, variant: 'destructive' });
      return;
    }

    toast({
      title: `Collected ${result?.collected ?? 0} signals`,
      description: result?.skipped?.length
        ? `Skipped: ${result.skipped.join(', ')}`
        : `${result?.created ?? 0} new, ${result?.updated ?? 0} updated`,
      variant: 'success',
    });
    load();
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center py-24 text-gray-400">
        <Loader2 className="h-8 w-8 animate-spin" />
      </div>
    );
  }

  if (loadError) {
    return (
      <div className="space-y-6">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Security program</h1>
        </div>
        <ErrorState action="load your security program evidence" detail={loadError} onRetry={load} />
      </div>
    );
  }

  const selected = coverage.find((c) => c.framework === activeFramework) ?? coverage[0];
  const connectors = Object.entries(health?.connectors ?? {});
  const anyConnected = connectors.some(([, on]) => on);

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Security program</h1>
          <p className="text-sm text-gray-500">
            What your connected systems actually report, mapped onto the frameworks you get
            audited against.
          </p>
        </div>
        <Button onClick={refresh} disabled={refreshing}>
          <RefreshCw className={cn('mr-1.5 h-4 w-4', refreshing && 'animate-spin')} />
          {refreshing ? 'Collecting…' : 'Refresh evidence'}
        </Button>
      </div>

      {!anyConnected && (
        <div className="flex items-start gap-3 rounded-xl border border-amber-200 bg-amber-50 p-4">
          <AlertTriangle className="mt-0.5 h-5 w-5 shrink-0 text-amber-600" />
          <div className="text-sm">
            <p className="font-medium text-amber-900">No connectors configured</p>
            <p className="mt-0.5 text-amber-800">
              Set <code className="rounded bg-amber-100 px-1">aws.program-awareness.enabled</code>,{' '}
              <code className="rounded bg-amber-100 px-1">github.*</code>,{' '}
              <code className="rounded bg-amber-100 px-1">jira.*</code> or{' '}
              <code className="rounded bg-amber-100 px-1">confluence.*</code> to start collecting
              real evidence. Until then there is nothing to report.
            </p>
          </div>
        </div>
      )}

      {/* Evidence health */}
      <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-5">
        {[
          { label: 'Passing', value: health?.passing ?? 0, icon: CheckCircle, tone: 'text-green-600' },
          { label: 'Warning', value: health?.warning ?? 0, icon: AlertTriangle, tone: 'text-amber-600' },
          { label: 'Failing', value: health?.failing ?? 0, icon: XCircle, tone: 'text-red-600' },
          { label: 'Informational', value: health?.informational ?? 0, icon: Circle, tone: 'text-blue-600' },
          { label: 'Stale (30d+)', value: health?.staleCount ?? 0, icon: AlertTriangle, tone: 'text-gray-500' },
        ].map(({ label, value, icon: Icon, tone }) => (
          <Card key={label}>
            <CardContent className="flex items-center gap-3 p-4">
              <Icon className={cn('h-5 w-5 shrink-0', tone)} />
              <div className="min-w-0">
                <p className="text-xl font-semibold text-gray-900">{value}</p>
                <p className="truncate text-xs text-gray-500">{label}</p>
              </div>
            </CardContent>
          </Card>
        ))}
      </div>

      {/* Framework coverage */}
      {selected && (
        <Card>
          <CardHeader>
            <div className="flex flex-wrap items-center justify-between gap-3">
              <div>
                <CardTitle>Control coverage</CardTitle>
                <CardDescription>
                  {selected.coveredControls} of {selected.totalControls} controls have live evidence
                </CardDescription>
              </div>
              <div className="flex flex-wrap gap-1.5">
                {coverage.map((summary) => (
                  <button
                    key={summary.framework}
                    type="button"
                    onClick={() => setActiveFramework(summary.framework)}
                    className={cn(
                      'rounded-lg px-3 py-1.5 text-xs font-medium transition-colors',
                      summary.framework === selected.framework
                        ? 'bg-primary-600 text-white'
                        : 'border border-gray-300 text-gray-700 hover:bg-gray-50'
                    )}
                  >
                    {frameworkLabel(summary.framework)}
                  </button>
                ))}
              </div>
            </div>
          </CardHeader>

          <CardContent>
            {/* Coverage bar with its number stated in text, not read off the bar */}
            <div className="mb-5">
              <div className="mb-1 flex items-center justify-between text-sm">
                <span className="text-gray-600">Coverage</span>
                <span className="font-semibold text-gray-900">{selected.coveragePercent}%</span>
              </div>
              <div className="h-2 overflow-hidden rounded-full bg-gray-100">
                <div
                  className="h-full rounded-full bg-primary-600 transition-all"
                  style={{ width: `${selected.coveragePercent}%` }}
                />
              </div>
              <p className="mt-1.5 text-xs text-gray-500">
                {selected.passingControls} passing · {selected.failingControls} failing ·{' '}
                {selected.totalControls - selected.coveredControls} with no evidence
              </p>
            </div>

            <ul className="divide-y divide-gray-100">
              {selected.controls.map((control) => {
                const style = HEALTH_STYLES[control.health];
                const Icon = style.icon;

                return (
                  <li key={control.controlId} className="flex items-center gap-3 py-3">
                    <span
                      className={cn(
                        'inline-flex shrink-0 items-center gap-1.5 rounded-full border px-2 py-0.5 text-xs font-medium',
                        style.tone
                      )}
                    >
                      <Icon className="h-3 w-3" />
                      {style.label}
                    </span>

                    <div className="min-w-0 flex-1">
                      <p className="text-sm font-medium text-gray-900">
                        <span className="font-mono text-xs text-gray-500">{control.controlId}</span>
                        {'  '}
                        {control.controlTitle}
                      </p>
                      <p className="text-xs text-gray-500">{control.category}</p>
                    </div>

                    <span className="shrink-0 text-xs text-gray-500">
                      {control.evidenceCount} evidence
                    </span>
                  </li>
                );
              })}
            </ul>
          </CardContent>
        </Card>
      )}

      {/* Raw signals */}
      <Card>
        <CardHeader>
          <CardTitle>Observed evidence</CardTitle>
          <CardDescription>
            Facts pulled from connected systems, newest first. Each is dated so an answer built
            on it can be qualified.
          </CardDescription>
        </CardHeader>
        <CardContent>
          {signals.length === 0 ? (
            <p className="py-8 text-center text-sm text-gray-500">
              No evidence collected yet. Configure a connector and run a refresh.
            </p>
          ) : (
            <ul className="divide-y divide-gray-100">
              {signals.map((signal) => {
                const SourceIcon = SOURCE_ICONS[signal.source] ?? ShieldCheck;
                const style = HEALTH_STYLES[signal.status];
                const StatusIcon = style.icon;

                return (
                  <li key={signal.id} className="flex items-start gap-3 py-3">
                    <SourceIcon className="mt-0.5 h-4 w-4 shrink-0 text-gray-400" />

                    <div className="min-w-0 flex-1">
                      <div className="flex flex-wrap items-center gap-2">
                        <p className="text-sm font-medium text-gray-900">{signal.title}</p>
                        <span
                          className={cn(
                            'inline-flex items-center gap-1 rounded-full border px-2 py-0.5 text-[11px] font-medium',
                            style.tone
                          )}
                        >
                          <StatusIcon className="h-3 w-3" />
                          {style.label}
                        </span>
                        {signal.category && (
                          <Badge variant="secondary">{signal.category}</Badge>
                        )}
                      </div>

                      {signal.detail && (
                        <p className="mt-0.5 text-sm text-gray-600">{signal.detail}</p>
                      )}

                      <p className="mt-1 text-xs text-gray-400">
                        {signal.source} · observed {formatRelativeTime(signal.observedAt)}
                        {signal.sourceUrl && (
                          <>
                            {' · '}
                            <a
                              href={signal.sourceUrl}
                              target="_blank"
                              rel="noreferrer"
                              className="text-primary-600 hover:underline"
                            >
                              view source
                            </a>
                          </>
                        )}
                      </p>
                    </div>
                  </li>
                );
              })}
            </ul>
          )}
        </CardContent>
      </Card>
    </div>
  );
}

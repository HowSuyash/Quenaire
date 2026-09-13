'use client';

import { useEffect, useMemo, useState } from 'react';
import {
  ResponsiveContainer, LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, Legend,
  BarChart, Bar, Cell, LabelList,
} from 'recharts';
import { Clock, DollarSign, Loader2, Table2, TrendingUp, Zap } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { Select } from '@/components/ui/Select';
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/Card';
import { api } from '@/lib/api';
import { AnalyticsDashboard } from '@/types/analytics';
import { cn } from '@/lib/utils';

/**
 * Palette roles, taken from the validated reference palette and checked against
 * this page's white chart surface:
 *   - two-series trend  -> categorical slots 1 (blue) + 2 (orange)
 *   - confidence bands  -> ordinal one-hue blue ramp (ordered magnitude, not status)
 *   - single-series bar -> categorical slot 1
 */
const SERIES_ANSWERED = '#2a78d6';
const SERIES_APPROVED = '#eb6834';
const INK_MUTED = '#898781';
const GRIDLINE = '#e1e0d9';

/** Ordinal ramp: light end stays above 2:1 on white. */
const CONFIDENCE_RAMP: Record<string, string> = {
  'High (90+)': '#1c5cab',
  'Medium (70-89)': '#3987e5',
  'Low (<70)': '#86b6ef',
  'Not scored': '#c3c2b7',
};

const RANGE_OPTIONS = [
  { value: '7', label: 'Last 7 days' },
  { value: '30', label: 'Last 30 days' },
  { value: '90', label: 'Last 90 days' },
  { value: '365', label: 'Last 12 months' },
];

function formatCurrency(value: number, currency: string): string {
  try {
    return new Intl.NumberFormat('en-US', {
      style: 'currency', currency, maximumFractionDigits: 0,
    }).format(value);
  } catch {
    return `${currency} ${Math.round(value).toLocaleString()}`;
  }
}

function formatHours(value: number | null): string {
  if (value == null) return '—';
  if (value < 1) return `${Math.round(value * 60)} min`;
  if (value < 48) return `${value.toFixed(1)} h`;
  return `${(value / 24).toFixed(1)} days`;
}

/** Shared tooltip so every chart on the page reads the same. */
function ChartTooltip({ active, payload, label }: {
  active?: boolean;
  payload?: Array<{ name?: string; value?: number | string; color?: string; payload?: Record<string, unknown> }>;
  label?: string | number;
}) {
  if (!active || !payload?.length) return null;

  return (
    <div className="rounded-lg border border-gray-200 bg-white px-3 py-2 shadow-lg">
      {label != null && (
        <p className="mb-1 text-xs font-medium text-gray-900">{String(label)}</p>
      )}
      {payload.map((entry, index) => (
        <p key={index} className="flex items-center gap-2 text-xs text-gray-600">
          <span
            className="inline-block h-2 w-2 shrink-0 rounded-full"
            style={{ backgroundColor: entry.color }}
          />
          <span>{entry.name}</span>
          <span className="ml-auto font-medium text-gray-900">{entry.value}</span>
        </p>
      ))}
    </div>
  );
}

export default function AnalyticsPage() {
  const [data, setData] = useState<AnalyticsDashboard | null>(null);
  const [loading, setLoading] = useState(true);
  const [days, setDays] = useState('30');
  const [showTable, setShowTable] = useState(false);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);

    api.getAnalyticsDashboard(Number(days)).then((result) => {
      if (!cancelled) {
        setData(result);
        setLoading(false);
      }
    });

    return () => { cancelled = true; };
  }, [days]);

  const trend = useMemo(() => (data?.trend ?? []).map((point) => ({
    ...point,
    // Recharts renders the raw key; format once here rather than per axis tick.
    label: new Date(point.date).toLocaleDateString('en-US', { month: 'short', day: 'numeric' }),
  })), [data]);

  if (loading || !data) {
    return (
      <div className="flex items-center justify-center py-24 text-gray-600">
        <Loader2 className="h-8 w-8 animate-spin" />
      </div>
    );
  }

  const { totals, roi, turnaround, categories, confidence, smeWorkload } = data;
  const currency = roi.assumptions.currency;

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Analytics &amp; ROI</h1>
          <p className="text-sm text-gray-500">
            What the questionnaire pipeline produced, and what it saved.
          </p>
        </div>
        <div className="flex items-center gap-2">
          <div className="w-44">
            <Select
              value={days}
              onChange={(e) => setDays(e.target.value)}
              options={RANGE_OPTIONS}
              aria-label="Time range"
            />
          </div>
          <Button variant="outline" size="sm" onClick={() => setShowTable((v) => !v)}>
            <Table2 className="mr-1.5 h-4 w-4" />
            {showTable ? 'Hide data' : 'View data'}
          </Button>
        </div>
      </div>

      {/* ROI headline — a hero number, not a chart */}
      <Card>
        <CardContent className="grid gap-6 p-6 sm:grid-cols-2 lg:grid-cols-4">
          <div>
            <p className="flex items-center gap-1.5 text-xs font-medium uppercase tracking-wide text-gray-500">
              <DollarSign className="h-3.5 w-3.5" />
              Cost avoided
            </p>
            <p className="mt-1 text-4xl font-bold tracking-tight text-gray-900">
              {formatCurrency(roi.costAvoided, currency)}
            </p>
            <p className="mt-1 text-sm text-gray-500">
              {roi.savingsPercent.toFixed(0)}% less effort than answering by hand
            </p>
          </div>

          <div>
            <p className="flex items-center gap-1.5 text-xs font-medium uppercase tracking-wide text-gray-500">
              <Clock className="h-3.5 w-3.5" />
              Hours saved
            </p>
            <p className="mt-1 text-4xl font-bold tracking-tight text-gray-900">
              {roi.hoursSaved.toFixed(0)}
            </p>
            <p className="mt-1 text-sm text-gray-500">
              {roi.manualHours.toFixed(0)} h manual vs {roi.actualHours.toFixed(0)} h actual
            </p>
          </div>

          <div>
            <p className="flex items-center gap-1.5 text-xs font-medium uppercase tracking-wide text-gray-500">
              <Zap className="h-3.5 w-3.5" />
              Automation rate
            </p>
            <p className="mt-1 text-4xl font-bold tracking-tight text-gray-900">
              {totals.automationRate.toFixed(0)}%
            </p>
            <p className="mt-1 text-sm text-gray-500">
              {totals.questionsAutoApproved} answers approved unedited
            </p>
          </div>

          <div>
            <p className="flex items-center gap-1.5 text-xs font-medium uppercase tracking-wide text-gray-500">
              <TrendingUp className="h-3.5 w-3.5" />
              Median turnaround
            </p>
            <p className="mt-1 text-4xl font-bold tracking-tight text-gray-900">
              {formatHours(turnaround.medianHours)}
            </p>
            <p className="mt-1 text-sm text-gray-500">
              across {turnaround.sampleSize} completed questionnaire
              {turnaround.sampleSize === 1 ? '' : 's'}
            </p>
          </div>
        </CardContent>

        <div className="border-t border-gray-100 px-6 py-3">
          <p className="text-xs text-gray-500">
            <span className="font-medium">Modelled, not measured.</span> Assumes{' '}
            {roi.assumptions.manualMinutesPerQuestion} min to answer a question by hand,{' '}
            {roi.assumptions.reviewMinutesPerQuestion} min to review an AI draft, at{' '}
            {formatCurrency(roi.assumptions.hourlyRate, currency)}/hour. Edited answers are
            charged review time plus half the manual time. Change these under{' '}
            <code className="rounded bg-gray-100 px-1 py-0.5 text-gray-700">analytics.*</code> in application.yml.
          </p>
        </div>
      </Card>

      {/* Throughput trend */}
      <Card>
        <CardHeader>
          <CardTitle as="h2">Throughput</CardTitle>
          <CardDescription>Questions answered and approved per day</CardDescription>
        </CardHeader>
        <CardContent>
          <div className="h-72 w-full">
            <ResponsiveContainer width="100%" height="100%">
              <LineChart data={trend} margin={{ top: 8, right: 16, bottom: 0, left: -12 }}>
                <CartesianGrid stroke={GRIDLINE} strokeDasharray="0" vertical={false} />
                <XAxis
                  dataKey="label"
                  tick={{ fill: INK_MUTED, fontSize: 11 }}
                  tickLine={false}
                  axisLine={{ stroke: GRIDLINE }}
                  minTickGap={24}
                />
                <YAxis
                  tick={{ fill: INK_MUTED, fontSize: 11 }}
                  tickLine={false}
                  axisLine={false}
                  allowDecimals={false}
                />
                <Tooltip content={<ChartTooltip />} cursor={{ stroke: GRIDLINE, strokeWidth: 1 }} />
                <Legend
                  verticalAlign="top"
                  align="left"
                  height={28}
                  iconType="plainline"
                  wrapperStyle={{ fontSize: 12, color: '#52514e' }}
                />
                <Line
                  type="monotone"
                  dataKey="questionsAnswered"
                  name="Answered"
                  stroke={SERIES_ANSWERED}
                  strokeWidth={2}
                  dot={false}
                  activeDot={{ r: 4, strokeWidth: 2, stroke: '#ffffff' }}
                />
                <Line
                  type="monotone"
                  dataKey="questionsApproved"
                  name="Approved"
                  stroke={SERIES_APPROVED}
                  strokeWidth={2}
                  dot={false}
                  activeDot={{ r: 4, strokeWidth: 2, stroke: '#ffffff' }}
                />
              </LineChart>
            </ResponsiveContainer>
          </div>
        </CardContent>
      </Card>

      <div className="grid gap-6 lg:grid-cols-2">
        {/* Confidence distribution — ordered magnitude, one-hue ramp */}
        <Card>
          <CardHeader>
            <CardTitle as="h2">Confidence distribution</CardTitle>
            <CardDescription>How sure the model was, across all questions</CardDescription>
          </CardHeader>
          <CardContent>
            <div className="h-64 w-full">
              <ResponsiveContainer width="100%" height="100%">
                <BarChart
                  data={confidence}
                  layout="vertical"
                  margin={{ top: 4, right: 40, bottom: 0, left: 8 }}
                >
                  <CartesianGrid stroke={GRIDLINE} horizontal={false} />
                  <XAxis
                    type="number"
                    tick={{ fill: INK_MUTED, fontSize: 11 }}
                    tickLine={false}
                    axisLine={{ stroke: GRIDLINE }}
                    allowDecimals={false}
                  />
                  <YAxis
                    type="category"
                    dataKey="band"
                    width={110}
                    tick={{ fill: '#52514e', fontSize: 12 }}
                    tickLine={false}
                    axisLine={false}
                  />
                  <Tooltip content={<ChartTooltip />} cursor={{ fill: 'rgba(0,0,0,0.03)' }} />
                  <Bar dataKey="count" name="Questions" radius={[0, 4, 4, 0]} barSize={22}>
                    {confidence.map((entry) => (
                      <Cell
                        key={entry.band}
                        fill={CONFIDENCE_RAMP[entry.band] ?? SERIES_ANSWERED}
                      />
                    ))}
                    {/* Direct labels: identity never rests on color alone. */}
                    <LabelList
                      dataKey="count"
                      position="right"
                      style={{ fill: '#52514e', fontSize: 11 }}
                    />
                  </Bar>
                </BarChart>
              </ResponsiveContainer>
            </div>
          </CardContent>
        </Card>

        {/* Category volume — single series, so no legend */}
        <Card>
          <CardHeader>
            <CardTitle as="h2">Questions by category</CardTitle>
            <CardDescription>Where the questionnaire workload concentrates</CardDescription>
          </CardHeader>
          <CardContent>
            <div className="h-64 w-full">
              <ResponsiveContainer width="100%" height="100%">
                <BarChart
                  data={categories.slice(0, 7)}
                  layout="vertical"
                  margin={{ top: 4, right: 40, bottom: 0, left: 8 }}
                >
                  <CartesianGrid stroke={GRIDLINE} horizontal={false} />
                  <XAxis
                    type="number"
                    tick={{ fill: INK_MUTED, fontSize: 11 }}
                    tickLine={false}
                    axisLine={{ stroke: GRIDLINE }}
                    allowDecimals={false}
                  />
                  <YAxis
                    type="category"
                    dataKey="category"
                    width={150}
                    tick={{ fill: '#52514e', fontSize: 12 }}
                    tickLine={false}
                    axisLine={false}
                  />
                  <Tooltip content={<ChartTooltip />} cursor={{ fill: 'rgba(0,0,0,0.03)' }} />
                  <Bar
                    dataKey="total"
                    name="Questions"
                    fill={SERIES_ANSWERED}
                    radius={[0, 4, 4, 0]}
                    barSize={18}
                  >
                    <LabelList
                      dataKey="total"
                      position="right"
                      style={{ fill: '#52514e', fontSize: 11 }}
                    />
                  </Bar>
                </BarChart>
              </ResponsiveContainer>
            </div>
          </CardContent>
        </Card>
      </div>

      {/* SME workload — identity plus several measures reads better as a table */}
      <Card>
        <CardHeader>
          <CardTitle as="h2">Reviewer workload</CardTitle>
          <CardDescription>Assignment load and SLA adherence per SME</CardDescription>
        </CardHeader>
        <CardContent>
          {smeWorkload.length === 0 ? (
            <p className="py-6 text-center text-sm text-gray-500">
              No SME assignments yet. Route low-confidence answers from the review queue to
              populate this.
            </p>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b border-gray-200 text-left text-xs uppercase tracking-wide text-gray-500">
                    <th className="pb-2 pr-4 font-medium">Reviewer</th>
                    <th className="pb-2 pr-4 text-right font-medium">Assigned</th>
                    <th className="pb-2 pr-4 text-right font-medium">Completed</th>
                    <th className="pb-2 pr-4 text-right font-medium">Open</th>
                    <th className="pb-2 pr-4 text-right font-medium">Overdue</th>
                    <th className="pb-2 text-right font-medium">Avg. time</th>
                  </tr>
                </thead>
                <tbody>
                  {smeWorkload.map((sme) => (
                    <tr key={sme.smeUserId} className="border-b border-gray-100 last:border-0">
                      <td className="py-2.5 pr-4 font-mono text-xs text-gray-600">{sme.smeUserId}</td>
                      <td className="py-2.5 pr-4 text-right text-gray-900">{sme.assigned}</td>
                      <td className="py-2.5 pr-4 text-right text-gray-900">{sme.completed}</td>
                      <td className="py-2.5 pr-4 text-right text-gray-900">{sme.open}</td>
                      <td className={cn(
                        'py-2.5 pr-4 text-right font-medium',
                        sme.overdue > 0 ? 'text-red-600' : 'text-gray-600'
                      )}>
                        {sme.overdue}
                      </td>
                      <td className="py-2.5 text-right text-gray-900">
                        {formatHours(sme.averageHoursToComplete)}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </CardContent>
      </Card>

      {/* Table view of the charted series */}
      {showTable && (
        <Card>
          <CardHeader>
            <CardTitle as="h2">Underlying data</CardTitle>
            <CardDescription>The same series the charts above draw from</CardDescription>
          </CardHeader>
          <CardContent>
            <div className="max-h-96 overflow-auto">
              <table className="w-full text-sm">
                <thead className="sticky top-0 bg-white">
                  <tr className="border-b border-gray-200 text-left text-xs uppercase tracking-wide text-gray-500">
                    <th className="pb-2 pr-4 font-medium">Date</th>
                    <th className="pb-2 pr-4 text-right font-medium">Answered</th>
                    <th className="pb-2 pr-4 text-right font-medium">Approved</th>
                    <th className="pb-2 text-right font-medium">Hours saved</th>
                  </tr>
                </thead>
                <tbody>
                  {trend.map((point) => (
                    <tr key={point.date} className="border-b border-gray-100 last:border-0">
                      <td className="py-2 pr-4 text-gray-600">{point.date}</td>
                      <td className="py-2 pr-4 text-right text-gray-900">{point.questionsAnswered}</td>
                      <td className="py-2 pr-4 text-right text-gray-900">{point.questionsApproved}</td>
                      <td className="py-2 text-right text-gray-900">{point.hoursSaved.toFixed(1)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </CardContent>
        </Card>
      )}
    </div>
  );
}

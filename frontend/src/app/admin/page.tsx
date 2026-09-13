'use client';

import { useCallback, useEffect, useState } from 'react';
import { Building2, Loader2, Search, ShieldAlert, Users, FileText } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Select } from '@/components/ui/Select';
import { Badge } from '@/components/ui/Badge';
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/Card';
import { useToast } from '@/hooks/useToast';
import { api } from '@/lib/api';
import { PlatformStats, TenantSummary } from '@/types/whitelabel';
import { cn, formatDate } from '@/lib/utils';

const STATUS_FILTERS = [
  { value: '', label: 'All statuses' },
  { value: 'ACTIVE', label: 'Active' },
  { value: 'TRIAL', label: 'Trial' },
  { value: 'SUSPENDED', label: 'Suspended' },
  { value: 'EXPIRED', label: 'Expired' },
];

const PLAN_OPTIONS = [
  { value: 'FREE', label: 'Free' },
  { value: 'STARTER', label: 'Starter' },
  { value: 'PROFESSIONAL', label: 'Professional' },
  { value: 'ENTERPRISE', label: 'Enterprise' },
  { value: 'CUSTOM', label: 'Custom' },
];

function statusTone(status: string): string {
  switch (status) {
    case 'ACTIVE': return 'bg-green-100 text-green-700';
    case 'TRIAL': return 'bg-blue-100 text-blue-700';
    case 'SUSPENDED': return 'bg-red-100 text-red-700';
    case 'EXPIRED': return 'bg-yellow-100 text-yellow-800';
    default: return 'bg-gray-100 text-gray-600';
  }
}

export default function PlatformAdminPage() {
  const { toast } = useToast();

  const [stats, setStats] = useState<PlatformStats | null>(null);
  const [tenants, setTenants] = useState<TenantSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [forbidden, setForbidden] = useState(false);
  const [search, setSearch] = useState('');
  const [status, setStatus] = useState('');

  const load = useCallback(async () => {
    setLoading(true);
    const [statsResult, tenantsResult] = await Promise.all([
      api.getPlatformStats(),
      api.getPlatformTenants({ search: search || undefined, status: status || undefined }),
    ]);

    // The API returns null / empty when the caller lacks PLATFORM_ADMIN; there is
    // no client-side role check, the server is the authority.
    setForbidden(statsResult === null);
    setStats(statsResult);
    setTenants(tenantsResult);
    setLoading(false);
  }, [search, status]);

  useEffect(() => {
    const timer = setTimeout(load, 250);
    return () => clearTimeout(timer);
  }, [load]);

  const changeStatus = async (tenant: TenantSummary, next: string) => {
    const result = await api.setTenantStatus(tenant.id, next);
    if (!result.success) {
      toast({ title: 'Could not update workspace', description: result.error, variant: 'destructive' });
      return;
    }
    toast({ title: `${tenant.name} set to ${next.toLowerCase()}`, variant: 'success' });
    load();
  };

  const changePlan = async (tenant: TenantSummary, plan: string) => {
    const result = await api.setTenantPlan(tenant.id, plan);
    if (!result.success) {
      toast({ title: 'Could not update plan', description: result.error, variant: 'destructive' });
      return;
    }
    toast({
      title: `${tenant.name} moved to ${plan.toLowerCase()}`,
      description: 'A later Stripe webhook will overwrite this for Stripe-managed accounts.',
      variant: 'success',
    });
    load();
  };

  if (loading && !stats && tenants.length === 0) {
    return (
      <div className="flex items-center justify-center py-24 text-gray-600">
        <Loader2 className="h-8 w-8 animate-spin" />
      </div>
    );
  }

  if (forbidden) {
    return (
      <div className="mx-auto max-w-md py-24 text-center">
        <ShieldAlert className="mx-auto mb-3 h-10 w-10 text-gray-600" />
        <h1 className="text-lg font-semibold text-gray-900">Platform access required</h1>
        <p className="mt-1 text-sm text-gray-500">
          This portal is limited to accounts holding the <code>PLATFORM_ADMIN</code> realm role.
          Owning a workspace does not grant access.
        </p>
      </div>
    );
  }

  const tiles = stats ? [
    { label: 'Workspaces', value: stats.totalTenants, icon: Building2 },
    { label: 'Active', value: stats.activeTenants, icon: Building2 },
    { label: 'Trial', value: stats.trialTenants, icon: Building2 },
    { label: 'Suspended', value: stats.suspendedTenants, icon: ShieldAlert },
    { label: 'Users', value: stats.totalUsers, icon: Users },
    { label: 'Questionnaires', value: stats.totalQuestionnaires, icon: FileText },
  ] : [];

  return (
    <div className="mx-auto max-w-7xl space-y-6 p-6">
      <div>
        <h1 className="text-2xl font-bold text-gray-900">Platform admin</h1>
        <p className="text-sm text-gray-500">
          Cross-tenant operations. Workspace content stays private — only metadata and
          aggregates are shown here.
        </p>
      </div>

      <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-6">
        {tiles.map(({ label, value, icon: Icon }) => (
          <Card key={label}>
            <CardContent className="flex items-center gap-3 p-4">
              <Icon className="h-5 w-5 shrink-0 text-gray-600" />
              <div className="min-w-0">
                <p className="text-xl font-semibold text-gray-900">{value}</p>
                <p className="truncate text-xs text-gray-500">{label}</p>
              </div>
            </CardContent>
          </Card>
        ))}
      </div>

      <Card>
        <CardHeader>
          <CardTitle as="h2">Workspaces</CardTitle>
          <CardDescription>{tenants.length} shown</CardDescription>
        </CardHeader>
        <CardContent>
          <div className="mb-4 flex flex-wrap items-end gap-3">
            <div className="min-w-[240px] flex-1">
              <Input
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                placeholder="Search by name, slug or domain…"
                leftIcon={<Search className="h-4 w-4" />}
                aria-label="Search workspaces"
              />
            </div>
            <div className="w-44">
              <Select
                value={status}
                onChange={(e) => setStatus(e.target.value)}
                options={STATUS_FILTERS}
                aria-label="Filter by status"
              />
            </div>
          </div>

          {tenants.length === 0 ? (
            <p className="py-10 text-center text-sm text-gray-500">No workspaces match.</p>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b border-gray-200 text-left text-xs uppercase tracking-wide text-gray-500">
                    <th className="pb-2 pr-4 font-medium">Workspace</th>
                    <th className="pb-2 pr-4 font-medium">Status</th>
                    <th className="pb-2 pr-4 font-medium">Plan</th>
                    <th className="pb-2 pr-4 font-medium">Subscription</th>
                    <th className="pb-2 pr-4 text-right font-medium">Users</th>
                    <th className="pb-2 pr-4 text-right font-medium">Questionnaires</th>
                    <th className="pb-2 pr-4 font-medium">Created</th>
                    <th className="pb-2 font-medium">Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {tenants.map((tenant) => (
                    <tr key={tenant.id} className="border-b border-gray-100 last:border-0">
                      <td className="py-3 pr-4">
                        <p className="font-medium text-gray-900">{tenant.name}</p>
                        <p className="text-xs text-gray-500">
                          {tenant.customDomain || `/${tenant.slug}`}
                        </p>
                      </td>
                      <td className="py-3 pr-4">
                        <Badge className={statusTone(tenant.status)}>{tenant.status}</Badge>
                      </td>
                      <td className="py-3 pr-4">
                        <select
                          value={tenant.plan}
                          onChange={(e) => changePlan(tenant, e.target.value)}
                          className="rounded border border-gray-300 bg-white px-2 py-1 text-xs focus:outline-none focus:ring-2 focus:ring-primary-500"
                          aria-label={`Plan for ${tenant.name}`}
                        >
                          {PLAN_OPTIONS.map((option) => (
                            <option key={option.value} value={option.value}>{option.label}</option>
                          ))}
                        </select>
                      </td>
                      <td className="py-3 pr-4">
                        <span className={cn(
                          'text-xs',
                          tenant.subscriptionStatus === 'ACTIVE' ? 'text-green-700'
                            : tenant.subscriptionStatus === 'PAST_DUE' ? 'text-red-600'
                            : 'text-gray-500'
                        )}>
                          {tenant.subscriptionStatus}
                        </span>
                      </td>
                      <td className="py-3 pr-4 text-right text-gray-900">{tenant.userCount}</td>
                      <td className="py-3 pr-4 text-right text-gray-900">{tenant.questionnaireCount}</td>
                      <td className="py-3 pr-4 text-xs text-gray-500">
                        {tenant.createdAt ? formatDate(tenant.createdAt) : '—'}
                      </td>
                      <td className="py-3">
                        {tenant.status === 'SUSPENDED' ? (
                          <Button size="sm" variant="outline" onClick={() => changeStatus(tenant, 'ACTIVE')}>
                            Reactivate
                          </Button>
                        ) : (
                          <Button
                            size="sm"
                            variant="outline"
                            className="text-red-600 hover:bg-red-50"
                            onClick={() => changeStatus(tenant, 'SUSPENDED')}
                          >
                            Suspend
                          </Button>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  );
}

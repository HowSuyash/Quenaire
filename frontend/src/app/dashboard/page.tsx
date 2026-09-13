'use client';

import { useQuery } from '@tanstack/react-query';
import { api } from '@/lib/api';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Badge } from '@/components/ui/Badge';
import { cn, formatDateTime, getStatusColor } from '@/lib/utils';
import { 
  ClipboardList, 
  FileText, 
  Users, 
  TrendingUp,
  Plus,
  ArrowUpRight,
  ArrowDownRight,
  ExternalLink
} from 'lucide-react';
import Link from 'next/link';

export default function DashboardPage() {
  const { data: questionnaires } = useQuery({
    queryKey: ['questionnaires', { size: 5 }],
    queryFn: () => api.getQuestionnaires({ size: 5 }),
  });

  const { data: documents } = useQuery({
    queryKey: ['documents', { size: 5 }],
    queryFn: () => api.getDocuments({ size: 5 }),
  });

  // The counts below are tenant-wide, so they come from the analytics endpoint
  // rather than being summed from the five rows fetched for the lists.
  const { data: analytics } = useQuery({
    queryKey: ['analytics', 30],
    queryFn: () => api.getAnalyticsDashboard(30),
  });

  // Every figure here is read back from the API. Two of these cards used to hold
  // literal constants -- Team Members 5, Completion Rate 87% -- and all four
  // carried a hardcoded "+12% vs last month" trend, shown on accounts minutes
  // old. Nothing compares against last month, so no trend is claimed.
  const totals = analytics?.totals;

  const stats = [
    {
      name: 'Questionnaires',
      value: totals?.questionnaires ?? questionnaires?.totalElements ?? 0,
      icon: ClipboardList,
      color: 'bg-primary-600',
      hint: totals ? `${totals.questionnairesCompleted} completed` : null,
    },
    {
      name: 'Documents indexed',
      value: documents?.totalElements ?? 0,
      icon: FileText,
      color: 'bg-emerald-600',
      hint: 'Answers are grounded in these',
    },
    {
      name: 'Questions answered',
      value: totals ? `${totals.questionsAnswered} of ${totals.questions}` : '—',
      icon: Users,
      color: 'bg-sky-600',
      hint: totals ? `${totals.questionsApproved} approved` : null,
    },
    {
      name: 'Answered without edits',
      // What the product is actually for: the share of answers a reviewer let
      // through unchanged. Undefined until something has been answered, and
      // shown as such rather than as a number.
      value:
        totals && totals.questionsAnswered > 0
          ? `${Math.round(totals.automationRate)}%`
          : '—',
      icon: TrendingUp,
      color: 'bg-indigo-600',
      hint: totals && totals.questionsHumanEdited > 0
        ? `${totals.questionsHumanEdited} edited by a reviewer`
        : null,
    },
  ];

  const recentQuestionnaires = questionnaires?.content?.slice(0, 5) || [];
  const recentDocuments = documents?.content?.slice(0, 5) || [];

  return (
    <div className="space-y-6">
      {/* Page Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold text-gray-900">Dashboard</h1>
          <p className="text-gray-500 mt-1">Overview of your security questionnaire automation</p>
        </div>
        <Link href="/dashboard/questionnaires">
          <Button>
            <Plus className="h-4 w-4 mr-2" />
            New Questionnaire
          </Button>
        </Link>
      </div>

      {/* Stats Grid */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-6">
        {stats.map((stat) => (
          <Card key={stat.name}>
            <CardContent className="p-6">
              <div className="flex items-center justify-between">
                <div>
                  <p className="text-sm font-medium text-gray-500">{stat.name}</p>
                  <p className="text-3xl font-bold text-gray-900 mt-1">{stat.value}</p>
                  {stat.hint && (
                    <p className="text-sm text-gray-500 mt-2">{stat.hint}</p>
                  )}
                </div>
                <div className={cn('p-3 rounded-xl', stat.color)}>
                  <stat.icon className="h-6 w-6 text-white" />
                </div>
              </div>
            </CardContent>
          </Card>
        ))}
      </div>

      {/* Recent Activity */}
      <div className="grid lg:grid-cols-2 gap-6">
        {/* Recent Questionnaires */}
        <Card>
          <CardHeader className="flex flex-row items-center justify-between">
            <CardTitle as="h2">Recent Questionnaires</CardTitle>
            <Link href="/dashboard/questionnaires" className="text-sm text-primary-600 hover:underline flex items-center gap-1">
              View all
              <ArrowUpRight className="h-3 w-3" />
            </Link>
          </CardHeader>
          <CardContent>
            {recentQuestionnaires.length === 0 ? (
              <div className="text-center py-8 text-gray-500">
                <ClipboardList className="h-12 w-12 mx-auto text-gray-300 mb-3" />
                <p>No questionnaires yet</p>
                <Link href="/dashboard/questionnaires" className="mt-2 inline-block">
                  <Button variant="outline" size="sm">Create Questionnaire</Button>
                </Link>
              </div>
            ) : (
              <div className="space-y-4">
                {recentQuestionnaires.map((q) => (
                  <Link key={q.id} href={`/dashboard/questionnaires/${q.id}`} className="block">
                    <div className="flex items-center justify-between p-3 hover:bg-gray-50 rounded-lg transition-colors">
                      <div className="flex-1 min-w-0">
                        <p className="font-medium text-gray-900 truncate">{q.title}</p>
                        <p className="text-sm text-gray-500">
                          {q.totalQuestions} questions • {formatDateTime(q.updatedAt)}
                        </p>
                      </div>
                      <div className="flex items-center gap-2">
                        <Badge className={getStatusColor(q.status)}>{q.status.replace('_', ' ')}</Badge>
                        <ExternalLink className="h-4 w-4 text-gray-600" />
                      </div>
                    </div>
                  </Link>
                ))}
              </div>
            )}
          </CardContent>
        </Card>

        {/* Recent Documents */}
        <Card>
          <CardHeader className="flex flex-row items-center justify-between">
            <CardTitle as="h2">Recent Documents</CardTitle>
            <Link href="/dashboard/documents" className="text-sm text-primary-600 hover:underline flex items-center gap-1">
              View all
              <ArrowUpRight className="h-3 w-3" />
            </Link>
          </CardHeader>
          <CardContent>
            {recentDocuments.length === 0 ? (
              <div className="text-center py-8 text-gray-500">
                <FileText className="h-12 w-12 mx-auto text-gray-300 mb-3" />
                <p>No documents yet</p>
                <Link href="/dashboard/documents" className="mt-2 inline-block">
                  <Button variant="outline" size="sm">Upload Document</Button>
                </Link>
              </div>
            ) : (
              <div className="space-y-4">
                {recentDocuments.map((d) => (
                  <Link key={d.id} href={`/dashboard/documents/${d.id}`} className="block">
                    <div className="flex items-center justify-between p-3 hover:bg-gray-50 rounded-lg transition-colors">
                      <div className="flex items-center gap-3">
                        <div className="w-10 h-10 rounded-lg bg-gray-100 flex items-center justify-center">
                          <FileText className="h-5 w-5 text-gray-600" />
                        </div>
                        <div>
                          <p className="font-medium text-gray-900 truncate max-w-[200px]">{d.title}</p>
                          <p className="text-sm text-gray-500">{d.fileName || 'No file'} • {formatDateTime(d.updatedAt)}</p>
                        </div>
                      </div>
                      <Badge className={getStatusColor(d.status)}>{d.status}</Badge>
                    </div>
                  </Link>
                ))}
              </div>
            )}
          </CardContent>
        </Card>
      </div>

      {/* Quick Actions */}
      <Card>
        <CardHeader>
          <CardTitle as="h2">Quick Actions</CardTitle>
        </CardHeader>
        <CardContent>
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
            <Link href="/dashboard/questionnaires" className="block">
              <Button variant="outline" className="w-full h-24 flex flex-col items-center justify-center gap-2">
                <ClipboardList className="h-8 w-8 text-primary-600" />
                <span className="font-medium">New Questionnaire</span>
                <span className="text-xs text-gray-500">Upload or create</span>
              </Button>
            </Link>
            <Link href="/dashboard/documents" className="block">
              <Button variant="outline" className="w-full h-24 flex flex-col items-center justify-center gap-2">
                <FileText className="h-8 w-8 text-green-600" />
                <span className="font-medium">Upload Document</span>
                <span className="text-xs text-gray-500">Add to knowledge base</span>
              </Button>
            </Link>
            {/* The integrations tile used to advertise Jira, Confluence, GitHub and Slack.
                No controller serves /api/integrations -- there is no backend behind any of
                them -- so the four status calls 404. Pointing at the review queue instead:
                a tile that goes somewhere real. */}
            <Link href="/dashboard/review-queue" className="block">
              <Button variant="outline" className="w-full h-24 flex flex-col items-center justify-center gap-2">
                <ExternalLink className="h-8 w-8 text-sky-600" />
                <span className="font-medium">Review Queue</span>
                <span className="text-xs text-gray-500">Approve or correct answers</span>
              </Button>
            </Link>
            <Link href="/api/swagger-ui.html" target="_blank" rel="noopener noreferrer" className="block">
              <Button variant="outline" className="w-full h-24 flex flex-col items-center justify-center gap-2">
                <ExternalLink className="h-8 w-8 text-purple-600" />
                <span className="font-medium">API Documentation</span>
                <span className="text-xs text-gray-500">Explore endpoints</span>
              </Button>
            </Link>
          </div>
        </CardContent>
      </Card>
    </div>
  );
}
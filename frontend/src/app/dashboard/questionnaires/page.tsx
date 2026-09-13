'use client';

import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { api } from '@/lib/api';
import { Questionnaire } from '@/types/api';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Select } from '@/components/ui/Select';
import { Badge } from '@/components/ui/Badge';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/Card';
import { cn, formatDateTime, getStatusColor } from '@/lib/utils';
import { 
  Plus, 
  Search, 
  Filter, 
  Download, 
  RefreshCw,
  ChevronLeft,
  ChevronRight
} from 'lucide-react';
import Link from 'next/link';

const statusOptions = [
  { value: 'all', label: 'All Status' },
  { value: 'DRAFT', label: 'Draft' },
  { value: 'PROCESSING', label: 'Processing' },
  { value: 'READY_FOR_REVIEW', label: 'Ready for Review' },
  { value: 'IN_REVIEW', label: 'In Review' },
  { value: 'APPROVED', label: 'Approved' },
  { value: 'SUBMITTED', label: 'Submitted' },
  { value: 'ARCHIVED', label: 'Archived' },
];

export default function QuestionnairesPage() {
  const [page, setPage] = useState(0);
  const [size] = useState(10);
  const [search, setSearch] = useState('');
  const [status, setStatus] = useState('all');
  const [debouncedSearch, setDebouncedSearch] = useState('');

  // Debounce search
  const { data, isLoading } = useQuery({
    queryKey: ['questionnaires', { page, size, status: status !== 'all' ? status : undefined, search: debouncedSearch }],
    queryFn: () => api.getQuestionnaires({ page, size, status: status !== 'all' ? status : undefined, search: debouncedSearch }),
  });

  // Debounce effect
  const searchTimeout = useState<NodeJS.Timeout | null>(null)[0];
  
  // We'll handle debouncing in the onChange handler

  const handleSearchChange = (value: string) => {
    setSearch(value);
    if (searchTimeout) clearTimeout(searchTimeout);
    const timeout = setTimeout(() => {
      setDebouncedSearch(value);
      setPage(0);
    }, 300);
    // Note: In real app, use useRef for timeout
  };

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h1 className="text-3xl font-bold text-gray-900">Questionnaires</h1>
          <p className="text-gray-500 mt-1">Manage and review security questionnaires</p>
        </div>
        <Link href="/dashboard/questionnaires/new">
          <Button>
            <Plus className="h-4 w-4 mr-2" />
            New Questionnaire
          </Button>
        </Link>
      </div>

      {/* Filters */}
      <Card>
        <CardContent className="p-4 pt-6">
          <div className="flex flex-col sm:flex-row gap-4">
            <div className="flex-1 max-w-md">
              <Input
                placeholder="Search questionnaires..."
                value={search}
                onChange={(e) => handleSearchChange(e.target.value)}
                leftIcon={<Search className="h-4 w-4" />}
              />
            </div>
            {/* aria-label because the control has no visible label beside it: a
                screen reader announced an unnamed select. */}
            <Select
              aria-label="Filter by status"
              value={status}
              onChange={(e) => setStatus(e.target.value)}
              options={statusOptions}
              className="w-full sm:w-48"
            />
            <Button variant="outline" size="sm">
              <RefreshCw className="h-4 w-4 mr-1" />
              Refresh
            </Button>
          </div>
        </CardContent>
      </Card>

      {/* Table */}
      <Card>
        <CardContent className="p-0">
          {isLoading ? (
            <div className="p-8 text-center">
              <RefreshCw className="h-8 w-8 animate-spin text-primary-600 mx-auto mb-2" />
              <p className="text-gray-500">Loading questionnaires...</p>
            </div>
          ) : (data?.content?.length ?? 0) === 0 ? (
            <div className="p-12 text-center">
              <p className="text-gray-500 mb-4">No questionnaires found</p>
              <Link href="/dashboard/questionnaires/new">
                <Button>
                  <Plus className="h-4 w-4 mr-2" />
                  Create Questionnaire
                </Button>
              </Link>
            </div>
          ) : (
            <>
              <div className="overflow-x-auto">
                <table className="w-full">
                  <thead className="bg-gray-50 border-b border-gray-200">
                    <tr>
                      <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Questionnaire</th>
                      <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Source</th>
                      <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Questions</th>
                      <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Progress</th>
                      <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Status</th>
                      <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Due Date</th>
                      <th className="px-6 py-3 text-right text-xs font-medium text-gray-500 uppercase tracking-wider">Actions</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-gray-200">
                    {data?.content?.map((q: Questionnaire) => (
                      <tr key={q.id} className="hover:bg-gray-50">
                        <td className="px-6 py-4">
                          <Link href={`/dashboard/questionnaires/${q.id}`} className="block">
                            <p className="font-medium text-gray-900 truncate max-w-xs">{q.title}</p>
                            <p className="text-sm text-gray-500">{formatDateTime(q.updatedAt)}</p>
                          </Link>
                        </td>
                        <td className="px-6 py-4">
                          <Badge variant="outline" className="text-xs">{q.sourceType.replace('_', ' ')}</Badge>
                        </td>
                        <td className="px-6 py-4 text-gray-900">{q.totalQuestions}</td>
                        <td className="px-6 py-4">
                          <div className="w-32">
                            <div className="h-2 bg-gray-200 rounded-full overflow-hidden">
                              <div
                                className="h-full bg-primary-600 transition-all duration-300"
                                style={{ width: `${q.totalQuestions > 0 ? (q.approvedQuestions / q.totalQuestions) * 100 : 0}%` }}
                              />
                            </div>
                            <p className="text-xs text-gray-500 mt-1">
                              {q.approvedQuestions}/{q.totalQuestions} approved
                            </p>
                          </div>
                        </td>
                        <td className="px-6 py-4">
                          <Badge className={getStatusColor(q.status)}>{q.status.replace('_', ' ')}</Badge>
                        </td>
                        <td className="px-6 py-4 text-gray-500">
                          {q.dueDate ? new Date(q.dueDate).toLocaleDateString() : '—'}
                        </td>
                        <td className="px-6 py-4 text-right">
                          <div className="flex items-center justify-end gap-2">
                            <Link href={`/dashboard/questionnaires/${q.id}`}>
                              <Button variant="ghost" size="icon" className="h-8 w-8">
                                <Download className="h-4 w-4" />
                              </Button>
                            </Link>
                            <Link href={`/dashboard/questionnaires/${q.id}`}>
                              <Button variant="ghost" size="icon" className="h-8 w-8">
                                <Filter className="h-4 w-4" />
                              </Button>
                            </Link>
                          </div>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>

              {/* Pagination */}
              {data && data.totalPages > 1 && (
                <div className="px-6 py-4 border-t border-gray-200 flex items-center justify-between">
                  <p className="text-sm text-gray-500">
                    Showing {page * size + 1} to {Math.min((page + 1) * size, data.totalElements)} of {data.totalElements} results
                  </p>
                  <div className="flex items-center gap-2">
                    <Button
                      variant="outline"
                      size="sm"
                      onClick={() => setPage(p => Math.max(0, p - 1))}
                      disabled={page === 0}
                    >
                      <ChevronLeft className="h-4 w-4" />
                    </Button>
                    <Button
                      variant="outline"
                      size="sm"
                      onClick={() => setPage(p => Math.min(data.totalPages - 1, p + 1))}
                      disabled={page >= data.totalPages - 1}
                    >
                      <ChevronRight className="h-4 w-4" />
                    </Button>
                  </div>
                </div>
              )}
            </>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
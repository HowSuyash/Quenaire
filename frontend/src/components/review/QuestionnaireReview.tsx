'use client';

import type { ExportFormat } from '@/lib/api';

import { Questionnaire, Question, Answer } from '@/types/api';
import { QuestionCard } from '@/components/review/QuestionCard';
import { Button } from '@/components/ui/Button';
import { Badge } from '@/components/ui/Badge';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/Card';
import { cn, formatDateTime, getStatusColor } from '@/lib/utils';
import { 
  Download, 
  RefreshCw, 
  ExternalLink,
  CheckCircle,
  Clock,
  AlertTriangle
} from 'lucide-react';
import { useState } from 'react';

interface QuestionnaireReviewProps {
  questionnaire: Questionnaire;
  questions: Question[];
  answers: Map<string, Answer>;
  onGenerateAnswer: (questionId: string) => void;
  onUpdateAnswer: (questionId: string, content: string, status?: string) => void;
  onApproveAnswer: (questionId: string) => void;
  onRejectAnswer: (questionId: string, reason: string) => void;
  onExport: (format: ExportFormat) => void;
  isGenerating?: Record<string, boolean>;
  isUpdating?: Record<string, boolean>;
  isExporting?: boolean;
}

export function QuestionnaireReview({
  questionnaire,
  questions,
  answers,
  onGenerateAnswer,
  onUpdateAnswer,
  onApproveAnswer,
  onRejectAnswer,
  onExport,
  isGenerating = {},
  isUpdating = {},
  isExporting,
}: QuestionnaireReviewProps) {
  const [filter, setFilter] = useState<'all' | 'pending' | 'needs_review' | 'approved' | 'rejected' | 'ai_generated' | 'draft'>('all');
  const [showOnlyUnapproved, setShowOnlyUnapproved] = useState(false);

  const filteredQuestions = questions.filter((q) => {
    const answer = answers.get(q.id);
    // Use question status for filtering, as it has more states (NEEDS_REVIEW, PENDING, etc.)
    const status = answer?.status || q.status || 'DRAFT';
    
    if (filter !== 'all' && status.toLowerCase() !== filter.toUpperCase()) return false;
    if (showOnlyUnapproved && (answer?.status === 'APPROVED' || q.status === 'APPROVED')) return false;
    return true;
  });

  const stats = {
    total: questions.length,
    approved: questions.filter((q) => answers.get(q.id)?.status === 'APPROVED' || q.status === 'APPROVED').length,
    needsReview: questions.filter((q) => q.status === 'NEEDS_REVIEW' || q.status === 'AI_DRAFTED' || answers.get(q.id)?.status === 'AI_GENERATED').length,
    pending: questions.filter((q) => !answers.get(q.id) || q.status === 'PENDING' || answers.get(q.id)?.status === 'DRAFT').length,
    rejected: questions.filter((q) => answers.get(q.id)?.status === 'REJECTED' || q.status === 'REJECTED').length,
  };

  return (
    <div className="h-full flex flex-col">
      {/* Header */}
      <div className="px-6 py-4 border-b border-gray-200 bg-white sticky top-0 z-10">
        <div className="flex items-center justify-between flex-wrap gap-4 mb-4">
          <div>
            <h1 className="text-2xl font-bold text-gray-900">{questionnaire.title}</h1>
            <p className="text-gray-500 mt-1">{questionnaire.description}</p>
          </div>
          <div className="flex items-center gap-3">
            <Badge className={getStatusColor(questionnaire.status)}>
              {questionnaire.status.replace('_', ' ')}
            </Badge>
            {questionnaire.dueDate && (
              <div className="flex items-center gap-1 text-sm text-gray-600">
                <Clock className="h-4 w-4" />
                Due: {new Date(questionnaire.dueDate).toLocaleDateString()}
              </div>
            )}
          </div>
        </div>

        {/* Stats Bar */}
        <div className="flex flex-wrap gap-4 mb-4">
          <div className="flex items-center gap-2 px-3 py-2 bg-green-50 rounded-lg">
            <CheckCircle className="h-5 w-5 text-green-600" />
            <div>
              <p className="text-2xl font-bold text-green-700">{stats.approved}</p>
              <p className="text-xs text-green-600">Approved</p>
            </div>
          </div>
          <div className="flex items-center gap-2 px-3 py-2 bg-yellow-50 rounded-lg">
            <AlertTriangle className="h-5 w-5 text-yellow-600" />
            <div>
              <p className="text-2xl font-bold text-yellow-700">{stats.needsReview}</p>
              <p className="text-xs text-yellow-600">Need Review</p>
            </div>
          </div>
          <div className="flex items-center gap-2 px-3 py-2 bg-gray-50 rounded-lg">
            <Clock className="h-5 w-5 text-gray-600" />
            <div>
              <p className="text-2xl font-bold text-gray-700">{stats.pending}</p>
              <p className="text-xs text-gray-600">Pending</p>
            </div>
          </div>
          <div className="flex items-center gap-2 px-3 py-2 bg-red-50 rounded-lg">
            <AlertTriangle className="h-5 w-5 text-red-600" />
            <div>
              <p className="text-2xl font-bold text-red-700">{stats.rejected}</p>
              <p className="text-xs text-red-600">Rejected</p>
            </div>
          </div>
          <div className="flex items-center gap-2 px-3 py-2 bg-blue-50 rounded-lg ml-auto">
            <p className="text-2xl font-bold text-blue-700">{stats.total}</p>
            <p className="text-xs text-blue-600">Total Questions</p>
          </div>
        </div>

        {/* Filters */}
        <div className="flex flex-wrap items-center gap-4">
          <div className="flex gap-2">
            {(['all', 'pending', 'needs_review', 'ai_generated', 'approved', 'rejected', 'draft'] as const).map((f) => (
              <button
                key={f}
                onClick={() => setFilter(f)}
                className={cn(
                  'px-3 py-1.5 text-sm font-medium rounded-lg transition-colors',
                  filter === f
                    ? 'bg-primary-600 text-white'
                    : 'bg-gray-100 text-gray-700 hover:bg-gray-200'
                )}
              >
                {f.replace('_', ' ')}
              </button>
            ))}
          </div>
          <label className="flex items-center gap-2 text-sm text-gray-700">
            <input
              type="checkbox"
              checked={showOnlyUnapproved}
              onChange={(e) => setShowOnlyUnapproved(e.target.checked)}
              className="rounded border-gray-300 text-primary-600 focus:ring-primary-500"
            />
            Hide approved
          </label>
          <div className="flex gap-2 ml-auto">
            <Button variant="outline" onClick={() => onExport('excel')} disabled={isExporting} size="sm">
              <Download className="h-4 w-4 mr-1" />
              Excel
            </Button>
            <Button variant="outline" onClick={() => onExport('pdf')} disabled={isExporting} size="sm">
              <Download className="h-4 w-4 mr-1" />
              PDF
            </Button>
            <Button
              variant="outline"
              onClick={() => onExport('pdf-approved')}
              disabled={isExporting}
              size="sm"
              title="Only approved answers"
            >
              <Download className="h-4 w-4 mr-1" />
              Approved PDF
            </Button>
          </div>
        </div>
      </div>

      {/* Questions List */}
      <div className="flex-1 overflow-y-auto p-6 space-y-4">
        {filteredQuestions.length === 0 ? (
          <div className="text-center py-12 text-gray-500">
            <p className="text-lg">No questions match the current filter</p>
          </div>
        ) : (
          filteredQuestions.map((question, index) => {
            const answer = answers.get(question.id);
            return (
              <QuestionCard
                key={question.id}
                question={question}
                answer={answer}
                onGenerate={onGenerateAnswer}
                onUpdate={onUpdateAnswer}
                onApprove={onApproveAnswer}
                onReject={onRejectAnswer}
                isGenerating={isGenerating[question.id]}
                isUpdating={isUpdating[question.id]}
              />
            );
          })
        )}
      </div>

      {/* Progress Footer */}
      <div className="px-6 py-4 border-t border-gray-200 bg-white sticky bottom-0 z-10">
        <div className="flex items-center justify-between">
          <div className="text-sm text-gray-600">
            Showing {filteredQuestions.length} of {questions.length} questions
          </div>
          <div className="flex items-center gap-4">
            <div className="w-48">
              <div className="h-2 bg-gray-200 rounded-full overflow-hidden">
                <div
                  className="h-full bg-primary-600 transition-all duration-300"
                  style={{ width: `${(stats.approved / stats.total) * 100}%` }}
                />
              </div>
            </div>
            <span className="text-sm font-medium text-gray-700">
              {stats.total > 0 ? Math.round((stats.approved / stats.total) * 100) : 0}% Complete
            </span>
          </div>
        </div>
      </div>
    </div>
  );
}
'use client';

import { useState } from 'react';
import { useParams } from 'next/navigation';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { api, EXPORT_EXTENSIONS, type ExportFormat } from '@/lib/api';
import { Questionnaire, Question, Answer } from '@/types/api';
import { QuestionnaireReview } from '@/components/review/QuestionnaireReview';
import { Button } from '@/components/ui/Button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/Card';
import { Badge } from '@/components/ui/Badge';
import { cn, formatDateTime, getStatusColor } from '@/lib/utils';
import { 
  ArrowLeft, 
  Download, 
  RefreshCw, 
  Play,
  ExternalLink,
  Send,
  Settings
} from 'lucide-react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';

export default function QuestionnaireDetailPage() {
  const params = useParams();
  const router = useRouter();
  const questionnaireId = params.id as string;
  const queryClient = useQueryClient();

  const [generatingQuestions, setGeneratingQuestions] = useState<Record<string, boolean>>({});
  const [updatingQuestions, setUpdatingQuestions] = useState<Record<string, boolean>>({});
  const [exporting, setExporting] = useState(false);

  const { data: questionnaire, isLoading: loadingQuestionnaire } = useQuery({
    queryKey: ['questionnaire', questionnaireId],
    queryFn: () => api.getQuestionnaire(questionnaireId),
    enabled: !!questionnaireId,
  });

  const { data: questionsData, isLoading: loadingQuestions } = useQuery({
    queryKey: ['questions', questionnaireId],
    queryFn: () => api.getQuestions(questionnaireId),
    enabled: !!questionnaireId,
  });

  // One request for the whole questionnaire. This used to await one request per
  // question in turn, and swallowed every failure as "no answer yet".
  const { data: answersData } = useQuery<Map<string, Answer>>({
    queryKey: ['answers', questionnaireId],
    queryFn: () => api.getAnswers(questionnaireId),
    enabled: !!questionsData?.content?.length,
  });

  // An answer's status is also the question's status in this model, so both lists
  // go stale together after any review action.
  const refreshReview = () => {
    queryClient.invalidateQueries({ queryKey: ['answers', questionnaireId] });
    queryClient.invalidateQueries({ queryKey: ['questions', questionnaireId] });
  };

  const generateAnswerMutation = useMutation({
    mutationFn: (questionId: string) => api.generateAnswer(questionId),
    onMutate: (questionId) => {
      setGeneratingQuestions(prev => ({ ...prev, [questionId]: true }));
    },
    onSuccess: (_, questionId) => {
      refreshReview();
      setGeneratingQuestions(prev => ({ ...prev, [questionId]: false }));
    },
    onError: (_, questionId) => {
      setGeneratingQuestions(prev => ({ ...prev, [questionId]: false }));
    },
  });

  const updateAnswerMutation = useMutation({
    mutationFn: ({ questionId, content, status }: { questionId: string; content: string; status?: string }) =>
      api.updateAnswer(questionId, { content, status }),
    onMutate: ({ questionId }) => {
      setUpdatingQuestions(prev => ({ ...prev, [questionId]: true }));
    },
    onSuccess: (_, { questionId }) => {
      refreshReview();
      setUpdatingQuestions(prev => ({ ...prev, [questionId]: false }));
    },
    onError: (_, { questionId }) => {
      setUpdatingQuestions(prev => ({ ...prev, [questionId]: false }));
    },
  });

  const approveAnswerMutation = useMutation({
    mutationFn: (questionId: string) => api.approveAnswer(questionId),
    onSuccess: () => {
      refreshReview();
      queryClient.invalidateQueries({ queryKey: ['questionnaire', questionnaireId] });
    },
  });

  const rejectAnswerMutation = useMutation({
    mutationFn: ({ questionId, reason }: { questionId: string; reason: string }) =>
      api.rejectAnswer(questionId, reason),
    onSuccess: () => {
      refreshReview();
      queryClient.invalidateQueries({ queryKey: ['questionnaire', questionnaireId] });
    },
  });

  const processQuestionnaireMutation = useMutation({
    mutationFn: () => api.processQuestionnaire(questionnaireId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['questionnaire', questionnaireId] });
      refreshReview();
    },
  });

  const exportMutation = useMutation({
    mutationFn: (format: ExportFormat) => api.exportQuestionnaire(questionnaireId, format),
    onMutate: () => setExporting(true),
    onSuccess: (blob, format) => {
      const url = window.URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      // The extension follows the endpoint, not the button label: the
      // "approved" variant is still a .pdf.
      a.download = `${questionnaire?.title || 'questionnaire'}.${EXPORT_EXTENSIONS[format]}`;
      document.body.appendChild(a);
      a.click();
      window.URL.revokeObjectURL(url);
      document.body.removeChild(a);
      setExporting(false);
    },
    onError: () => setExporting(false),
  });

  const loopioMutation = useMutation({
    mutationFn: () => api.submitToLoopio(questionnaireId, { approvedOnly: true }),
  });

  if (loadingQuestionnaire) {
    return (
      <div className="space-y-6">
        <div className="animate-pulse">
          <div className="h-8 bg-gray-200 rounded w-3/4 mb-2"></div>
          <div className="h-4 bg-gray-200 rounded w-1/2"></div>
        </div>
        <div className="grid lg:grid-cols-4 gap-6">
          {[1,2,3,4].map(i => (
            <Card key={i}><CardContent className="p-6"><div className="h-8 bg-gray-200 rounded w-1/2"></div></CardContent></Card>
          ))}
        </div>
      </div>
    );
  }

  if (!questionnaire) {
    return (
      <div className="text-center py-12">
        <p className="text-gray-500">Questionnaire not found</p>
        <Link href="/dashboard/questionnaires" className="mt-4 inline-block">
          <Button>Back to Questionnaires</Button>
        </Link>
      </div>
    );
  }

  const questions = questionsData?.content || [];
  const answers = answersData || new Map();

  // Wrapper functions to match QuestionnaireReview expected signatures
  const handleGenerateAnswer = (questionId: string) => generateAnswerMutation.mutate(questionId);
  const handleUpdateAnswer = (questionId: string, content: string, status?: string) => updateAnswerMutation.mutate({ questionId, content, status });
  const handleApproveAnswer = (questionId: string) => approveAnswerMutation.mutate(questionId);
  const handleRejectAnswer = (questionId: string, reason: string) => rejectAnswerMutation.mutate({ questionId, reason });
  const handleExport = (format: ExportFormat) => exportMutation.mutate(format);

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div className="flex items-center gap-4">
          <Link href="/dashboard/questionnaires" onClick={(e) => e.preventDefault()} className="p-2 rounded-lg hover:bg-gray-100">
            <ArrowLeft className="h-5 w-5 text-gray-500" />
          </Link>
          <div>
            <h1 className="text-3xl font-bold text-gray-900">{questionnaire.title}</h1>
            <p className="text-gray-500 mt-1">{questionnaire.description || 'No description'}</p>
          </div>
        </div>
        <div className="flex items-center gap-3 flex-wrap">
          <Badge className={getStatusColor(questionnaire.status)}>{questionnaire.status.replace('_', ' ')}</Badge>
          <Link href="/dashboard/questionnaires">
            <Button variant="outline" size="sm">
              <ArrowLeft className="h-4 w-4 mr-1" />
              Back
            </Button>
          </Link>
          <Button variant="outline" onClick={() => exportMutation.mutate('excel')} disabled={exporting} size="sm">
            <Download className="h-4 w-4 mr-1" />
            Excel
          </Button>
          <Button variant="outline" onClick={() => exportMutation.mutate('pdf')} disabled={exporting} size="sm">
            <Download className="h-4 w-4 mr-1" />
            PDF
          </Button>
          <Button
            variant="outline"
            onClick={() => exportMutation.mutate('pdf-approved')}
            disabled={exporting}
            size="sm"
            title="Only approved answers — what you would send to a prospect"
          >
            <Download className="h-4 w-4 mr-1" />
            Approved PDF
          </Button>
          <Button variant="outline" onClick={() => exportMutation.mutate('summary')} disabled={exporting} size="sm">
            <Download className="h-4 w-4 mr-1" />
            Summary
          </Button>
          <Button
            variant="outline"
            onClick={() => loopioMutation.mutate()}
            disabled={loopioMutation.isPending}
            size="sm"
            title="Push approved answers into Loopio"
          >
            <Send className="h-4 w-4 mr-1" />
            {loopioMutation.isPending ? 'Submitting…' : 'Submit to Loopio'}
          </Button>
          {questionnaire.status === 'DRAFT' && (
            <Button onClick={() => processQuestionnaireMutation.mutate()} disabled={processQuestionnaireMutation.isPending}>
              <Play className="h-4 w-4 mr-1" />
              {processQuestionnaireMutation.isPending ? 'Processing...' : 'Process'}
            </Button>
          )}
        </div>
      </div>

      {/* Questionnaire Review - Main Component */}
      <QuestionnaireReview
        questionnaire={questionnaire}
        questions={questions}
        answers={answers}
        onGenerateAnswer={handleGenerateAnswer}
        onUpdateAnswer={handleUpdateAnswer}
        onApproveAnswer={handleApproveAnswer}
        onRejectAnswer={handleRejectAnswer}
        onExport={handleExport}
        isGenerating={generatingQuestions}
        isUpdating={updatingQuestions}
        isExporting={exporting}
      />
    </div>
  );
}
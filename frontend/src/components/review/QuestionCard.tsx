'use client';

import { Question, Answer, Citation } from '@/types/api';
import { Button } from '@/components/ui/Button';
import { Badge } from '@/components/ui/Badge';
import { Textarea } from '@/components/ui/Textarea';
import { Select } from '@/components/ui/Select';
import { cn, formatDateTime, getQuestionTypeIcon, getStatusColor } from '@/lib/utils';
import { 
  FileText, 
  Copy, 
  CheckCircle, 
  XCircle, 
  Edit, 
  AlertTriangle,
  ExternalLink,
  ChevronDown,
  ChevronUp
} from 'lucide-react';
import { useState } from 'react';

interface QuestionCardProps {
  question: Question;
  answer?: Answer;
  onApprove: (questionId: string) => void;
  onReject: (questionId: string, reason: string) => void;
  onUpdate: (questionId: string, content: string, status?: string) => void;
  onGenerate: (questionId: string) => void;
  isGenerating?: boolean;
  isUpdating?: boolean;
  showCitations?: boolean;
}

export function QuestionCard({
  question,
  answer,
  onApprove,
  onReject,
  onUpdate,
  onGenerate,
  isGenerating,
  isUpdating,
  showCitations = true,
}: QuestionCardProps) {
  const [showCitationsPanel, setShowCitationsPanel] = useState(false);
  const [rejectReason, setRejectReason] = useState('');
  const [showRejectDialog, setShowRejectDialog] = useState(false);
  const [isEditing, setIsEditing] = useState(false);
  const [editContent, setEditContent] = useState(answer?.content || answer?.aiGeneratedContent || '');

  const currentContent = answer?.content || answer?.aiGeneratedContent || '';
  const displayContent = isEditing ? editContent : currentContent;
  const answerStatus: 'DRAFT' | 'AI_GENERATED' | 'HUMAN_EDITED' | 'APPROVED' | 'REJECTED' = answer?.status || 'DRAFT';
  const isApproved = answerStatus === 'APPROVED';

  const handleApprove = () => {
    onApprove(question.id);
  };

  const handleReject = () => {
    if (!rejectReason.trim()) return;
    onReject(question.id, rejectReason);
    setRejectReason('');
    setShowRejectDialog(false);
  };

  const handleSave = () => {
    onUpdate(question.id, editContent, 'HUMAN_EDITED');
    setIsEditing(false);
  };

  const handleGenerate = () => {
    onGenerate(question.id);
  };

  const handleCancelEdit = () => {
    setEditContent(currentContent);
    setIsEditing(false);
  };

  const citations = answer?.citationsJson || [];

  return (
    <div className="border border-gray-200 rounded-xl bg-white overflow-hidden">
      {/* Question Header */}
      <div className="px-6 py-4 border-b border-gray-100 bg-gray-50">
        <div className="flex items-start justify-between gap-4">
          <div className="flex-1 min-w-0">
            <div className="flex items-center gap-3 flex-wrap mb-2">
              <span className="font-mono text-sm text-gray-500">Q{question.questionNumber}</span>
              <Badge variant="outline" className="text-xs">
                {question.questionType.replace('_', ' ')}
              </Badge>
              {question.isRequired && (
                <Badge variant="destructive" className="text-xs">Required</Badge>
              )}
              <Badge className={getStatusColor(question.status)} variant="default">
                {question.status.replace('_', ' ')}
              </Badge>
            </div>
            <p className="text-lg font-medium text-gray-900">{question.text}</p>
            {question.section && (
              <p className="mt-1 text-sm text-gray-500">Section: {question.section}</p>
            )}
          </div>
        </div>
      </div>

      {/* Answer Section */}
      <div className="p-6">
        {answer && answer.aiGeneratedContent && !answer.content && !isEditing ? (
          // AI Generated Answer - pending review
          <div className="space-y-4">
            <div className="flex items-center gap-3 p-3 bg-blue-50 rounded-lg border border-blue-100">
              <AlertTriangle className="h-5 w-5 text-blue-600 flex-shrink-0" />
              <div>
                <p className="font-medium text-blue-800">AI Generated Answer - Review Required</p>
                <p className="text-sm text-blue-600">Please review and approve, edit, or reject this answer.</p>
              </div>
            </div>

            <div className="prose prose-sm max-w-none bg-gray-50 p-4 rounded-lg border border-gray-100">
              {answer.aiGeneratedContent}
            </div>

            {showCitations && citations.length > 0 && (
              <div className="space-y-2">
                <button
                  onClick={() => setShowCitationsPanel(!showCitationsPanel)}
                  className="flex items-center gap-2 text-sm text-primary-600 hover:text-primary-700"
                >
                  {showCitationsPanel ? <ChevronUp className="h-4 w-4" /> : <ChevronDown className="h-4 w-4" />}
                  <span>Citations ({citations.length})</span>
                </button>
                {showCitationsPanel && (
                  <div className="space-y-2 pl-4 border-l-2 border-primary-200">
                    {citations.map((citation, index) => (
                      <div key={index} className="bg-gray-50 p-3 rounded-lg border border-gray-100">
                        <div className="flex items-start justify-between gap-2">
                          <div className="flex-1">
                            <p className="font-medium text-sm text-gray-900">{citation.documentTitle}</p>
                            <p className="text-xs text-gray-500 mt-1">Relevance: {Math.round(citation.relevanceScore * 100)}%</p>
                            <p className="text-sm text-gray-600 mt-2 line-clamp-2">{citation.chunkContent}</p>
                          </div>
                          <button
                            className="text-primary-600 hover:text-primary-700 text-xs flex items-center gap-1"
                          >
                            <ExternalLink className="h-3 w-3" />
                            View
                          </button>
                        </div>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            )}

            <div className="flex items-center gap-3 pt-4 border-t border-gray-100">
              <Button onClick={handleApprove} size="sm">
                <CheckCircle className="h-4 w-4 mr-1" />
                Approve
              </Button>
              <Button variant="outline" onClick={() => setIsEditing(true)} size="sm">
                <Edit className="h-4 w-4 mr-1" />
                Edit
              </Button>
              <Button variant="destructive" onClick={() => setShowRejectDialog(true)} size="sm">
                <XCircle className="h-4 w-4 mr-1" />
                Reject
              </Button>
            </div>
          </div>
        ) : (
          // Editable answer or approved answer
          <div className="space-y-4">
            {isEditing ? (
              <>
                <Textarea
                  value={editContent}
                  onChange={(e) => setEditContent(e.target.value)}
                  placeholder="Enter your answer..."
                  className="min-h-[120px]"
                />
                <div className="flex items-center gap-3">
                  <Button onClick={handleSave} disabled={isUpdating} size="sm">
                    Save
                  </Button>
                  <Button variant="ghost" onClick={handleCancelEdit} size="sm">
                    Cancel
                  </Button>
                </div>
              </>
            ) : (
              <>
                <div className="prose prose-sm max-w-none bg-gray-50 p-4 rounded-lg border border-gray-100 min-h-[100px]">
                  {displayContent || <span className="text-gray-400 italic">No answer yet</span>}
                </div>
                <div className="flex items-center gap-3">
                  {answerStatus === 'APPROVED' ? (
                    <Badge variant="success" className="flex items-center gap-1">
                      <CheckCircle className="h-3 w-3" />
                      Approved
                    </Badge>
                  ) : answerStatus === 'REJECTED' ? (
                    <Badge variant="destructive" className="flex items-center gap-1">
                      <XCircle className="h-3 w-3" />
                      Rejected
                    </Badge>
                  ) : answerStatus === 'AI_GENERATED' ? (
                    <Badge variant="info" className="flex items-center gap-1">
                      AI Generated
                    </Badge>
                  ) : answerStatus === 'HUMAN_EDITED' ? (
                    <Badge variant="default" className="flex items-center gap-1">
                      <Edit className="h-3 w-3" />
                      Human Edited
                    </Badge>
                  ) : (
                    <Badge variant="secondary" className="flex items-center gap-1">
                      Draft
                    </Badge>
                  )}
                  <Button variant="ghost" onClick={() => setIsEditing(true)} size="sm" disabled={answerStatus === 'APPROVED'}>
                    <Edit className="h-4 w-4 mr-1" />
                    Edit
                  </Button>
                  {!isApproved && (
                    <>
                      <Button variant="outline" onClick={handleApprove} size="sm" disabled={isApproved}>
                        <CheckCircle className="h-4 w-4 mr-1" />
                        Approve
                      </Button>
                      <Button variant="destructive" onClick={() => setShowRejectDialog(true)} size="sm">
                        <XCircle className="h-4 w-4 mr-1" />
                        Reject
                      </Button>
                    </>
                  )}
                </div>
              </>
            )}

            {showCitations && citations.length > 0 && (
              <div className="space-y-2 pt-4 border-t border-gray-100">
                <button
                  onClick={() => setShowCitationsPanel(!showCitationsPanel)}
                  className="flex items-center gap-2 text-sm text-primary-600 hover:text-primary-700"
                >
                  {showCitationsPanel ? <ChevronUp className="h-4 w-4" /> : <ChevronDown className="h-4 w-4" />}
                  <span>Citations ({citations.length})</span>
                </button>
                {showCitationsPanel && (
                  <div className="space-y-2 pl-4 border-l-2 border-primary-200">
                    {citations.map((citation, index) => (
                      <div key={index} className="bg-gray-50 p-3 rounded-lg border border-gray-100">
                        <div className="flex items-start justify-between gap-2">
                          <div className="flex-1">
                            <p className="font-medium text-sm text-gray-900">{citation.documentTitle}</p>
                            <p className="text-xs text-gray-500 mt-1">Relevance: {Math.round(citation.relevanceScore * 100)}%</p>
                            <p className="text-sm text-gray-600 mt-2 line-clamp-2">{citation.chunkContent}</p>
                          </div>
                        </div>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            )}

            {rejectReason && (
              <div className="p-3 bg-red-50 border border-red-100 rounded-lg">
                <p className="font-medium text-red-800">Rejection Reason:</p>
                <p className="text-sm text-red-600 mt-1">{rejectReason}</p>
              </div>
            )}
          </div>
        )}

        {!answer && (
          // No answer yet - generate button
          <div className="text-center py-8">
            <p className="text-gray-500 mb-4">No answer generated yet</p>
            <Button onClick={handleGenerate} disabled={isGenerating}>
              {isGenerating ? 'Generating...' : 'Generate Answer'}
            </Button>
          </div>
        )}

        {/* Reject Dialog */}
        {showRejectDialog && (
          <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50" onClick={() => setShowRejectDialog(false)}>
            <div className="bg-white rounded-xl p-6 w-full max-w-md" onClick={(e) => e.stopPropagation()}>
              <h3 className="text-lg font-semibold mb-4">Reject Answer</h3>
              <p className="text-gray-600 mb-4">Please provide a reason for rejecting this answer.</p>
              <Textarea
                value={rejectReason}
                onChange={(e) => setRejectReason(e.target.value)}
                placeholder="Enter rejection reason..."
                rows={3}
              />
              <div className="flex justify-end gap-3 mt-4">
                <Button variant="ghost" onClick={() => { setShowRejectDialog(false); setRejectReason(''); }}>
                  Cancel
                </Button>
                <Button variant="destructive" onClick={handleReject}>
                  Reject
                </Button>
              </div>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
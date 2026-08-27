'use client';

import { useEffect, useRef, useState } from 'react';
import {
  CheckCircle, XCircle, Pencil, AlertTriangle, Clock, FileText,
  ChevronDown, ChevronRight, Lock, Loader2, Save, X,
} from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { Badge } from '@/components/ui/Badge';
import { Textarea } from '@/components/ui/Textarea';
import { ReviewQueueItem } from '@/types/api';
import { cn, formatRelativeTime } from '@/lib/utils';

export interface ReviewQueueCardProps {
  item: ReviewQueueItem;
  selected: boolean;
  checked: boolean;
  editing: boolean;
  /** Display name of another reviewer holding the edit lock, if any. */
  lockedBy?: string;
  busy?: boolean;
  onSelect: () => void;
  onToggleCheck: () => void;
  onStartEdit: () => void;
  onCancelEdit: () => void;
  onSaveEdit: (answer: string, approve: boolean) => void;
  onApprove: () => void;
  onReject: () => void;
  onSelectOption: (option: string) => void;
}

const CONFIDENCE_STYLES: Record<string, { badge: string; bar: string; label: string }> = {
  HIGH: { badge: 'bg-green-100 text-green-700', bar: 'bg-green-500', label: 'High confidence' },
  MEDIUM: { badge: 'bg-yellow-100 text-yellow-700', bar: 'bg-yellow-500', label: 'Needs a look' },
  LOW: { badge: 'bg-red-100 text-red-700', bar: 'bg-red-500', label: 'Needs SecOps review' },
};

export function ReviewQueueCard({
  item,
  selected,
  checked,
  editing,
  lockedBy,
  busy,
  onSelect,
  onToggleCheck,
  onStartEdit,
  onCancelEdit,
  onSaveEdit,
  onApprove,
  onReject,
  onSelectOption,
}: ReviewQueueCardProps) {
  const answer = item.finalAnswer ?? item.aiAnswer ?? '';
  const [draft, setDraft] = useState(answer);
  const [citationsOpen, setCitationsOpen] = useState(false);
  const cardRef = useRef<HTMLDivElement>(null);
  const textareaRef = useRef<HTMLTextAreaElement>(null);

  const confidence = CONFIDENCE_STYLES[item.confidenceLevel] ?? CONFIDENCE_STYLES.LOW;

  // Reset the draft whenever the underlying answer changes (including live
  // updates pushed by another reviewer) unless we are mid-edit.
  useEffect(() => {
    if (!editing) setDraft(answer);
  }, [answer, editing]);

  useEffect(() => {
    if (editing) {
      textareaRef.current?.focus();
      textareaRef.current?.setSelectionRange(draft.length, draft.length);
    }
    // Only refocus on entering edit mode, not on every keystroke.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [editing]);

  useEffect(() => {
    if (selected) {
      cardRef.current?.scrollIntoView({ block: 'nearest', behavior: 'smooth' });
    }
  }, [selected]);

  const handleTextareaKeyDown = (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === 'Escape') {
      e.preventDefault();
      onCancelEdit();
      return;
    }
    if (e.key === 'Enter' && (e.ctrlKey || e.metaKey)) {
      e.preventDefault();
      onSaveEdit(draft, e.shiftKey);
    }
  };

  return (
    <div
      ref={cardRef}
      onClick={onSelect}
      className={cn(
        'rounded-xl border bg-white shadow-sm transition-all',
        selected ? 'border-primary-500 ring-2 ring-primary-100' : 'border-gray-200 hover:border-gray-300',
        item.overdue && !selected && 'border-l-4 border-l-red-500'
      )}
    >
      <div className="flex items-start gap-3 p-4">
        <input
          type="checkbox"
          checked={checked}
          onChange={onToggleCheck}
          onClick={(e) => e.stopPropagation()}
          className="mt-1 h-4 w-4 rounded border-gray-300 text-primary-600 focus:ring-primary-500"
          aria-label={`Select question ${item.questionNumber}`}
        />

        <div className="min-w-0 flex-1">
          {/* Header row */}
          <div className="flex flex-wrap items-center gap-2 mb-1.5">
            <span className="font-mono text-xs text-gray-500">{item.questionNumber}</span>
            <Badge className={confidence.badge}>
              {item.confidenceScore ?? 0}% · {confidence.label}
            </Badge>
            {item.overdue && (
              <Badge className="bg-red-100 text-red-700">
                <AlertTriangle className="mr-1 h-3 w-3" />
                Overdue
              </Badge>
            )}
            {item.stalenessDetected && (
              <Badge className="bg-orange-100 text-orange-700">Stale source</Badge>
            )}
            {lockedBy && (
              <Badge className="bg-gray-100 text-gray-600">
                <Lock className="mr-1 h-3 w-3" />
                {lockedBy} editing
              </Badge>
            )}
          </div>

          <p className="font-medium text-gray-900">{item.questionText}</p>

          <div className="mt-1 flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-gray-500">
            <span className="inline-flex items-center gap-1">
              <FileText className="h-3 w-3" />
              {item.questionnaireTitle}
            </span>
            {item.category && <span>· {item.category}</span>}
            {item.sme && <span>· Assigned to {item.sme.fullName}</span>}
            {item.dueAt && (
              <span className={cn('inline-flex items-center gap-1', item.overdue && 'text-red-600')}>
                <Clock className="h-3 w-3" />
                Due {formatRelativeTime(item.dueAt)}
              </span>
            )}
          </div>

          {/* Answer */}
          <div className="mt-3">
            {editing ? (
              <div className="space-y-2" onClick={(e) => e.stopPropagation()}>
                <Textarea
                  ref={textareaRef}
                  value={draft}
                  onChange={(e) => setDraft(e.target.value)}
                  onKeyDown={handleTextareaKeyDown}
                  rows={6}
                  className="font-normal"
                  aria-label="Edit answer"
                />
                <div className="flex flex-wrap items-center gap-2">
                  <Button size="sm" onClick={() => onSaveEdit(draft, false)} disabled={busy}>
                    {busy ? <Loader2 className="mr-1 h-4 w-4 animate-spin" /> : <Save className="mr-1 h-4 w-4" />}
                    Save
                  </Button>
                  <Button
                    size="sm"
                    variant="secondary"
                    onClick={() => onSaveEdit(draft, true)}
                    disabled={busy}
                  >
                    <CheckCircle className="mr-1 h-4 w-4" />
                    Save &amp; approve
                  </Button>
                  <Button size="sm" variant="ghost" onClick={onCancelEdit} disabled={busy}>
                    <X className="mr-1 h-4 w-4" />
                    Cancel
                  </Button>
                  <span className="ml-auto text-xs text-gray-400">
                    Ctrl+Enter to save · Shift+Ctrl+Enter to save &amp; approve · Esc to cancel
                  </span>
                </div>
              </div>
            ) : (
              <div className="rounded-lg bg-gray-50 p-3 text-sm text-gray-700 whitespace-pre-wrap">
                {answer || <span className="italic text-gray-400">No answer drafted yet.</span>}
              </div>
            )}
          </div>

          {/* Choice questions get one-click option buttons */}
          {!editing && item.options.length > 0 && (
            <div className="mt-2 flex flex-wrap gap-2" onClick={(e) => e.stopPropagation()}>
              {item.options.map((option) => (
                <Button
                  key={option}
                  size="sm"
                  variant={item.finalAnswer === option ? 'primary' : 'outline'}
                  onClick={() => onSelectOption(option)}
                  disabled={busy}
                >
                  {option}
                </Button>
              ))}
            </div>
          )}

          {/* Citations */}
          {item.citations.length > 0 && (
            <div className="mt-3" onClick={(e) => e.stopPropagation()}>
              <button
                type="button"
                onClick={() => setCitationsOpen((v) => !v)}
                className="inline-flex items-center gap-1 text-xs font-medium text-primary-600 hover:underline"
              >
                {citationsOpen ? <ChevronDown className="h-3 w-3" /> : <ChevronRight className="h-3 w-3" />}
                {item.citations.length} citation{item.citations.length === 1 ? '' : 's'}
              </button>

              {citationsOpen && (
                <ul className="mt-2 space-y-2">
                  {item.citations.map((citation) => (
                    <li key={citation.id} className="rounded-lg border border-gray-200 bg-white p-2.5 text-xs">
                      <div className="flex items-center justify-between gap-2">
                        <span className="font-medium text-gray-800">{citation.documentTitle}</span>
                        <span className="shrink-0 text-gray-400">
                          {Math.round((citation.relevanceScore ?? 0) * 100)}% match
                        </span>
                      </div>
                      {citation.sectionTitle && (
                        <p className="mt-0.5 text-gray-500">
                          {citation.sectionTitle}
                          {citation.pageNumber != null && ` · p.${citation.pageNumber}`}
                        </p>
                      )}
                      {citation.snippet && (
                        <p className="mt-1 border-l-2 border-gray-200 pl-2 text-gray-600">{citation.snippet}</p>
                      )}
                    </li>
                  ))}
                </ul>
              )}
            </div>
          )}
        </div>

        {/* Actions */}
        {!editing && (
          <div className="flex shrink-0 flex-col gap-1.5" onClick={(e) => e.stopPropagation()}>
            <Button
              size="sm"
              onClick={onApprove}
              disabled={busy || !!lockedBy}
              title="Approve (A)"
            >
              <CheckCircle className="h-4 w-4" />
            </Button>
            <Button
              size="sm"
              variant="outline"
              onClick={onStartEdit}
              disabled={busy || !!lockedBy}
              title={lockedBy ? `${lockedBy} is editing` : 'Edit (E)'}
            >
              <Pencil className="h-4 w-4" />
            </Button>
            <Button
              size="sm"
              variant="outline"
              onClick={onReject}
              disabled={busy || !!lockedBy}
              className="text-red-600 hover:bg-red-50"
              title="Reject (R)"
            >
              <XCircle className="h-4 w-4" />
            </Button>
          </div>
        )}
      </div>

      {/* Confidence bar */}
      <div className="h-1 w-full overflow-hidden rounded-b-xl bg-gray-100">
        <div
          className={cn('h-full transition-all', confidence.bar)}
          style={{ width: `${item.confidenceScore ?? 0}%` }}
        />
      </div>
    </div>
  );
}

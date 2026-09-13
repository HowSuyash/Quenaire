'use client';

import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  RefreshCw, Loader2, Search, AlertTriangle, CheckCircle, Clock,
  Keyboard, Users, Inbox, XCircle,
} from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Select } from '@/components/ui/Select';
import { Card, CardContent } from '@/components/ui/Card';
import { ReviewQueueCard } from '@/components/review/ReviewQueueCard';
import { PresenceBar } from '@/components/review/PresenceBar';
import { ShortcutsHelp } from '@/components/review/ShortcutsHelp';
import { useCollaboration } from '@/hooks/useCollaboration';
import { ShortcutBinding, useKeyboardShortcuts } from '@/hooks/useKeyboardShortcuts';
import { useToast } from '@/hooks/useToast';
import { api } from '@/lib/api';
import { ReviewQueueItem, ReviewQueueStats } from '@/types/api';
import { cn } from '@/lib/utils';

const EMPTY_STATS: ReviewQueueStats = {
  total: 0, pending: 0, inProgress: 0, approved: 0,
  rejected: 0, overdue: 0, highConfidence: 0, activeSMEs: 0,
};

const STATUS_OPTIONS = [
  { value: '', label: 'All statuses' },
  { value: 'PENDING', label: 'Pending' },
  { value: 'IN_PROGRESS', label: 'In progress' },
  { value: 'AI_DRAFTED', label: 'AI drafted' },
  { value: 'NEEDS_REVIEW', label: 'Needs review' },
  { value: 'HUMAN_EDITED', label: 'Human edited' },
  { value: 'DELEGATED', label: 'Delegated' },
];

const CONFIDENCE_OPTIONS = [
  { value: '', label: 'All confidence' },
  { value: 'HIGH', label: 'High (90+)' },
  { value: 'MEDIUM', label: 'Medium (70-89)' },
  { value: 'LOW', label: 'Low (<70)' },
];

export default function ReviewQueuePage() {
  const { toast } = useToast();

  const [items, setItems] = useState<ReviewQueueItem[]>([]);
  const [stats, setStats] = useState<ReviewQueueStats>(EMPTY_STATS);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [busyIds, setBusyIds] = useState<Set<string>>(new Set());

  const [search, setSearch] = useState('');
  const [statusFilter, setStatusFilter] = useState('');
  const [confidenceFilter, setConfidenceFilter] = useState('');
  const [overdueOnly, setOverdueOnly] = useState(false);
  const [mineOnly, setMineOnly] = useState(false);

  const [selectedIndex, setSelectedIndex] = useState(0);
  const [checkedIds, setCheckedIds] = useState<Set<string>>(new Set());
  const [editingId, setEditingId] = useState<string | null>(null);
  const [helpOpen, setHelpOpen] = useState(false);

  const searchRef = useRef<HTMLInputElement>(null);

  const selectedItem = items[selectedIndex];

  // ---- Data loading -----------------------------------------------------

  const load = useCallback(async (opts: { silent?: boolean } = {}) => {
    if (opts.silent) setRefreshing(true);
    else setLoading(true);

    try {
      const response = await api.getReviewQueue({
        search: search || undefined,
        status: statusFilter || undefined,
        confidence: confidenceFilter || undefined,
        overdueOnly: overdueOnly || undefined,
        mine: mineOnly || undefined,
      });
      setItems(response.items);
      setStats(response.stats);
      setSelectedIndex((prev) => Math.min(prev, Math.max(response.items.length - 1, 0)));
    } catch {
      toast({ title: 'Could not load the review queue', variant: 'destructive' });
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, [search, statusFilter, confidenceFilter, overdueOnly, mineOnly, toast]);

  // Debounce so typing in the search box does not fire a request per keystroke.
  useEffect(() => {
    const timer = setTimeout(() => load(), 250);
    return () => clearTimeout(timer);
  }, [load]);

  // ---- Real-time collaboration ------------------------------------------

  const handleRemoteUpdate = useCallback((item: ReviewQueueItem, actorName: string) => {
    setItems((prev) => {
      const idx = prev.findIndex((i) => i.questionId === item.questionId);
      // A decided question leaves the queue for everyone.
      if (item.questionStatus === 'APPROVED' || item.questionStatus === 'REJECTED') {
        return idx === -1 ? prev : prev.filter((i) => i.questionId !== item.questionId);
      }
      if (idx === -1) return prev;
      const next = [...prev];
      next[idx] = item;
      return next;
    });

    toast({
      title: `${actorName} updated ${item.questionNumber}`,
      description: item.questionStatus.replace(/_/g, ' ').toLowerCase(),
    });
  }, [toast]);

  const { peers, locks, connected, reportFocus, lock, unlock } = useCollaboration({
    onQuestionUpdated: handleRemoteUpdate,
  });

  // Tell peers which row we are on so their cursors line up with ours.
  useEffect(() => {
    reportFocus(selectedItem?.questionId ?? null, editingId !== null);
  }, [selectedItem?.questionId, editingId, reportFocus]);

  // ---- Mutations ---------------------------------------------------------

  const withBusy = useCallback(async (questionId: string, fn: () => Promise<void>) => {
    setBusyIds((prev) => new Set(prev).add(questionId));
    try {
      await fn();
    } finally {
      setBusyIds((prev) => {
        const next = new Set(prev);
        next.delete(questionId);
        return next;
      });
    }
  }, []);

  /** Drop a decided row locally and keep the selection on a sensible neighbour. */
  const removeItem = useCallback((questionId: string) => {
    setItems((prev) => {
      const idx = prev.findIndex((i) => i.questionId === questionId);
      if (idx === -1) return prev;
      const next = prev.filter((i) => i.questionId !== questionId);
      setSelectedIndex((current) => {
        if (idx < current) return Math.max(current - 1, 0);
        return Math.min(current, Math.max(next.length - 1, 0));
      });
      return next;
    });
    setCheckedIds((prev) => {
      const next = new Set(prev);
      next.delete(questionId);
      return next;
    });
  }, []);

  const approve = useCallback((item: ReviewQueueItem) => withBusy(item.questionId, async () => {
    const result = await api.approveQueueItem(item.questionId);
    if (result?.success === false) {
      toast({ title: 'Approve failed', description: result.error, variant: 'destructive' });
      return;
    }
    removeItem(item.questionId);
    setStats((s) => ({ ...s, approved: s.approved + 1, total: Math.max(s.total - 1, 0) }));
    toast({ title: `Approved ${item.questionNumber}`, variant: 'success' });
  }), [withBusy, removeItem, toast]);

  const reject = useCallback((item: ReviewQueueItem) => {
    const reason = window.prompt(`Why is ${item.questionNumber} being rejected?`);
    if (reason === null) return;

    return withBusy(item.questionId, async () => {
      const result = await api.rejectQueueItem(item.questionId, reason);
      if (result?.success === false) {
        toast({ title: 'Reject failed', description: result.error, variant: 'destructive' });
        return;
      }
      removeItem(item.questionId);
      setStats((s) => ({ ...s, rejected: s.rejected + 1, total: Math.max(s.total - 1, 0) }));
      toast({ title: `Rejected ${item.questionNumber}` });
    });
  }, [withBusy, removeItem, toast]);

  const startEdit = useCallback(async (item: ReviewQueueItem) => {
    const heldBy = await lock(item.questionId);
    if (heldBy) {
      toast({ title: `${heldBy} is editing this answer`, variant: 'destructive' });
      return;
    }
    setEditingId(item.questionId);
  }, [lock, toast]);

  const cancelEdit = useCallback(async (questionId: string) => {
    setEditingId(null);
    await unlock(questionId);
  }, [unlock]);

  const saveEdit = useCallback((item: ReviewQueueItem, answer: string, alsoApprove: boolean) =>
    withBusy(item.questionId, async () => {
      const result = alsoApprove
        ? await api.editAndApproveQueueItem(item.questionId, answer)
        : await api.saveQueueItemAnswer(item.questionId, answer);

      if (result?.success === false) {
        toast({ title: 'Save failed', description: result.error, variant: 'destructive' });
        return;
      }

      setEditingId(null);
      await unlock(item.questionId);

      if (alsoApprove) {
        removeItem(item.questionId);
        setStats((s) => ({ ...s, approved: s.approved + 1, total: Math.max(s.total - 1, 0) }));
        toast({ title: `Saved and approved ${item.questionNumber}`, variant: 'success' });
      } else {
        setItems((prev) => prev.map((i) => (
          i.questionId === item.questionId
            ? { ...i, finalAnswer: answer, questionStatus: 'HUMAN_EDITED' }
            : i
        )));
        toast({ title: `Saved ${item.questionNumber}` });
      }
    }), [withBusy, unlock, removeItem, toast]);

  const selectOption = useCallback((item: ReviewQueueItem, option: string) =>
    withBusy(item.questionId, async () => {
      const result = await api.selectQueueItemOption(item.questionId, option);
      if (result?.success === false) {
        toast({ title: 'Could not select option', description: result.error, variant: 'destructive' });
        return;
      }
      setItems((prev) => prev.map((i) => (
        i.questionId === item.questionId ? { ...i, finalAnswer: option } : i
      )));
    }), [withBusy, toast]);

  const bulkApprove = useCallback(async () => {
    const ids = Array.from(checkedIds);
    if (ids.length === 0) return;

    const result = await api.bulkApproveQueueItems(ids);
    ids.forEach(removeItem);
    setCheckedIds(new Set());
    setStats((s) => ({
      ...s,
      approved: s.approved + (result?.approvedCount ?? ids.length),
      total: Math.max(s.total - ids.length, 0),
    }));
    toast({
      title: `Approved ${result?.approvedCount ?? ids.length} question${ids.length === 1 ? '' : 's'}`,
      description: result?.failedCount ? `${result.failedCount} failed` : undefined,
      variant: result?.failedCount ? 'destructive' : 'success',
    });
  }, [checkedIds, removeItem, toast]);

  const toggleCheck = useCallback((questionId: string) => {
    setCheckedIds((prev) => {
      const next = new Set(prev);
      if (next.has(questionId)) next.delete(questionId);
      else next.add(questionId);
      return next;
    });
  }, []);

  // ---- Keyboard shortcuts ------------------------------------------------

  const bindings = useMemo<ShortcutBinding[]>(() => [
    {
      key: 'j', group: 'Navigation', description: 'Next question',
      handler: () => setSelectedIndex((i) => Math.min(i + 1, Math.max(items.length - 1, 0))),
    },
    {
      key: 'k', group: 'Navigation', description: 'Previous question',
      handler: () => setSelectedIndex((i) => Math.max(i - 1, 0)),
    },
    {
      key: 'arrowdown', group: 'Navigation', description: 'Next question',
      handler: () => setSelectedIndex((i) => Math.min(i + 1, Math.max(items.length - 1, 0))),
    },
    {
      key: 'arrowup', group: 'Navigation', description: 'Previous question',
      handler: () => setSelectedIndex((i) => Math.max(i - 1, 0)),
    },
    {
      key: '/', group: 'Navigation', description: 'Focus search',
      handler: () => searchRef.current?.focus(),
    },
    {
      key: 'a', group: 'Review', description: 'Approve selected',
      handler: () => { if (selectedItem && !editingId) approve(selectedItem); },
    },
    {
      key: 'r', group: 'Review', description: 'Reject selected',
      handler: () => { if (selectedItem && !editingId) reject(selectedItem); },
    },
    {
      key: 'e', group: 'Review', description: 'Edit selected answer',
      handler: () => { if (selectedItem && !editingId) startEdit(selectedItem); },
    },
    {
      key: 'x', group: 'Review', description: 'Toggle selection checkbox',
      handler: () => { if (selectedItem) toggleCheck(selectedItem.questionId); },
    },
    {
      key: 'a', mod: true, shift: true, group: 'Review', description: 'Approve all checked',
      handler: () => bulkApprove(),
    },
    {
      key: 'escape', group: 'Review', description: 'Cancel edit / clear selection',
      allowInInput: true,
      handler: () => {
        if (editingId) cancelEdit(editingId);
        else if (checkedIds.size > 0) setCheckedIds(new Set());
        else searchRef.current?.blur();
      },
    },
    {
      key: '?', shift: true, group: 'General', description: 'Show this help',
      handler: () => setHelpOpen((v) => !v),
    },
    {
      key: 'g', group: 'General', description: 'Refresh queue',
      handler: () => load({ silent: true }),
    },
  ], [items.length, selectedItem, editingId, checkedIds.size, approve, reject, startEdit, toggleCheck, bulkApprove, cancelEdit, load]);

  useKeyboardShortcuts(bindings, !helpOpen);

  // Close the help overlay with Escape even though it swallows other keys.
  useEffect(() => {
    if (!helpOpen) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setHelpOpen(false);
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [helpOpen]);

  // ---- Render ------------------------------------------------------------

  const statTiles = [
    { label: 'In queue', value: stats.total, icon: Inbox, tone: 'text-gray-600' },
    { label: 'Pending', value: stats.pending, icon: Clock, tone: 'text-yellow-600' },
    { label: 'Overdue', value: stats.overdue, icon: AlertTriangle, tone: 'text-red-600' },
    { label: 'High confidence', value: stats.highConfidence, icon: CheckCircle, tone: 'text-green-600' },
    { label: 'Approved', value: stats.approved, icon: CheckCircle, tone: 'text-green-600' },
    { label: 'Rejected', value: stats.rejected, icon: XCircle, tone: 'text-gray-500' },
    { label: 'Active SMEs', value: stats.activeSMEs, icon: Users, tone: 'text-primary-600' },
  ];

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">SME Review Queue</h1>
          <p className="text-sm text-gray-500">
            Every answer still waiting on a human decision, hardest first.
          </p>
        </div>

        <div className="flex items-center gap-3">
          <PresenceBar peers={peers} connected={connected} />
          <Button variant="outline" size="sm" onClick={() => setHelpOpen(true)}>
            <Keyboard className="mr-1.5 h-4 w-4" />
            Shortcuts
          </Button>
          <Button variant="outline" size="sm" onClick={() => load({ silent: true })} disabled={refreshing}>
            <RefreshCw className={cn('mr-1.5 h-4 w-4', refreshing && 'animate-spin')} />
            Refresh
          </Button>
        </div>
      </div>

      {/* Stats */}
      <div className="grid grid-cols-2 gap-3 sm:grid-cols-4 lg:grid-cols-7">
        {statTiles.map(({ label, value, icon: Icon, tone }) => (
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

      {/* Filters */}
      <Card>
        <CardContent className="flex flex-wrap items-end gap-3 p-4">
          <div className="min-w-[240px] flex-1">
            <Input
              ref={searchRef}
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="Search questions, answers or questionnaires…"
              leftIcon={<Search className="h-4 w-4" />}
              aria-label="Search the review queue"
            />
          </div>

          <div className="w-44">
            <Select
              value={statusFilter}
              onChange={(e) => setStatusFilter(e.target.value)}
              options={STATUS_OPTIONS}
              aria-label="Filter by status"
            />
          </div>

          <div className="w-44">
            <Select
              value={confidenceFilter}
              onChange={(e) => setConfidenceFilter(e.target.value)}
              options={CONFIDENCE_OPTIONS}
              aria-label="Filter by confidence"
            />
          </div>

          <label className="flex h-10 items-center gap-2 text-sm text-gray-700">
            <input
              type="checkbox"
              checked={overdueOnly}
              onChange={(e) => setOverdueOnly(e.target.checked)}
              className="h-4 w-4 rounded border-gray-300 text-primary-600 focus:ring-primary-500"
            />
            Overdue only
          </label>

          <label className="flex h-10 items-center gap-2 text-sm text-gray-700">
            <input
              type="checkbox"
              checked={mineOnly}
              onChange={(e) => setMineOnly(e.target.checked)}
              className="h-4 w-4 rounded border-gray-300 text-primary-600 focus:ring-primary-500"
            />
            Assigned to me
          </label>
        </CardContent>
      </Card>

      {/* Bulk action bar */}
      {checkedIds.size > 0 && (
        <div className="sticky top-2 z-10 flex items-center justify-between rounded-xl border border-primary-200 bg-primary-50 px-4 py-3 shadow-sm">
          <span className="text-sm font-medium text-primary-900">
            {checkedIds.size} question{checkedIds.size === 1 ? '' : 's'} selected
          </span>
          <div className="flex items-center gap-2">
            <Button size="sm" onClick={bulkApprove}>
              <CheckCircle className="mr-1.5 h-4 w-4" />
              Approve selected
            </Button>
            <Button size="sm" variant="ghost" onClick={() => setCheckedIds(new Set())}>
              Clear
            </Button>
          </div>
        </div>
      )}

      {/* Queue */}
      {loading ? (
        <div className="flex items-center justify-center py-20 text-gray-600">
          <Loader2 className="h-8 w-8 animate-spin" />
        </div>
      ) : items.length === 0 ? (
        <Card>
          <CardContent className="py-16 text-center">
            <CheckCircle className="mx-auto mb-3 h-10 w-10 text-green-500" />
            <h2 className="text-lg font-semibold text-gray-900">Queue is clear</h2>
            <p className="mt-1 text-sm text-gray-500">
              Nothing is waiting on a human right now. New low-confidence answers will appear here
              automatically.
            </p>
          </CardContent>
        </Card>
      ) : (
        <div className="space-y-3">
          {items.map((item, index) => (
            <ReviewQueueCard
              key={item.questionId}
              item={item}
              selected={index === selectedIndex}
              checked={checkedIds.has(item.questionId)}
              editing={editingId === item.questionId}
              lockedBy={editingId === item.questionId ? undefined : locks[item.questionId]}
              busy={busyIds.has(item.questionId)}
              onSelect={() => setSelectedIndex(index)}
              onToggleCheck={() => toggleCheck(item.questionId)}
              onStartEdit={() => startEdit(item)}
              onCancelEdit={() => cancelEdit(item.questionId)}
              onSaveEdit={(answer, alsoApprove) => saveEdit(item, answer, alsoApprove)}
              onApprove={() => approve(item)}
              onReject={() => reject(item)}
              onSelectOption={(option) => selectOption(item, option)}
            />
          ))}
        </div>
      )}

      <ShortcutsHelp bindings={bindings} open={helpOpen} onClose={() => setHelpOpen(false)} />
    </div>
  );
}

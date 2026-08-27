'use client';

import { useCallback, useEffect, useRef, useState } from 'react';
import { api } from '@/lib/api';
import { PresenceUser, ReviewQueueItem } from '@/types/api';

interface UseCollaborationOptions {
  /** Called when another reviewer changes a row we may be displaying. */
  onQuestionUpdated?: (item: ReviewQueueItem, actorName: string) => void;
  enabled?: boolean;
}

interface UseCollaborationResult {
  /** Everyone currently viewing the queue, excluding nobody — filter yourself in the UI. */
  peers: PresenceUser[];
  /** questionId → display name of whoever is editing it. */
  locks: Record<string, string>;
  connected: boolean;
  /** Report which question this client is looking at / editing. */
  reportFocus: (questionId: string | null, editing: boolean) => void;
  /** Take the edit lock. Resolves to the holder's name if someone else has it. */
  lock: (questionId: string) => Promise<string | null>;
  unlock: (questionId: string) => Promise<void>;
}

const PRESENCE_HEARTBEAT_MS = 20_000;

/**
 * Subscribes to the tenant's review-queue event stream.
 *
 * The stream is best-effort: when the backend is unavailable (or the browser
 * blocks EventSource) the hook degrades to an empty roster and the queue still
 * works, just without live presence.
 */
export function useCollaboration(options: UseCollaborationOptions = {}): UseCollaborationResult {
  const { onQuestionUpdated, enabled = true } = options;

  const [peers, setPeers] = useState<PresenceUser[]>([]);
  const [locks, setLocks] = useState<Record<string, string>>({});
  const [connected, setConnected] = useState(false);

  const sourceRef = useRef<EventSource | null>(null);
  const focusRef = useRef<{ questionId: string | null; editing: boolean }>({
    questionId: null,
    editing: false,
  });

  // Keep the latest callback without re-opening the stream on every render.
  const onUpdatedRef = useRef(onQuestionUpdated);
  useEffect(() => {
    onUpdatedRef.current = onQuestionUpdated;
  }, [onQuestionUpdated]);

  useEffect(() => {
    if (!enabled || typeof window === 'undefined' || typeof EventSource === 'undefined') {
      return;
    }

    let source: EventSource;
    try {
      source = new EventSource(api.collaborationStreamUrl(), { withCredentials: true });
    } catch {
      return;
    }
    sourceRef.current = source;

    source.addEventListener('open', () => setConnected(true));

    source.addEventListener('PRESENCE', (event) => {
      try {
        const data = JSON.parse((event as MessageEvent).data);
        setPeers(Array.isArray(data.users) ? data.users : []);
      } catch {
        /* malformed frame — ignore rather than tear down the stream */
      }
    });

    source.addEventListener('QUESTION_UPDATED', (event) => {
      try {
        const data = JSON.parse((event as MessageEvent).data);
        if (data.item) onUpdatedRef.current?.(data.item, data.actorName ?? 'A teammate');
      } catch {
        /* ignore */
      }
    });

    source.addEventListener('QUESTION_LOCKED', (event) => {
      try {
        const data = JSON.parse((event as MessageEvent).data);
        setLocks((prev) => ({ ...prev, [data.questionId]: data.actorName }));
      } catch {
        /* ignore */
      }
    });

    source.addEventListener('QUESTION_UNLOCKED', (event) => {
      try {
        const data = JSON.parse((event as MessageEvent).data);
        setLocks((prev) => {
          const next = { ...prev };
          delete next[data.questionId];
          return next;
        });
      } catch {
        /* ignore */
      }
    });

    source.addEventListener('error', () => {
      // EventSource reconnects on its own; just reflect the gap in the UI.
      setConnected(false);
    });

    return () => {
      source.close();
      sourceRef.current = null;
      setConnected(false);
    };
  }, [enabled]);

  // Heartbeat so the server can expire us if this tab goes away.
  useEffect(() => {
    if (!enabled) return;

    const tick = () => {
      api.updatePresence(focusRef.current.questionId, focusRef.current.editing);
    };

    tick();
    const interval = setInterval(tick, PRESENCE_HEARTBEAT_MS);
    return () => clearInterval(interval);
  }, [enabled]);

  const reportFocus = useCallback((questionId: string | null, editing: boolean) => {
    focusRef.current = { questionId, editing };
    api.updatePresence(questionId, editing);
  }, []);

  const lock = useCallback((questionId: string) => api.acquireLock(questionId), []);

  const unlock = useCallback(async (questionId: string) => {
    await api.releaseLock(questionId);
    setLocks((prev) => {
      const next = { ...prev };
      delete next[questionId];
      return next;
    });
  }, []);

  return { peers, locks, connected, reportFocus, lock, unlock };
}

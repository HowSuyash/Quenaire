'use client';

import { useEffect, useRef } from 'react';

export interface ShortcutBinding {
  /** Lowercased `event.key`, e.g. 'j', 'enter', 'escape', '?'. */
  key: string;
  handler: (event: KeyboardEvent) => void;
  description: string;
  /** Group heading in the help overlay. */
  group?: string;
  /** Require Ctrl (or Cmd on macOS). */
  mod?: boolean;
  shift?: boolean;
  /** Fire even while a text field has focus. Off by default. */
  allowInInput?: boolean;
}

const EDITABLE = new Set(['INPUT', 'TEXTAREA', 'SELECT']);

function isEditingTarget(target: EventTarget | null): boolean {
  if (!(target instanceof HTMLElement)) return false;
  return EDITABLE.has(target.tagName) || target.isContentEditable;
}

/**
 * Global keyboard shortcuts for the review queue.
 *
 * Bindings are matched on the physical key plus modifiers. Anything typed into
 * a text field is ignored unless the binding opts in with `allowInInput`, so
 * writing "j" into an answer never jumps the selection.
 */
export function useKeyboardShortcuts(bindings: ShortcutBinding[], enabled = true) {
  // Re-reading from a ref keeps the listener stable across renders.
  const bindingsRef = useRef(bindings);
  useEffect(() => {
    bindingsRef.current = bindings;
  }, [bindings]);

  useEffect(() => {
    if (!enabled) return;

    const onKeyDown = (event: KeyboardEvent) => {
      const editing = isEditingTarget(event.target);
      const key = event.key.toLowerCase();
      const mod = event.ctrlKey || event.metaKey;

      for (const binding of bindingsRef.current) {
        if (binding.key !== key) continue;
        if (!!binding.mod !== mod) continue;
        if (!!binding.shift !== event.shiftKey) continue;
        if (editing && !binding.allowInInput) continue;

        event.preventDefault();
        binding.handler(event);
        return;
      }
    };

    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [enabled]);
}

/** Human-readable label for a binding, e.g. "Ctrl + Enter". */
export function shortcutLabel(binding: ShortcutBinding): string {
  const parts: string[] = [];
  if (binding.mod) parts.push('Ctrl');
  if (binding.shift) parts.push('Shift');

  const named: Record<string, string> = {
    enter: 'Enter',
    escape: 'Esc',
    arrowup: '↑',
    arrowdown: '↓',
    ' ': 'Space',
  };
  parts.push(named[binding.key] ?? binding.key.toUpperCase());

  return parts.join(' + ');
}

'use client';

import { X } from 'lucide-react';
import { ShortcutBinding, shortcutLabel } from '@/hooks/useKeyboardShortcuts';

interface ShortcutsHelpProps {
  bindings: ShortcutBinding[];
  open: boolean;
  onClose: () => void;
}

export function ShortcutsHelp({ bindings, open, onClose }: ShortcutsHelpProps) {
  if (!open) return null;

  const groups = bindings.reduce<Record<string, ShortcutBinding[]>>((acc, binding) => {
    const group = binding.group ?? 'General';
    (acc[group] ??= []).push(binding);
    return acc;
  }, {});

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4"
      role="dialog"
      aria-modal="true"
      aria-label="Keyboard shortcuts"
      onClick={onClose}
    >
      <div
        className="w-full max-w-lg rounded-xl bg-white shadow-xl"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="flex items-center justify-between border-b border-gray-200 px-6 py-4">
          <h2 className="text-lg font-semibold text-gray-900">Keyboard shortcuts</h2>
          <button
            type="button"
            onClick={onClose}
            className="rounded-lg p-1 text-gray-600 hover:bg-gray-100 hover:text-gray-600"
            aria-label="Close"
          >
            <X className="h-5 w-5" />
          </button>
        </div>

        <div className="max-h-[60vh] overflow-y-auto px-6 py-4 space-y-5">
          {Object.entries(groups).map(([group, items]) => (
            <div key={group}>
              <h3 className="mb-2 text-xs font-semibold uppercase tracking-wide text-gray-600">
                {group}
              </h3>
              <ul className="space-y-1.5">
                {items.map((binding) => (
                  <li
                    key={`${group}-${binding.key}-${binding.mod ? 'mod' : ''}${binding.shift ? 'shift' : ''}`}
                    className="flex items-center justify-between gap-4 text-sm"
                  >
                    <span className="text-gray-700">{binding.description}</span>
                    <kbd className="rounded border border-gray-300 bg-gray-50 px-2 py-0.5 font-mono text-xs text-gray-600">
                      {shortcutLabel(binding)}
                    </kbd>
                  </li>
                ))}
              </ul>
            </div>
          ))}
        </div>

        <div className="border-t border-gray-200 px-6 py-3 text-xs text-gray-500">
          Press <kbd className="rounded border border-gray-300 bg-gray-50 px-1.5 py-0.5 font-mono">?</kbd> any
          time to reopen this list.
        </div>
      </div>
    </div>
  );
}

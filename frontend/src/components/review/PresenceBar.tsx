'use client';

import { Wifi, WifiOff } from 'lucide-react';
import { PresenceUser } from '@/types/api';
import { cn } from '@/lib/utils';

interface PresenceBarProps {
  peers: PresenceUser[];
  connected: boolean;
  className?: string;
}

function initials(name: string): string {
  const parts = name.trim().split(/\s+/).filter(Boolean);
  if (parts.length === 0) return '?';
  if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase();
  return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase();
}

/**
 * Avatar stack of everyone currently in the review queue, with a dot showing
 * who is actively editing.
 */
export function PresenceBar({ peers, connected, className }: PresenceBarProps) {
  const visible = peers.slice(0, 5);
  const overflow = peers.length - visible.length;

  return (
    <div className={cn('flex items-center gap-3', className)}>
      <div
        className="flex items-center gap-1 text-xs text-gray-500"
        title={connected ? 'Live updates connected' : 'Live updates unavailable'}
      >
        {connected ? (
          <Wifi className="h-3.5 w-3.5 text-green-600" />
        ) : (
          <WifiOff className="h-3.5 w-3.5 text-gray-600" />
        )}
        <span>{connected ? 'Live' : 'Offline'}</span>
      </div>

      {peers.length > 0 && (
        <div className="flex items-center -space-x-2">
          {visible.map((peer) => (
            <div
              key={peer.userId}
              className="relative"
              title={`${peer.name}${peer.editing ? ' — editing' : ''}`}
            >
              <div
                className="h-7 w-7 rounded-full ring-2 ring-white flex items-center justify-center text-[10px] font-semibold text-white"
                style={{ backgroundColor: peer.color }}
              >
                {initials(peer.name)}
              </div>
              {peer.editing && (
                <span className="absolute -bottom-0.5 -right-0.5 h-2.5 w-2.5 rounded-full bg-amber-500 ring-2 ring-white" />
              )}
            </div>
          ))}
          {overflow > 0 && (
            <div className="h-7 w-7 rounded-full ring-2 ring-white bg-gray-200 flex items-center justify-center text-[10px] font-semibold text-gray-600">
              +{overflow}
            </div>
          )}
        </div>
      )}
    </div>
  );
}

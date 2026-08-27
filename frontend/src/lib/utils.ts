import { clsx, type ClassValue } from 'clsx';
import { twMerge } from 'tailwind-merge';

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs));
}

export function formatDate(date: string | Date): string {
  const d = new Date(date);
  return d.toLocaleDateString('en-US', {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
  });
}

export function formatDateTime(date: string | Date): string {
  const d = new Date(date);
  return d.toLocaleDateString('en-US', {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
}

export function formatRelativeTime(date: string | Date): string {
  const d = new Date(date);
  const now = new Date();
  const diffMs = now.getTime() - d.getTime();
  const diffMins = Math.floor(diffMs / 60000);
  const diffHours = Math.floor(diffMs / 3600000);
  const diffDays = Math.floor(diffMs / 86400000);

  if (diffMins < 1) return 'just now';
  if (diffMins < 60) return `${diffMins}m ago`;
  if (diffHours < 24) return `${diffHours}h ago`;
  if (diffDays < 7) return `${diffDays}d ago`;
  return formatDate(d);
}

export function truncate(text: string, length: number): string {
  if (text.length <= length) return text;
  return text.slice(0, length).trim() + '...';
}

export function getStatusColor(status: string): string {
  const colors: Record<string, string> = {
    DRAFT: 'bg-gray-100 text-gray-700',
    PROCESSING: 'bg-blue-100 text-blue-700',
    READY_FOR_REVIEW: 'bg-yellow-100 text-yellow-700',
    IN_REVIEW: 'bg-orange-100 text-orange-700',
    APPROVED: 'bg-green-100 text-green-700',
    SUBMITTED: 'bg-purple-100 text-purple-700',
    ARCHIVED: 'bg-gray-100 text-gray-500',
    UPLOADING: 'bg-gray-100 text-gray-700',
    INDEXED: 'bg-green-100 text-green-700',
    FAILED: 'bg-red-100 text-red-700',
    PENDING: 'bg-gray-100 text-gray-700',
    AI_DRAFTED: 'bg-blue-100 text-blue-700',
    NEEDS_REVIEW: 'bg-yellow-100 text-yellow-700',
    REJECTED: 'bg-red-100 text-red-700',
    SKIPPED: 'bg-gray-100 text-gray-500',
    AI_GENERATED: 'bg-blue-100 text-blue-700',
    HUMAN_EDITED: 'bg-indigo-100 text-indigo-700',
    ACTIVE: 'bg-green-100 text-green-700',
    SUSPENDED: 'bg-red-100 text-red-700',
    TRIAL: 'bg-yellow-100 text-yellow-700',
    EXPIRED: 'bg-gray-100 text-gray-500',
    DELETED: 'bg-gray-100 text-gray-400',
    INVITED: 'bg-yellow-100 text-yellow-700',
    DEACTIVATED: 'bg-gray-100 text-gray-500',
  };
  return colors[status] || 'bg-gray-100 text-gray-700';
}

export function getQuestionTypeIcon(type: string): string {
  const icons: Record<string, string> = {
    FREE_TEXT: 'Type',
    SINGLE_CHOICE: 'Radio',
    MULTIPLE_CHOICE: 'CheckSquare',
    YES_NO: 'HelpCircle',
    NUMERIC: 'Hash',
    DATE: 'Calendar',
    FILE_UPLOAD: 'Upload',
    TABLE: 'Table',
  };
  return icons[type] || 'HelpCircle';
}
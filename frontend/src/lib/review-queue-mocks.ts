/**
 * Offline fixtures for the SME review queue.
 *
 * The API client falls back to these when the backend is unreachable, so the
 * dashboard stays demoable without Postgres/Ollama running — same contract the
 * rest of `api.ts` already uses for questionnaires and documents.
 */
import { ReviewQueueItem, ReviewQueueResponse, ReviewQueueStats, SMEProfile } from '@/types/api';

export const MOCK_SMES: SMEProfile[] = [
  {
    userId: 'usr-2',
    email: 'sarah.miller@acmesecurity.com',
    fullName: 'Sarah Miller',
    expertiseTags: ['encryption', 'cryptography', 'key management'],
    slackId: 'U02SARAH',
    maxConcurrentReviews: 10,
    isActive: true,
    activeReviewCount: 3,
  },
  {
    userId: 'usr-3',
    email: 'priya.raman@acmesecurity.com',
    fullName: 'Priya Raman',
    expertiseTags: ['business continuity', 'disaster recovery', 'availability'],
    slackId: 'U03PRIYA',
    maxConcurrentReviews: 8,
    isActive: true,
    activeReviewCount: 1,
  },
  {
    userId: 'usr-4',
    email: 'diego.santos@acmesecurity.com',
    fullName: 'Diego Santos',
    expertiseTags: ['vulnerability management', 'pen testing', 'appsec'],
    slackId: 'U04DIEGO',
    maxConcurrentReviews: 6,
    isActive: true,
    activeReviewCount: 2,
  },
];

const hoursFromNow = (h: number) => new Date(Date.now() + h * 3600_000).toISOString();

export const MOCK_QUEUE_ITEMS: ReviewQueueItem[] = [
  {
    questionId: 'qst-303',
    questionnaireId: 'q-101',
    questionnaireTitle: 'SOC 2 Type II Security Assessment 2026',
    questionNumber: 'SEC-2.1',
    questionText: 'What are your Recovery Time Objectives (RTO) and Recovery Point Objectives (RPO)?',
    category: 'Business Continuity',
    section: 'Business Continuity & Disaster Recovery',
    questionType: 'FREE_TEXT',
    options: [],
    aiAnswer:
      'Our SLA guarantees a Recovery Point Objective (RPO) of 15 minutes or less through continuous multi-region transactional database replication and point-in-time snapshots, and a Recovery Time Objective (RTO) of 1 hour or less with automated multi-region failover tested semi-annually.',
    confidenceScore: 92,
    confidenceLevel: 'HIGH',
    questionStatus: 'AI_DRAFTED',
    reviewStatus: 'PENDING',
    assignedAt: hoursFromNow(-6),
    dueAt: hoursFromNow(18),
    overdue: false,
    sme: MOCK_SMES[1],
    citations: [
      {
        id: 'cit-01',
        documentId: 'doc-203',
        documentTitle: 'Business Continuity & Disaster Recovery Plan',
        snippet: 'Target RPO: 15 minutes. Target RTO: 60 minutes for tier-1 production services.',
        relevanceScore: 0.95,
        pageNumber: 4,
        sectionTitle: 'Recovery Objectives',
      },
    ],
    stalenessDetected: false,
    updatedAt: hoursFromNow(-2),
  },
  {
    questionId: 'qst-304',
    questionnaireId: 'q-101',
    questionnaireTitle: 'SOC 2 Type II Security Assessment 2026',
    questionNumber: 'SEC-3.1',
    questionText:
      'How frequently are independent third-party penetration tests conducted, and can you provide executive summaries?',
    category: 'Vulnerability Management',
    section: 'Vulnerability Management & Pen Testing',
    questionType: 'FREE_TEXT',
    options: [],
    aiAnswer:
      'Independent third-party penetration tests are performed annually against production infrastructure and the customer-facing application, with targeted retests after significant architectural changes. Executive summaries are available under NDA.',
    confidenceScore: 74,
    confidenceLevel: 'MEDIUM',
    questionStatus: 'NEEDS_REVIEW',
    reviewStatus: 'IN_PROGRESS',
    assignedAt: hoursFromNow(-30),
    dueAt: hoursFromNow(-6),
    overdue: true,
    sme: MOCK_SMES[2],
    citations: [
      {
        id: 'cit-02',
        documentId: 'doc-202',
        documentTitle: 'SOC 2 Type II Audit Report 2025-2026',
        snippet: 'Annual penetration testing performed by an accredited third party; findings tracked to closure.',
        relevanceScore: 0.86,
        pageNumber: 42,
        sectionTitle: 'Control Testing',
      },
    ],
    stalenessDetected: true,
    updatedAt: hoursFromNow(-30),
  },
  {
    questionId: 'qst-305',
    questionnaireId: 'q-102',
    questionnaireTitle: 'Vendor Risk Assessment - Cloud Infrastructure (AWS)',
    questionNumber: 'CLD-4.2',
    questionText:
      'Do you maintain a documented sub-processor list and notify customers before onboarding new sub-processors?',
    category: 'Vendor Risk',
    section: 'Third-Party Management',
    questionType: 'YES_NO',
    options: ['Yes', 'No', 'Partial'],
    aiAnswer:
      'Yes. A current sub-processor roster is published and customers receive 30 days advance notice before a new sub-processor begins processing customer data.',
    confidenceScore: 58,
    confidenceLevel: 'LOW',
    questionStatus: 'NEEDS_REVIEW',
    reviewStatus: 'PENDING',
    assignedAt: hoursFromNow(-2),
    dueAt: hoursFromNow(22),
    overdue: false,
    sme: MOCK_SMES[0],
    citations: [],
    stalenessDetected: false,
    updatedAt: hoursFromNow(-2),
  },
  {
    questionId: 'qst-306',
    questionnaireId: 'q-105',
    questionnaireTitle: 'Financial Services Third-Party Risk (SIG Core)',
    questionNumber: 'SIG-7.4',
    questionText:
      'Describe your secure software development lifecycle, including code review and dependency scanning controls.',
    category: 'Application Security',
    section: 'Secure Development',
    questionType: 'FREE_TEXT',
    options: [],
    aiAnswer:
      'All changes require peer code review and pass automated SAST, dependency and secret scanning in CI before merge. Production deploys require a second approver, and dependency advisories are triaged against an SLA tied to severity.',
    confidenceScore: 81,
    confidenceLevel: 'MEDIUM',
    questionStatus: 'AI_DRAFTED',
    reviewStatus: 'PENDING',
    assignedAt: hoursFromNow(-9),
    dueAt: hoursFromNow(15),
    overdue: false,
    sme: MOCK_SMES[2],
    citations: [
      {
        id: 'cit-03',
        documentId: 'doc-201',
        documentTitle: 'Information Security Policy v4.2',
        snippet: 'Section 6.1: No change reaches production without peer review and a passing security pipeline.',
        relevanceScore: 0.9,
        pageNumber: 18,
        sectionTitle: 'Secure Development',
      },
    ],
    stalenessDetected: false,
    updatedAt: hoursFromNow(-9),
  },
];

export function mutateMockItem(
  questionId: string,
  patch: Partial<ReviewQueueItem>
): ReviewQueueItem | undefined {
  const idx = MOCK_QUEUE_ITEMS.findIndex((i) => i.questionId === questionId);
  if (idx === -1) return undefined;
  MOCK_QUEUE_ITEMS[idx] = { ...MOCK_QUEUE_ITEMS[idx], ...patch, updatedAt: new Date().toISOString() };
  return MOCK_QUEUE_ITEMS[idx];
}

export function buildMockQueue(params?: {
  questionnaireId?: string;
  status?: string;
  confidence?: string;
  search?: string;
  overdueOnly?: boolean;
}): ReviewQueueResponse {
  // Approved/rejected items leave the queue, matching the server-side read model.
  let items = MOCK_QUEUE_ITEMS.filter(
    (i) => i.questionStatus !== 'APPROVED' && i.questionStatus !== 'REJECTED'
  );

  if (params?.questionnaireId) items = items.filter((i) => i.questionnaireId === params.questionnaireId);
  if (params?.confidence) items = items.filter((i) => i.confidenceLevel === params.confidence);
  if (params?.overdueOnly) items = items.filter((i) => i.overdue);
  if (params?.status) {
    items = items.filter((i) => i.questionStatus === params.status || i.reviewStatus === params.status);
  }
  if (params?.search) {
    const q = params.search.toLowerCase();
    items = items.filter(
      (i) =>
        i.questionText.toLowerCase().includes(q) ||
        i.questionNumber.toLowerCase().includes(q) ||
        i.questionnaireTitle.toLowerCase().includes(q) ||
        (i.aiAnswer ?? '').toLowerCase().includes(q)
    );
  }

  items = [...items].sort((a, b) => {
    if (a.overdue !== b.overdue) return a.overdue ? -1 : 1;
    return new Date(a.dueAt ?? 0).getTime() - new Date(b.dueAt ?? 0).getTime();
  });

  const stats: ReviewQueueStats = {
    total: items.length,
    pending: items.filter((i) => i.reviewStatus === 'PENDING').length,
    inProgress: items.filter((i) => i.reviewStatus === 'IN_PROGRESS').length,
    approved: MOCK_QUEUE_ITEMS.filter((i) => i.questionStatus === 'APPROVED').length,
    rejected: MOCK_QUEUE_ITEMS.filter((i) => i.questionStatus === 'REJECTED').length,
    overdue: items.filter((i) => i.overdue).length,
    highConfidence: items.filter((i) => i.confidenceLevel === 'HIGH').length,
    activeSMEs: new Set(items.map((i) => i.sme?.userId).filter(Boolean)).size,
  };

  return { items, stats };
}

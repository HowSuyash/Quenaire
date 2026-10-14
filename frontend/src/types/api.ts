export interface Tenant {
  id: string;
  name: string;
  slug: string;
  customDomain?: string;
  status: 'ACTIVE' | 'SUSPENDED' | 'TRIAL' | 'EXPIRED' | 'DELETED';
  plan: 'FREE' | 'STARTER' | 'PROFESSIONAL' | 'ENTERPRISE' | 'CUSTOM';
  planExpiresAt?: string;
  settingsJson: Record<string, unknown>;
  brandingJson: Record<string, unknown>;
  createdAt: string;
  updatedAt: string;
}

export interface User {
  id: string;
  email: string;
  firstName?: string;
  lastName?: string;
  keycloakId?: string;
  role: 'OWNER' | 'ADMIN' | 'MEMBER' | 'VIEWER' | 'API_USER';
  status: 'INVITED' | 'ACTIVE' | 'SUSPENDED' | 'DEACTIVATED';
  lastLoginAt?: string;
  preferencesJson: Record<string, unknown>;
  tenantId: string;
  createdAt: string;
  updatedAt: string;
}

export interface Document {
  id: string;
  title: string;
  description?: string;
  sourceType: string;
  sourceUrl?: string;
  sourceId?: string;
  fileName?: string;
  mimeType?: string;
  fileSize?: number;
  storagePath?: string;
  status: 'UPLOADING' | 'PROCESSING' | 'INDEXED' | 'FAILED' | 'ARCHIVED';
  contentHash?: string;
  pageCount?: number;
  wordCount?: number;
  language?: string;
  metadataJson: Record<string, unknown>;
  processingError?: string;
  processedAt?: string;
  tenantId: string;
  createdById?: string;
  createdAt: string;
  updatedAt: string;
}

export interface DocumentChunk {
  id: string;
  documentId: string;
  content: string;
  chunkIndex: number;
  tokenCount?: number;
  startChar?: number;
  endChar?: number;
  metadataJson: Record<string, unknown>;
  tenantId: string;
  createdAt: string;
  updatedAt: string;
}

export interface Questionnaire {
  id: string;
  title: string;
  description?: string;
  sourceType:
    | 'UPLOAD_EXCEL'
    | 'UPLOAD_PDF'
    | 'UPLOAD_WORD'
    | 'PORTAL_LINK'
    | 'API_IMPORT'
    | 'EMAIL_FORWARD'
    | 'INTAKE_LINK';
  sourceUrl?: string;
  sourceId?: string;
  fileName?: string;
  status: 'DRAFT' | 'PROCESSING' | 'READY_FOR_REVIEW' | 'IN_REVIEW' | 'APPROVED' | 'SUBMITTED' | 'ARCHIVED';
  totalQuestions: number;
  answeredQuestions: number;
  approvedQuestions: number;
  dueDate?: string;
  submittedAt?: string;
  metadataJson: Record<string, unknown>;
  tenantId: string;
  assignedToId?: string;
  createdById?: string;
  createdAt: string;
  updatedAt: string;
}

/** A single-use upload link (api/rest/agent/IntakeController.IntakeLinkResponse). */
export interface IntakeLink {
  id: string;
  vendorName: string;
  vendorEmail: string;
  state: 'PENDING' | 'SUBMITTED' | 'EXPIRED';
  expiresAt: string;
  createdAt: string;
  questionnaireId?: string | null;
  submittedAt?: string | null;
  submitterName?: string | null;
  submitterEmail?: string | null;
}

export interface Question {
  id: string;
  questionnaireId: string;
  text: string;
  originalText?: string;
  questionType: 'FREE_TEXT' | 'SINGLE_CHOICE' | 'MULTIPLE_CHOICE' | 'YES_NO' | 'NUMERIC' | 'DATE' | 'FILE_UPLOAD' | 'TABLE';
  optionsJson: unknown[];
  section?: string;
  subsection?: string;
  questionNumber: string;
  rowIndex?: number;
  // Mirrors VantaQuestion.QuestionStatus.
  status:
    | 'PENDING'
    | 'AI_DRAFTED'
    | 'NEEDS_REVIEW'
    | 'APPROVED'
    | 'REJECTED'
    | 'SKIPPED'
    | 'DELEGATED'
    | 'HUMAN_EDITED';
  confidenceScore?: number;
  isRequired: boolean;
  tagsJson: string[];
  metadataJson: Record<string, unknown>;
  tenantId: string;
  createdAt: string;
  updatedAt: string;
}

export interface Answer {
  id: string;
  questionId: string;
  content?: string;
  aiGeneratedContent?: string;
  citationsJson: Citation[];
  confidenceScore?: number;
  status: 'DRAFT' | 'AI_GENERATED' | 'HUMAN_EDITED' | 'APPROVED' | 'REJECTED';
  reviewNotes?: string;
  reviewedAt?: string;
  reviewedById?: string;
  version: number;
  previousVersionId?: string;
  metadataJson: Record<string, unknown>;
  tenantId: string;
  createdAt: string;
  updatedAt: string;
}

export interface Citation {
  documentId: string;
  documentTitle: string;
  chunkId: string;
  chunkContent: string;
  relevanceScore: number;
  startChar?: number;
  endChar?: number;
}

export interface GeneratedAnswer {
  answer: string;
  citations: Citation[];
  confidenceScore: number;
}

export interface PaginatedResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}

export interface ApiError {
  timestamp: string;
  status: number;
  error: string;
  message: string;
  path: string;
}

export interface QuestionnaireWithDetails extends Questionnaire {
  questions: Question[];
  assignedTo?: User;
  createdBy?: User;
}

export interface QuestionWithAnswer extends Question {
  answer?: Answer;
}
// ---------------------------------------------------------------------------
// Vanta questionnaire pipeline (agentic orchestration + SME review)
// ---------------------------------------------------------------------------

export type VantaQuestionType =
  | 'FREE_TEXT'
  | 'SINGLE_CHOICE'
  | 'MULTIPLE_CHOICE'
  | 'YES_NO'
  | 'YES_NO_NA'
  | 'YES_NO_PARTIAL'
  | 'NUMERIC'
  | 'DATE'
  | 'FILE_UPLOAD'
  | 'TABLE'
  | 'ATTACHMENT';

export type VantaQuestionStatus =
  | 'PENDING'
  | 'AI_DRAFTED'
  | 'NEEDS_REVIEW'
  | 'APPROVED'
  | 'REJECTED'
  | 'SKIPPED'
  | 'DELEGATED'
  | 'HUMAN_EDITED';

export type VantaQuestionnaireStatus =
  | 'DRAFT'
  | 'PARSING'
  | 'PROCESSING'
  | 'READY_FOR_REVIEW'
  | 'IN_REVIEW'
  | 'APPROVED'
  | 'EXPORTED'
  | 'SUBMITTED'
  | 'ARCHIVED'
  | 'FAILED';

export type ConfidenceLevel = 'HIGH' | 'MEDIUM' | 'LOW';

export interface VantaQuestion {
  id: string;
  questionnaireId: string;
  questionNumber: string;
  text: string;
  originalText?: string;
  category?: string;
  subCategory?: string;
  section?: string;
  questionType: VantaQuestionType;
  optionsJson?: string;
  isRequired: boolean;
  isAttachmentRequest: boolean;
  status: VantaQuestionStatus;
  /** 0-100 integer, unlike the legacy Answer.confidenceScore 0-1 float. */
  confidenceScore?: number;
  aiAnswer?: string;
  selectedOption?: string;
  humanAnswer?: string;
  finalAnswer?: string;
  reviewNotes?: string;
  stalenessDetected: boolean;
  sourceLibraryEntryId?: string;
  citationIds: string[];
  assignedToId?: string;
  reviewedById?: string;
  reviewedAt?: string;
  approvedById?: string;
  approvedAt?: string;
  rowIndex?: number;
  colIndex?: number;
  sheetName?: string;
  tenantId: string;
  version: number;
  createdAt: string;
  updatedAt: string;
}

export interface VantaQuestionnaire {
  id: string;
  title: string;
  description?: string;
  sourceType: string;
  sourceUrl?: string;
  fileName?: string;
  status: VantaQuestionnaireStatus;
  totalQuestions: number;
  answeredQuestions: number;
  approvedQuestions: number;
  highConfidenceQuestions: number;
  needsReviewQuestions: number;
  delegatedQuestions: number;
  dueDate?: string;
  completedAt?: string;
  templateId?: string;
  tenantId: string;
  createdAt: string;
  updatedAt: string;
}

export interface VantaCitation {
  id: string;
  documentId: string;
  documentTitle: string;
  chunkId?: string;
  snippet: string;
  relevanceScore: number;
  pageNumber?: number;
  sectionTitle?: string;
}

export interface SMEProfile {
  userId: string;
  email: string;
  fullName: string;
  expertiseTags: string[];
  slackId?: string;
  maxConcurrentReviews: number;
  isActive: boolean;
  lastActiveAt?: string;
  activeReviewCount?: number;
}

export type SMEReviewStatus = 'PENDING' | 'IN_PROGRESS' | 'COMPLETED' | 'ESCALATED';

export interface ReviewQueueItem {
  questionId: string;
  questionnaireId: string;
  questionnaireTitle: string;
  questionNumber: string;
  questionText: string;
  category?: string;
  section?: string;
  questionType: VantaQuestionType;
  options: string[];
  aiAnswer?: string;
  finalAnswer?: string;
  confidenceScore?: number;
  confidenceLevel: ConfidenceLevel;
  questionStatus: VantaQuestionStatus;
  reviewStatus: SMEReviewStatus;
  assignedAt?: string;
  dueAt?: string;
  overdue: boolean;
  sme?: SMEProfile;
  citations: VantaCitation[];
  reviewNotes?: string;
  stalenessDetected: boolean;
  updatedAt: string;
}

export interface ReviewQueueStats {
  total: number;
  pending: number;
  inProgress: number;
  approved: number;
  rejected: number;
  overdue: number;
  highConfidence: number;
  activeSMEs: number;
}

export interface ReviewQueueResponse {
  items: ReviewQueueItem[];
  stats: ReviewQueueStats;
}

/** A peer currently viewing/editing the same review queue. */
export interface PresenceUser {
  userId: string;
  name: string;
  email: string;
  color: string;
  /** Question the peer currently has focused, if any. */
  focusedQuestionId?: string;
  editing: boolean;
  lastSeenAt: string;
}

export type CollaborationEvent =
  | { type: 'PRESENCE'; users: PresenceUser[] }
  | { type: 'QUESTION_UPDATED'; questionId: string; item: ReviewQueueItem; actorId: string; actorName: string }
  | { type: 'QUESTION_LOCKED'; questionId: string; actorId: string; actorName: string }
  | { type: 'QUESTION_UNLOCKED'; questionId: string; actorId: string }
  | { type: 'HEARTBEAT'; at: string };

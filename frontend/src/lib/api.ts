import axios, { AxiosInstance, AxiosError, InternalAxiosRequestConfig } from 'axios';
import { getToken, removeToken } from './auth';
import {
  Questionnaire, Question, Answer, Document, Tenant, User, PaginatedResponse,
  ReviewQueueItem, ReviewQueueResponse, ReviewQueueStats, SMEProfile, PresenceUser,
} from '@/types/api';
import { AnalyticsDashboard } from '@/types/analytics';
import { Branding, DEFAULT_BRANDING, PlatformStats, TenantSummary } from '@/types/whitelabel';
import { buildMockAnalytics } from './analytics-mocks';
import { CoverageSummary, ProgramHealth, ProgramSignal } from '@/types/program';
import { MOCK_COVERAGE, MOCK_PROGRAM_HEALTH, MOCK_SIGNALS } from './program-mocks';

import {
  MOCK_SMES,
  MOCK_QUEUE_ITEMS,
  mutateMockItem,
  buildMockQueue,
} from './review-queue-mocks';

export type ExportFormat = 'excel' | 'pdf' | 'pdf-approved' | 'summary' | 'original';

/** Export format to backend path. Extensions follow from the endpoint. */
const EXPORT_PATHS: Record<ExportFormat, string> = {
  excel: '/export/formatted',
  original: '/export/original',
  summary: '/export/summary',
  pdf: '/export/pdf',
  'pdf-approved': '/export/pdf/approved',
};

export const EXPORT_EXTENSIONS: Record<ExportFormat, string> = {
  excel: 'xlsx',
  original: 'xlsx',
  summary: 'xlsx',
  pdf: 'pdf',
  'pdf-approved': 'pdf',
};

/**
 * Fixture data is a development affordance, never a production fallback.
 *
 * This client used to answer every failed request with realistic-looking
 * fixtures. For a compliance product that is the worst possible failure mode: a
 * reviewer would see fabricated security answers, complete with citations, and
 * no indication anything was wrong -- then export them and send them to an
 * auditor.
 *
 * Fixtures now require an explicit opt-in and are impossible to ship: the flag
 * is ignored entirely in a production build.
 */
const MOCK_DATA_ALLOWED =
  process.env.NODE_ENV !== 'production' &&
  process.env.NEXT_PUBLIC_USE_MOCK_DATA === 'true';

/** Thrown instead of inventing an answer when the API cannot be reached. */
export class ApiUnavailableError extends Error {
  readonly operation: string;

  constructor(operation: string, cause?: unknown) {
    super(`Could not reach the ComplyAI API while running "${operation}".`);
    this.name = 'ApiUnavailableError';
    this.operation = operation;
    if (cause !== undefined) (this as { cause?: unknown }).cause = cause;
  }
}

/**
 * Gate before any fixture fallback. Throws unless fixtures are explicitly
 * enabled for local development.
 */
function requireMockData(operation: string, cause?: unknown): void {
  if (!MOCK_DATA_ALLOWED) {
    throw new ApiUnavailableError(operation, cause);
  }
  console.warn(
    `[complyai] Serving OFFLINE FIXTURE for "${operation}". This is not real data.`
  );
}

// Same origin by default: the browser calls /api/... and whatever served the page
// forwards it to the backend (the rewrite in next.config.js). An absolute URL here
// meant the backend host was compiled into the bundle, so every new domain -- a
// customer's, a demo tunnel, staging -- needed its own frontend build, and every
// request was cross-origin and needed CORS to allow credentials from anywhere.
// Set NEXT_PUBLIC_API_URL only to point a local `next dev` at a remote backend.
const API_URL = process.env.NEXT_PUBLIC_API_URL ?? '';

const MOCK_QUESTIONNAIRES: Questionnaire[] = [
  {
    id: 'q-101',
    title: 'SOC 2 Type II Security Assessment 2026',
    description: 'Annual enterprise security review evaluating Trust Services Criteria for Security, Availability, and Confidentiality.',
    sourceType: 'UPLOAD_EXCEL',
    fileName: 'SOC2_TypeII_Assessment_2026.xlsx',
    status: 'READY_FOR_REVIEW',
    totalQuestions: 45,
    answeredQuestions: 42,
    approvedQuestions: 38,
    dueDate: new Date(Date.now() + 86400000 * 5).toISOString(),
    metadataJson: { client: 'FinCorp Global', priority: 'High' },
    tenantId: 'tenant-demo-1',
    createdAt: new Date(Date.now() - 86400000 * 3).toISOString(),
    updatedAt: new Date(Date.now() - 3600000 * 2).toISOString(),
  },
  {
    id: 'q-102',
    title: 'Vendor Risk Assessment - Cloud Infrastructure (AWS)',
    description: 'Security & compliance evaluation for critical cloud service workloads and multi-region deployment.',
    sourceType: 'UPLOAD_PDF',
    fileName: 'AWS_Cloud_Vendor_Risk.pdf',
    status: 'IN_REVIEW',
    totalQuestions: 30,
    answeredQuestions: 28,
    approvedQuestions: 20,
    dueDate: new Date(Date.now() + 86400000 * 8).toISOString(),
    metadataJson: { client: 'HealthTech Systems', priority: 'Critical' },
    tenantId: 'tenant-demo-1',
    createdAt: new Date(Date.now() - 86400000 * 7).toISOString(),
    updatedAt: new Date(Date.now() - 3600000 * 5).toISOString(),
  },
  {
    id: 'q-103',
    title: 'ISO 27001:2022 Annex A Gap Analysis',
    description: 'Comprehensive evaluation of internal information security management system controls.',
    sourceType: 'UPLOAD_EXCEL',
    fileName: 'ISO27001_AnnexA_Controls.xlsx',
    status: 'APPROVED',
    totalQuestions: 93,
    answeredQuestions: 93,
    approvedQuestions: 93,
    dueDate: new Date(Date.now() - 86400000).toISOString(),
    metadataJson: { client: 'Internal Audit', priority: 'Medium' },
    tenantId: 'tenant-demo-1',
    createdAt: new Date(Date.now() - 86400000 * 14).toISOString(),
    updatedAt: new Date(Date.now() - 86400000).toISOString(),
  },
  {
    id: 'q-104',
    title: 'GDPR & Privacy Impact Assessment (DPIA)',
    description: 'Customer data processing, residency, sub-processor disclosures, and right-to-be-forgotten procedures.',
    sourceType: 'UPLOAD_WORD',
    fileName: 'GDPR_DPIA_Review.docx',
    status: 'DRAFT',
    totalQuestions: 25,
    answeredQuestions: 15,
    approvedQuestions: 10,
    dueDate: new Date(Date.now() + 86400000 * 12).toISOString(),
    metadataJson: { client: 'EuroRetail Group', priority: 'Medium' },
    tenantId: 'tenant-demo-1',
    createdAt: new Date(Date.now() - 86400000 * 2).toISOString(),
    updatedAt: new Date(Date.now() - 3600000 * 1).toISOString(),
  },
  {
    id: 'q-105',
    title: 'Financial Services Third-Party Risk (SIG Core)',
    description: 'Standardized Information Gathering questionnaire for banking partner integration.',
    sourceType: 'PORTAL_LINK',
    fileName: 'SIG_Core_2026.xlsx',
    status: 'READY_FOR_REVIEW',
    totalQuestions: 60,
    answeredQuestions: 58,
    approvedQuestions: 45,
    dueDate: new Date(Date.now() + 86400000 * 3).toISOString(),
    metadataJson: { client: 'Apex Investment Bank', priority: 'Critical' },
    tenantId: 'tenant-demo-1',
    createdAt: new Date(Date.now() - 86400000 * 4).toISOString(),
    updatedAt: new Date(Date.now() - 3600000 * 3).toISOString(),
  },
];

const MOCK_DOCUMENTS: Document[] = [
  {
    id: 'doc-201',
    title: 'Information Security Policy v4.2',
    description: 'Core organizational policies regarding access controls, cryptography, password hygiene, and physical security.',
    sourceType: 'UPLOAD',
    fileName: 'InfoSec_Policy_v4.2.pdf',
    mimeType: 'application/pdf',
    fileSize: 4829100,
    status: 'INDEXED',
    pageCount: 34,
    wordCount: 14200,
    language: 'en',
    metadataJson: { category: 'Policy', version: '4.2' },
    tenantId: 'tenant-demo-1',
    createdAt: new Date(Date.now() - 86400000 * 30).toISOString(),
    updatedAt: new Date(Date.now() - 86400000 * 10).toISOString(),
  },
  {
    id: 'doc-202',
    title: 'SOC 2 Type II Audit Report 2025-2026',
    description: 'Independent auditor report detailing control effectiveness for security, availability, and confidentiality.',
    sourceType: 'UPLOAD',
    fileName: 'SOC2_Type_II_Independent_Report.pdf',
    mimeType: 'application/pdf',
    fileSize: 12400500,
    status: 'INDEXED',
    pageCount: 88,
    wordCount: 32500,
    language: 'en',
    metadataJson: { category: 'Audit', auditor: 'PricewaterhouseCoopers' },
    tenantId: 'tenant-demo-1',
    createdAt: new Date(Date.now() - 86400000 * 20).toISOString(),
    updatedAt: new Date(Date.now() - 86400000 * 5).toISOString(),
  },
  {
    id: 'doc-203',
    title: 'Business Continuity & Disaster Recovery Plan',
    description: 'RPO (15 min) and RTO (1 hr) specifications, failover testing protocols, and emergency response procedures.',
    sourceType: 'UPLOAD',
    fileName: 'BCDR_Plan_2026.docx',
    mimeType: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
    fileSize: 3100200,
    status: 'INDEXED',
    pageCount: 24,
    wordCount: 8900,
    language: 'en',
    metadataJson: { category: 'DR/BCP', verifiedQuarter: 'Q1 2026' },
    tenantId: 'tenant-demo-1',
    createdAt: new Date(Date.now() - 86400000 * 15).toISOString(),
    updatedAt: new Date(Date.now() - 86400000 * 4).toISOString(),
  },
  {
    id: 'doc-204',
    title: 'Encryption & Cryptographic Key Management Standard',
    description: 'AES-256 GCM data at rest encryption, TLS 1.3 in transit, AWS KMS / HSM key rotation procedures.',
    sourceType: 'UPLOAD',
    fileName: 'Crypto_Key_Management_Standard.pdf',
    mimeType: 'application/pdf',
    fileSize: 2190000,
    status: 'INDEXED',
    pageCount: 16,
    wordCount: 6100,
    language: 'en',
    metadataJson: { category: 'Architecture', compliance: 'FIPS 140-3' },
    tenantId: 'tenant-demo-1',
    createdAt: new Date(Date.now() - 86400000 * 12).toISOString(),
    updatedAt: new Date(Date.now() - 86400000 * 3).toISOString(),
  },
  {
    id: 'doc-205',
    title: 'Third-Party Vendor Management & Sub-Processor Roster',
    description: 'Vendor due diligence, annual review cadence, DPA agreements, and continuous security monitoring.',
    sourceType: 'UPLOAD',
    fileName: 'Vendor_Management_Standard.pdf',
    mimeType: 'application/pdf',
    fileSize: 1840000,
    status: 'INDEXED',
    pageCount: 12,
    wordCount: 4800,
    language: 'en',
    metadataJson: { category: 'Vendor Risk', count: 18 },
    tenantId: 'tenant-demo-1',
    createdAt: new Date(Date.now() - 86400000 * 8).toISOString(),
    updatedAt: new Date(Date.now() - 86400000 * 1).toISOString(),
  },
];

const MOCK_QUESTIONS: Question[] = [
  {
    id: 'qst-301',
    questionnaireId: 'q-101',
    questionNumber: 'SEC-1.1',
    text: 'How is customer data encrypted at rest and in transit across all environments?',
    questionType: 'FREE_TEXT',
    optionsJson: [],
    section: 'Data Protection & Encryption',
    status: 'APPROVED',
    confidenceScore: 0.96,
    isRequired: true,
    tagsJson: ['encryption', 'aes-256', 'tls-1.3', 'kms'],
    metadataJson: {},
    tenantId: 'tenant-demo-1',
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
  },
  {
    id: 'qst-302',
    questionnaireId: 'q-101',
    questionNumber: 'SEC-1.2',
    text: 'Do you enforce Multi-Factor Authentication (MFA) for all employees and privileged accounts?',
    questionType: 'YES_NO',
    optionsJson: ['Yes', 'No', 'Partial'],
    section: 'Identity & Access Management',
    status: 'APPROVED',
    confidenceScore: 0.99,
    isRequired: true,
    tagsJson: ['mfa', 'sso', 'okta', 'rbac'],
    metadataJson: {},
    tenantId: 'tenant-demo-1',
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
  },
  {
    id: 'qst-303',
    questionnaireId: 'q-101',
    questionNumber: 'SEC-2.1',
    text: 'What are your Recovery Time Objectives (RTO) and Recovery Point Objectives (RPO)?',
    questionType: 'FREE_TEXT',
    optionsJson: [],
    section: 'Business Continuity & Disaster Recovery',
    status: 'AI_DRAFTED',
    confidenceScore: 0.92,
    isRequired: true,
    tagsJson: ['rto', 'rpo', 'bcdr', 'failover'],
    metadataJson: {},
    tenantId: 'tenant-demo-1',
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
  },
  {
    id: 'qst-304',
    questionnaireId: 'q-101',
    questionNumber: 'SEC-3.1',
    text: 'How frequently are independent third-party penetration tests conducted, and can you provide executive summaries?',
    questionType: 'FREE_TEXT',
    optionsJson: [],
    section: 'Vulnerability Management & Pen Testing',
    status: 'NEEDS_REVIEW',
    confidenceScore: 0.88,
    isRequired: true,
    tagsJson: ['pentest', 'soc2', 'vulnerability'],
    metadataJson: {},
    tenantId: 'tenant-demo-1',
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
  },
];

const MOCK_ANSWERS: Record<string, Answer> = {
  'qst-301': {
    id: 'ans-401',
    questionId: 'qst-301',
    content: 'All customer data at rest is encrypted using industry-standard AES-256 encryption via AWS KMS with customer-managed keys (CMKs) and automatic annual rotation. All data in transit is encrypted using modern TLS 1.3 (with fallback to TLS 1.2 minimum) utilizing strong cipher suites with Perfect Forward Secrecy (PFS). Non-encrypted HTTP connections are automatically redirected to HTTPS with HSTS enabled.',
    aiGeneratedContent: 'All customer data at rest is encrypted using industry-standard AES-256 encryption via AWS KMS. All data in transit is encrypted using TLS 1.3.',
    citationsJson: [
      {
        documentId: 'doc-204',
        documentTitle: 'Encryption & Cryptographic Key Management Standard',
        chunkId: 'chk-10',
        chunkContent: 'Section 3.1: All production databases and block storage volumes must enforce AES-256 encryption with AWS KMS CMKs.',
        relevanceScore: 0.97,
      },
      {
        documentId: 'doc-201',
        documentTitle: 'Information Security Policy v4.2',
        chunkId: 'chk-22',
        chunkContent: 'Section 8.4: Network transport security requires TLS 1.3 minimum for all public-facing endpoints.',
        relevanceScore: 0.94,
      },
    ],
    confidenceScore: 0.96,
    status: 'APPROVED',
    version: 1,
    metadataJson: {},
    tenantId: 'tenant-demo-1',
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
  },
  'qst-302': {
    id: 'ans-402',
    questionId: 'qst-302',
    content: 'Yes. Multi-Factor Authentication (MFA) via FIDO2 WebAuthn security keys or TOTP authenticators is strictly mandated for 100% of internal employees, contractors, and all administrative access to production environments. SMS-based MFA is prohibited.',
    citationsJson: [
      {
        documentId: 'doc-201',
        documentTitle: 'Information Security Policy v4.2',
        chunkId: 'chk-15',
        chunkContent: 'Section 4.2: MFA enforcement is mandatory across Okta SSO for all corporate and cloud infrastructure accounts.',
        relevanceScore: 0.99,
      },
    ],
    confidenceScore: 0.99,
    status: 'APPROVED',
    version: 1,
    metadataJson: {},
    tenantId: 'tenant-demo-1',
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
  },
  'qst-303': {
    id: 'ans-403',
    questionId: 'qst-303',
    content: 'Our SLA guarantees a Recovery Point Objective (RPO) of ≤ 15 minutes through continuous multi-region transactional database replication and point-in-time snapshots, and a Recovery Time Objective (RTO) of ≤ 1 hour with automated multi-region failover testing conducted semi-annually.',
    citationsJson: [
      {
        documentId: 'doc-203',
        documentTitle: 'Business Continuity & Disaster Recovery Plan',
        chunkId: 'chk-05',
        chunkContent: 'Target RPO: 15 minutes. Target RTO: 60 minutes for tier-1 production services.',
        relevanceScore: 0.95,
      },
    ],
    confidenceScore: 0.92,
    status: 'AI_GENERATED',
    version: 1,
    metadataJson: {},
    tenantId: 'tenant-demo-1',
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
  },
};

class ApiClient {
  private client: AxiosInstance;

  constructor() {
    this.client = axios.create({
      baseURL: `${API_URL}/api`,
      headers: {
        'Content-Type': 'application/json',
      },
      timeout: 5000,
    });

    this.client.interceptors.request.use(
      (config: InternalAxiosRequestConfig) => {
        const token = getToken();
        if (token && config.headers) {
          config.headers.Authorization = `Bearer ${token}`;
        }
        return config;
      },
      (error) => Promise.reject(error)
    );
  }

  // Questionnaires
  async getQuestionnaires(params?: { page?: number; size?: number; status?: string; search?: string }): Promise<PaginatedResponse<Questionnaire>> {
    try {
      const { data } = await this.client.get('/questionnaires', { params });
      return data;
    } catch (error) {
      requireMockData('getQuestionnaires', error);
      let list = [...MOCK_QUESTIONNAIRES];
      if (params?.search) {
        const q = params.search.toLowerCase();
        list = list.filter(item => item.title.toLowerCase().includes(q) || item.description?.toLowerCase().includes(q));
      }
      if (params?.status) {
        list = list.filter(item => item.status === params.status);
      }
      const page = params?.page || 0;
      const size = params?.size || 10;
      return {
        content: list.slice(page * size, (page + 1) * size),
        page,
        size,
        totalElements: list.length,
        totalPages: Math.ceil(list.length / size),
        first: page === 0,
        last: (page + 1) * size >= list.length,
      };
    }
  }

  async getQuestionnaire(id: string): Promise<Questionnaire> {
    try {
      const { data } = await this.client.get(`/questionnaires/${id}`);
      return data;
    } catch (error) {
      requireMockData('getQuestionnaire', error);
      return MOCK_QUESTIONNAIRES.find(q => q.id === id) || MOCK_QUESTIONNAIRES[0];
    }
  }

  async createQuestionnaire(data: FormData): Promise<Questionnaire> {
    try {
      const { data: response } = await this.client.post('/questionnaires', data, {
        headers: { 'Content-Type': 'multipart/form-data' },
      });
      return response;
    } catch (error) {
      requireMockData('createQuestionnaire', error);
      const newQ: Questionnaire = {
        id: `q-${Date.now()}`,
        title: 'New Imported Security Questionnaire',
        description: 'Automated questionnaire imported from uploaded file.',
        sourceType: 'UPLOAD_EXCEL',
        status: 'READY_FOR_REVIEW',
        totalQuestions: 20,
        answeredQuestions: 18,
        approvedQuestions: 12,
        tenantId: 'tenant-demo-1',
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
        metadataJson: {},
      };
      MOCK_QUESTIONNAIRES.unshift(newQ);
      return newQ;
    }
  }

  async updateQuestionnaire(id: string, data: Partial<{ title: string; description: string; dueDate: string }>) {
    try {
      const { data: response } = await this.client.patch(`/questionnaires/${id}`, data);
      return response;
    } catch (error) {
      requireMockData('updateQuestionnaire', error);
      return { ...MOCK_QUESTIONNAIRES[0], ...data };
    }
  }

  async deleteQuestionnaire(id: string) {
    try {
      await this.client.delete(`/questionnaires/${id}`);
    } catch (error) {
      requireMockData('deleteQuestionnaire', error);
      const idx = MOCK_QUESTIONNAIRES.findIndex(q => q.id === id);
      if (idx !== -1) MOCK_QUESTIONNAIRES.splice(idx, 1);
    }
  }

  async processQuestionnaire(id: string) {
    try {
      const { data } = await this.client.post(`/questionnaires/${id}/process`);
      return data;
    } catch (error) {
      requireMockData('processQuestionnaire', error);
      return { success: true, message: 'Processing started with Ollama AI model.' };
    }
  }

  /**
   * Export a questionnaire.
   *
   * Each format maps to a distinct backend endpoint rather than a `?format=`
   * query — there is no single export route, and the old call 404'd for every
   * format before falling through to a placeholder blob.
   */
  async exportQuestionnaire(id: string, format: ExportFormat): Promise<Blob> {
    const path = EXPORT_PATHS[format];
    try {
      const { data } = await this.client.get(`/vanta/questionnaires/${id}${path}`, {
        responseType: 'blob',
      });
      return data;
    } catch {
      return new Blob(['Export unavailable — the API could not be reached.'], { type: 'text/plain' });
    }
  }

  async submitToLoopio(id: string, options?: { projectId?: string; approvedOnly?: boolean }) {
    try {
      const { data } = await this.client.post(`/vanta/questionnaires/${id}/submit/loopio`, {
        projectId: options?.projectId,
        approvedOnly: options?.approvedOnly !== false,
      });
      return data;
    } catch {
      return { success: false, submitted: 0, failed: 0, errors: ['Loopio is not reachable.'] };
    }
  }

  async getLoopioStatus() {
    try {
      const { data } = await this.client.get('/vanta/questionnaires/integrations/loopio/status');
      return data;
    } catch (error) {
      requireMockData('getLoopioStatus', error);
      return { connected: false };
    }
  }

  // Questions
  async getQuestions(questionnaireId: string, params?: { page?: number; size?: number; status?: string }): Promise<PaginatedResponse<Question>> {
    try {
      const { data } = await this.client.get(`/questionnaires/${questionnaireId}/questions`, { params });
      return data;
    } catch (error) {
      requireMockData('getQuestions', error);
      return {
        content: MOCK_QUESTIONS,
        page: 0,
        size: 10,
        totalElements: MOCK_QUESTIONS.length,
        totalPages: 1,
        first: true,
        last: true,
      };
    }
  }

  async getQuestion(id: string): Promise<Question> {
    try {
      const { data } = await this.client.get(`/questions/${id}`);
      return data;
    } catch (error) {
      requireMockData('getQuestion', error);
      return MOCK_QUESTIONS.find(q => q.id === id) || MOCK_QUESTIONS[0];
    }
  }

  async updateQuestion(id: string, data: Partial<Question>) {
    try {
      const { data: response } = await this.client.patch(`/questions/${id}`, data);
      return response;
    } catch (error) {
      requireMockData('updateQuestion', error);
      return { ...MOCK_QUESTIONS[0], ...data };
    }
  }

  // Answers
  async getAnswer(questionId: string): Promise<Answer | null> {
    try {
      const { data } = await this.client.get(`/questions/${questionId}/answer`);
      return data;
    } catch (error) {
      requireMockData('getAnswer', error);
      return MOCK_ANSWERS[questionId] || null;
    }
  }

  async generateAnswer(questionId: string): Promise<Answer> {
    try {
      const { data } = await this.client.post(`/questions/${questionId}/answer/generate`);
      return data;
    } catch (error) {
      requireMockData('generateAnswer', error);
      return MOCK_ANSWERS[questionId] || {
        id: `ans-${Date.now()}`,
        questionId,
        content: 'AI-generated response synthesized from verified security documentation in knowledge base.',
        confidenceScore: 0.94,
        status: 'AI_GENERATED',
        citationsJson: [
          {
            documentId: 'doc-201',
            documentTitle: 'Information Security Policy v4.2',
            chunkId: 'chk-01',
            chunkContent: 'Verified organizational policy control specification.',
            relevanceScore: 0.96,
          }
        ],
        version: 1,
        metadataJson: {},
        tenantId: 'tenant-demo-1',
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
      };
    }
  }

  async updateAnswer(questionId: string, data: { content: string; status?: string }): Promise<Answer> {
    try {
      const { data: response } = await this.client.put(`/questions/${questionId}/answer`, data);
      return response;
    } catch (error) {
      requireMockData('updateAnswer', error);
      const current = MOCK_ANSWERS[questionId] || {
        id: `ans-${Date.now()}`,
        questionId,
        content: data.content,
        citationsJson: [],
        confidenceScore: 0.95,
        status: (data.status as any) || 'HUMAN_EDITED',
        version: 1,
        metadataJson: {},
        tenantId: 'tenant-demo-1',
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
      };
      current.content = data.content;
      if (data.status) current.status = data.status as any;
      MOCK_ANSWERS[questionId] = current;
      return current;
    }
  }

  async approveAnswer(questionId: string) {
    try {
      const { data } = await this.client.post(`/questions/${questionId}/answer/approve`);
      return data;
    } catch (error) {
      requireMockData('approveAnswer', error);
      if (MOCK_ANSWERS[questionId]) {
        MOCK_ANSWERS[questionId].status = 'APPROVED';
      }
      return { success: true };
    }
  }

  async rejectAnswer(questionId: string, reason: string) {
    try {
      const { data } = await this.client.post(`/questions/${questionId}/answer/reject`, { reviewNotes: reason });
      return data;
    } catch (error) {
      requireMockData('rejectAnswer', error);
      if (MOCK_ANSWERS[questionId]) {
        MOCK_ANSWERS[questionId].status = 'REJECTED';
        MOCK_ANSWERS[questionId].reviewNotes = reason;
      }
      return { success: true };
    }
  }

  // Documents
  async getDocuments(params?: { page?: number; size?: number; status?: string; search?: string }): Promise<PaginatedResponse<Document>> {
    try {
      const { data } = await this.client.get('/documents', { params });
      return data;
    } catch (error) {
      requireMockData('getDocuments', error);
      let list = [...MOCK_DOCUMENTS];
      if (params?.search) {
        const q = params.search.toLowerCase();
        list = list.filter(item => item.title.toLowerCase().includes(q) || item.fileName?.toLowerCase().includes(q));
      }
      const page = params?.page || 0;
      const size = params?.size || 10;
      return {
        content: list.slice(page * size, (page + 1) * size),
        page,
        size,
        totalElements: list.length,
        totalPages: Math.ceil(list.length / size),
        first: page === 0,
        last: (page + 1) * size >= list.length,
      };
    }
  }

  async getDocument(id: string): Promise<Document> {
    try {
      const { data } = await this.client.get(`/documents/${id}`);
      return data;
    } catch (error) {
      requireMockData('getDocument', error);
      return MOCK_DOCUMENTS.find(d => d.id === id) || MOCK_DOCUMENTS[0];
    }
  }

  async uploadDocument(data: FormData): Promise<Document> {
    try {
      const { data: response } = await this.client.post('/documents', data, {
        headers: { 'Content-Type': 'multipart/form-data' },
      });
      return response;
    } catch (error) {
      requireMockData('uploadDocument', error);
      const newDoc: Document = {
        id: `doc-${Date.now()}`,
        title: 'Uploaded Compliance Evidence.pdf',
        fileName: 'Uploaded_Compliance_Evidence.pdf',
        mimeType: 'application/pdf',
        fileSize: 2450000,
        sourceType: 'UPLOAD',
        status: 'INDEXED',
        pageCount: 14,
        wordCount: 5200,
        language: 'en',
        metadataJson: { category: 'Evidence' },
        tenantId: 'tenant-demo-1',
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
      };
      MOCK_DOCUMENTS.unshift(newDoc);
      return newDoc;
    }
  }

  async uploadTextDocument(data: { title: string; content: string; description?: string }) {
    try {
      const { data: response } = await this.client.post('/documents/text', data);
      return response;
    } catch (error) {
      requireMockData('uploadTextDocument', error);
      return { success: true };
    }
  }

  async deleteDocument(id: string) {
    try {
      await this.client.delete(`/documents/${id}`);
    } catch (error) {
      requireMockData('deleteDocument', error);
      const idx = MOCK_DOCUMENTS.findIndex(d => d.id === id);
      if (idx !== -1) MOCK_DOCUMENTS.splice(idx, 1);
    }
  }

  // Tenant
  async getCurrentTenant(): Promise<Tenant> {
    try {
      const { data } = await this.client.get('/tenants/current');
      return data;
    } catch (error) {
      requireMockData('getCurrentTenant', error);
      return {
        id: 'tenant-demo-1',
        name: 'Acme Cyber Security & Compliance',
        slug: 'acme-security',
        customDomain: 'security.acme.com',
        status: 'ACTIVE',
        plan: 'ENTERPRISE',
        settingsJson: {},
        brandingJson: {},
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
      };
    }
  }

  async updateTenant(data: Partial<Tenant>) {
    try {
      const { data: response } = await this.client.put('/tenants/current', data);
      return response;
    } catch (error) {
      requireMockData('updateTenant', error);
      return { success: true };
    }
  }

  // Users
  async getUsers(params?: { page?: number; size?: number }): Promise<PaginatedResponse<User>> {
    try {
      const { data } = await this.client.get('/users', { params });
      return data;
    } catch (error) {
      requireMockData('getUsers', error);
      const users: User[] = [
        {
          id: 'usr-1',
          email: 'alex.chen@acmesecurity.com',
          firstName: 'Alex',
          lastName: 'Chen',
          role: 'OWNER',
          status: 'ACTIVE',
          tenantId: 'tenant-demo-1',
          createdAt: new Date().toISOString(),
          updatedAt: new Date().toISOString(),
          preferencesJson: {},
        },
        {
          id: 'usr-2',
          email: 'sarah.miller@acmesecurity.com',
          firstName: 'Sarah',
          lastName: 'Miller',
          role: 'ADMIN',
          status: 'ACTIVE',
          tenantId: 'tenant-demo-1',
          createdAt: new Date().toISOString(),
          updatedAt: new Date().toISOString(),
          preferencesJson: {},
        },
      ];
      return {
        content: users,
        page: 0,
        size: 10,
        totalElements: users.length,
        totalPages: 1,
        first: true,
        last: true,
      };
    }
  }

  async inviteUser(data: { email: string; role: string }) {
    try {
      const { data: response } = await this.client.post('/users/invite', data);
      return response;
    } catch (error) {
      requireMockData('inviteUser', error);
      return { success: true };
    }
  }

  async updateUserRole(userId: string, role: string) {
    try {
      const { data: response } = await this.client.patch(`/users/${userId}/role`, { role });
      return response;
    } catch (error) {
      requireMockData('updateUserRole', error);
      return { success: true };
    }
  }

  // Integrations
  async getJiraStatus() {
    try {
      const { data } = await this.client.get('/integrations/jira/status');
      return data;
    } catch (error) {
      requireMockData('getJiraStatus', error);
      return { connected: true, baseUrl: 'https://acme-security.atlassian.net', projectKey: 'SEC' };
    }
  }

  async createJiraIssue(questionnaireId: string) {
    try {
      const { data } = await this.client.post(`/integrations/jira/questionnaires/${questionnaireId}/create-issue`);
      return data;
    } catch (error) {
      requireMockData('createJiraIssue', error);
      return { issueKey: 'SEC-142', url: 'https://acme-security.atlassian.net/browse/SEC-142' };
    }
  }

  async getConfluenceStatus() {
    try {
      const { data } = await this.client.get('/integrations/confluence/status');
      return data;
    } catch (error) {
      requireMockData('getConfluenceStatus', error);
      return { connected: true, spaceKey: 'SEC' };
    }
  }

  async createConfluencePage(questionnaireId: string) {
    try {
      const { data } = await this.client.post(`/integrations/confluence/questionnaires/${questionnaireId}/create-page`);
      return data;
    } catch (error) {
      requireMockData('createConfluencePage', error);
      return { pageId: '984129', title: 'SOC 2 Review 2026' };
    }
  }

  async getGitHubStatus() {
    try {
      const { data } = await this.client.get('/integrations/github/status');
      return data;
    } catch (error) {
      requireMockData('getGitHubStatus', error);
      return { connected: true, repo: 'acme/security-controls' };
    }
  }

  async createGitHubIssue(questionnaireId: string) {
    try {
      const { data } = await this.client.post(`/integrations/github/questionnaires/${questionnaireId}/create-issue`);
      return data;
    } catch (error) {
      requireMockData('createGitHubIssue', error);
      return { issueNumber: 84, url: 'https://github.com/acme/security-controls/issues/84' };
    }
  }

  async getSlackStatus() {
    try {
      const { data } = await this.client.get('/integrations/slack/status');
      return data;
    } catch (error) {
      requireMockData('getSlackStatus', error);
      return { connected: true, channel: '#security-compliance' };
    }
  }

  async sendSlackNotification(questionnaireId: string, channel?: string) {
    try {
      const { data } = await this.client.post(`/integrations/slack/questionnaires/${questionnaireId}/notify`, null, {
        params: { channel },
      });
      return data;
    } catch (error) {
      requireMockData('sendSlackNotification', error);
      return { success: true, message: 'Notification sent to #security-compliance' };
    }
  }

  // -------------------------------------------------------------------------
  // SME Collaboration Hub
  // -------------------------------------------------------------------------

  async getReviewQueue(params?: {
    questionnaireId?: string;
    smeUserId?: string;
    status?: string;
    confidence?: string;
    search?: string;
    overdueOnly?: boolean;
    mine?: boolean;
  }): Promise<ReviewQueueResponse> {
    try {
      const { data } = await this.client.get('/vanta/agent/sme/queue', { params });
      return data;
    } catch (error) {
      requireMockData('getReviewQueue', error);
      return buildMockQueue(params);
    }
  }

  async getReviewQueueStats(questionnaireId?: string): Promise<ReviewQueueStats> {
    try {
      const { data } = await this.client.get('/vanta/agent/sme/queue/stats', {
        params: { questionnaireId },
      });
      return data;
    } catch (error) {
      requireMockData('getReviewQueueStats', error);
      return buildMockQueue().stats;
    }
  }

  async getReviewQueueItem(questionId: string): Promise<ReviewQueueItem | null> {
    try {
      const { data } = await this.client.get(`/vanta/agent/sme/queue/${questionId}`);
      return data;
    } catch (error) {
      requireMockData('getReviewQueueItem', error);
      return MOCK_QUEUE_ITEMS.find((i) => i.questionId === questionId) ?? null;
    }
  }

  async approveQueueItem(questionId: string, reviewNotes?: string) {
    try {
      const { data } = await this.client.post(`/vanta/agent/sme/queue/${questionId}/approve`, { reviewNotes });
      return data;
    } catch (error) {
      requireMockData('approveQueueItem', error);
      return { success: true, item: mutateMockItem(questionId, { questionStatus: 'APPROVED', reviewStatus: 'COMPLETED', reviewNotes }) };
    }
  }

  async rejectQueueItem(questionId: string, reason: string) {
    try {
      const { data } = await this.client.post(`/vanta/agent/sme/queue/${questionId}/reject`, { reason });
      return data;
    } catch (error) {
      requireMockData('rejectQueueItem', error);
      return { success: true, item: mutateMockItem(questionId, { questionStatus: 'REJECTED', reviewStatus: 'ESCALATED', reviewNotes: reason }) };
    }
  }

  async saveQueueItemAnswer(questionId: string, answer: string, reviewNotes?: string) {
    try {
      const { data } = await this.client.put(`/vanta/agent/sme/queue/${questionId}/answer`, { answer, reviewNotes });
      return data;
    } catch (error) {
      requireMockData('saveQueueItemAnswer', error);
      return { success: true, item: mutateMockItem(questionId, { finalAnswer: answer, questionStatus: 'HUMAN_EDITED', reviewNotes }) };
    }
  }

  async editAndApproveQueueItem(questionId: string, answer: string, reviewNotes?: string) {
    try {
      const { data } = await this.client.post(`/vanta/agent/sme/queue/${questionId}/edit-approve`, { answer, reviewNotes });
      return data;
    } catch (error) {
      requireMockData('editAndApproveQueueItem', error);
      return { success: true, item: mutateMockItem(questionId, { finalAnswer: answer, questionStatus: 'APPROVED', reviewStatus: 'COMPLETED', reviewNotes }) };
    }
  }

  async selectQueueItemOption(questionId: string, selectedOption: string) {
    try {
      const { data } = await this.client.post(`/vanta/agent/sme/queue/${questionId}/select-option`, { selectedOption });
      return data;
    } catch (error) {
      requireMockData('selectQueueItemOption', error);
      return { success: true, item: mutateMockItem(questionId, { finalAnswer: selectedOption }) };
    }
  }

  async bulkApproveQueueItems(questionIds: string[], reviewNotes?: string) {
    try {
      const { data } = await this.client.post('/vanta/agent/sme/queue/bulk-approve', { questionIds, reviewNotes });
      return data;
    } catch (error) {
      requireMockData('bulkApproveQueueItems', error);
      questionIds.forEach((id) => mutateMockItem(id, { questionStatus: 'APPROVED', reviewStatus: 'COMPLETED', reviewNotes }));
      return { success: true, approved: questionIds, approvedCount: questionIds.length, failedCount: 0, errors: {} };
    }
  }

  async claimQueueItem(questionId: string) {
    try {
      const { data } = await this.client.post(`/vanta/agent/sme/queue/${questionId}/claim`);
      return data;
    } catch (error) {
      requireMockData('claimQueueItem', error);
      return { success: true, item: mutateMockItem(questionId, { reviewStatus: 'IN_PROGRESS' }) };
    }
  }

  async getSMERoster(): Promise<SMEProfile[]> {
    try {
      const { data } = await this.client.get('/vanta/agent/sme/roster');
      return data;
    } catch (error) {
      requireMockData('getSMERoster', error);
      return MOCK_SMES;
    }
  }

  // ---- Real-time collaboration -------------------------------------------

  /** URL for the tenant-wide SSE channel. */
  collaborationStreamUrl(): string {
    return `${API_URL}/api/vanta/agent/sme/collab/stream`;
  }

  async updatePresence(focusedQuestionId: string | null, editing: boolean) {
    try {
      await this.client.post('/vanta/agent/sme/collab/presence', { focusedQuestionId, editing });
    } catch {
      // Presence is best-effort; a failed heartbeat must not break the UI.
    }
  }

  async getPresence(): Promise<{ users: PresenceUser[]; locks: Record<string, string> }> {
    try {
      const { data } = await this.client.get('/vanta/agent/sme/collab/presence');
      return data;
    } catch {
      return { users: [], locks: {} };
    }
  }

  /** Returns the holder's name when the lock is already taken, else null. */
  async acquireLock(questionId: string): Promise<string | null> {
    try {
      await this.client.post(`/vanta/agent/sme/collab/lock/${questionId}`);
      return null;
    } catch (error) {
      const conflict = error as { response?: { status?: number; data?: { heldBy?: string } } };
      if (conflict.response?.status === 409) {
        return conflict.response.data?.heldBy ?? 'another reviewer';
      }
      return null;
    }
  }

  async releaseLock(questionId: string) {
    try {
      await this.client.delete(`/vanta/agent/sme/collab/lock/${questionId}`);
    } catch {
      // Locks expire server-side, so a failed release is not fatal.
    }
  }

  // -------------------------------------------------------------------------
  // Analytics & ROI
  // -------------------------------------------------------------------------

  async getAnalyticsDashboard(days = 30): Promise<AnalyticsDashboard> {
    try {
      const { data } = await this.client.get('/vanta/analytics/dashboard', { params: { days } });
      return data;
    } catch (error) {
      requireMockData('getAnalyticsDashboard', error);
      return buildMockAnalytics(days);
    }
  }

  // -------------------------------------------------------------------------
  // Whitelabel branding
  // -------------------------------------------------------------------------

  async getBranding(): Promise<Branding> {
    try {
      const { data } = await this.client.get('/vanta/whitelabel/branding');
      return data;
    } catch (error) {
      requireMockData('getBranding', error);
      return DEFAULT_BRANDING;
    }
  }

  async updateBranding(branding: Branding): Promise<Branding> {
    try {
      const { data } = await this.client.put('/vanta/whitelabel/branding', branding);
      return data;
    } catch (error) {
      requireMockData('updateBranding', error);
      return branding;
    }
  }

  async setCustomDomain(domain: string | null) {
    try {
      const { data } = await this.client.put('/vanta/whitelabel/domain', { domain });
      return data;
    } catch (error) {
      const failure = error as { response?: { data?: { error?: string } } };
      return { success: false, error: failure.response?.data?.error ?? 'Could not save the domain.' };
    }
  }

  // -------------------------------------------------------------------------
  // Platform admin (cross-tenant; requires PLATFORM_ADMIN)
  // -------------------------------------------------------------------------

  async getPlatformStats(): Promise<PlatformStats | null> {
    try {
      const { data } = await this.client.get('/platform/admin/stats');
      return data;
    } catch {
      return null;
    }
  }

  async getPlatformTenants(params?: { search?: string; status?: string }): Promise<TenantSummary[]> {
    try {
      const { data } = await this.client.get('/platform/admin/tenants', { params });
      return data;
    } catch {
      return [];
    }
  }

  async setTenantStatus(tenantId: string, status: string) {
    try {
      const { data } = await this.client.patch(`/platform/admin/tenants/${tenantId}/status`, { status });
      return { success: true, tenant: data };
    } catch (error) {
      const failure = error as { response?: { data?: { error?: string } } };
      return { success: false, error: failure.response?.data?.error ?? 'Could not update the workspace.' };
    }
  }

  async setTenantPlan(tenantId: string, plan: string, expiresAt?: string) {
    try {
      const { data } = await this.client.patch(`/platform/admin/tenants/${tenantId}/plan`, { plan, expiresAt });
      return { success: true, tenant: data };
    } catch (error) {
      const failure = error as { response?: { data?: { error?: string } } };
      return { success: false, error: failure.response?.data?.error ?? 'Could not update the plan.' };
    }
  }

  // -------------------------------------------------------------------------
  // Security Program Awareness
  // -------------------------------------------------------------------------

  async getProgramHealth(): Promise<ProgramHealth> {
    try {
      const { data } = await this.client.get('/vanta/program/health');
      return data;
    } catch (error) {
      requireMockData('getProgramHealth', error);
      return MOCK_PROGRAM_HEALTH;
    }
  }

  async getProgramCoverage(): Promise<CoverageSummary[]> {
    try {
      const { data } = await this.client.get('/vanta/program/coverage');
      return data;
    } catch (error) {
      requireMockData('getProgramCoverage', error);
      return MOCK_COVERAGE;
    }
  }

  async getProgramSignals(): Promise<ProgramSignal[]> {
    try {
      const { data } = await this.client.get('/vanta/program/signals');
      return data;
    } catch (error) {
      requireMockData('getProgramSignals', error);
      return MOCK_SIGNALS;
    }
  }

  async refreshProgram() {
    try {
      const { data } = await this.client.post('/vanta/program/refresh');
      return data;
    } catch (error) {
      const failure = error as { response?: { data?: { error?: string } } };
      return {
        success: false,
        error: failure.response?.data?.error ?? 'Could not reach the connectors.',
      };
    }
  }
}

export const api = new ApiClient();

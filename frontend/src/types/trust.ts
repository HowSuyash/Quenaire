export type TrustItemKind = 'CERTIFICATION' | 'DOCUMENT' | 'CONTROL' | 'FAQ';
export type TrustItemAccess = 'PUBLIC' | 'REQUEST_ONLY';

export interface TrustItem {
  id: string;
  kind: TrustItemKind;
  title: string;
  body?: string;
  icon?: string;
  issuer?: string;
  validFrom?: string;
  validUntil?: string;
  expired: boolean;
  accessLevel: TrustItemAccess;
  /** Null for REQUEST_ONLY items — the server withholds the link. */
  externalUrl?: string | null;
}

export interface TrustCta {
  enabled: boolean;
  heading?: string;
  body?: string;
  buttonLabel?: string;
}

export interface TrustProfile {
  slug: string;
  companyName: string;
  headline?: string;
  summary?: string;
  logoUrl?: string;
  primaryColor?: string;
  contactEmail?: string;
  securityPageUrl?: string;
  statusPageUrl?: string;
  publishedAt?: string;
  certifications: TrustItem[];
  documents: TrustItem[];
  controls: TrustItem[];
  faqs: TrustItem[];
  cta: TrustCta;
}

export interface TrustLeadPayload {
  name: string;
  email: string;
  company?: string;
  roleTitle?: string;
  message?: string;
  requestedItemId?: string;
  sourceUrl?: string;
}

export type TrustLeadStatus = 'NEW' | 'CONTACTED' | 'GRANTED' | 'DECLINED' | 'SPAM';

export interface TrustLead {
  id: string;
  name: string;
  email: string;
  company?: string;
  roleTitle?: string;
  message?: string;
  requestedItemId?: string;
  status: TrustLeadStatus;
  sourceUrl?: string;
  createdAt: string;
}

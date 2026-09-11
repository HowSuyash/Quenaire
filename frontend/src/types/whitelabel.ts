export interface Branding {
  productName: string;
  /** Hex only — the server rejects anything else, since this lands inside CSS. */
  primaryColor: string;
  accentColor: string;
  logoUrl?: string | null;
  faviconUrl?: string | null;
  supportEmail?: string | null;
  documentFooter?: string | null;
  hidePoweredBy: boolean;
  loginBackgroundUrl?: string | null;
  customCssVars?: string | null;
}

export const DEFAULT_BRANDING: Branding = {
  productName: 'Quenaire',
  primaryColor: '#2563EB',
  accentColor: '#7C3AED',
  logoUrl: null,
  faviconUrl: null,
  supportEmail: null,
  documentFooter: null,
  hidePoweredBy: false,
  loginBackgroundUrl: null,
  customCssVars: null,
};

export interface PublicBranding {
  tenantSlug: string;
  productName: string;
  primaryColor: string;
  accentColor: string;
  logoUrl?: string | null;
  faviconUrl?: string | null;
  loginBackgroundUrl?: string | null;
  hidePoweredBy: boolean;
}

export type TenantStatusValue = 'ACTIVE' | 'SUSPENDED' | 'TRIAL' | 'EXPIRED' | 'DELETED';
export type TenantPlanValue = 'FREE' | 'STARTER' | 'PROFESSIONAL' | 'ENTERPRISE' | 'CUSTOM';

export interface TenantSummary {
  id: string;
  name: string;
  slug: string;
  customDomain?: string | null;
  status: TenantStatusValue;
  plan: TenantPlanValue;
  planExpiresAt?: string | null;
  userCount: number;
  questionnaireCount: number;
  subscriptionStatus: string;
  createdAt: string;
}

export interface PlatformStats {
  totalTenants: number;
  activeTenants: number;
  trialTenants: number;
  suspendedTenants: number;
  totalUsers: number;
  totalQuestionnaires: number;
  tenantsByPlan: Record<string, number>;
  subscriptionsByStatus: Record<string, number>;
}

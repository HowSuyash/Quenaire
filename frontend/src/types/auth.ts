export interface SignupStatus {
  signupEnabled: boolean;
  /** False on instances where Keycloak has not been wired up yet. */
  identityProviderConfigured: boolean;
}

export interface RegisterPayload {
  companyName: string;
  firstName: string;
  lastName: string;
  email: string;
  password: string;
  workspaceSlug: string;
}

export interface RegisterResult {
  success: boolean;
  error?: string;
  tenantId?: string;
  userId?: string;
  workspaceSlug?: string;
  /** False means the account exists but sign-in must be finished out of band. */
  credentialsProvisioned?: boolean;
  message?: string;
}

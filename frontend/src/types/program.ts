export type SignalSource = 'AWS' | 'GITHUB' | 'JIRA' | 'CONFLUENCE' | 'MANUAL';
export type SignalStatus = 'PASSING' | 'WARNING' | 'FAILING' | 'INFORMATIONAL';
export type ControlHealth = SignalStatus | 'UNKNOWN';

export interface ProgramSignal {
  id: string;
  source: SignalSource;
  externalId: string;
  title: string;
  detail?: string | null;
  status: SignalStatus;
  category?: string | null;
  resourceType?: string | null;
  sourceUrl?: string | null;
  /** When the source system last reported this state — not when we polled. */
  observedAt: string;
}

export interface ControlCoverage {
  framework: string;
  controlId: string;
  controlTitle: string;
  category: string;
  covered: boolean;
  health: ControlHealth;
  evidenceCount: number;
  signalIds: string[];
}

export interface CoverageSummary {
  framework: string;
  totalControls: number;
  coveredControls: number;
  passingControls: number;
  failingControls: number;
  coveragePercent: number;
  controls: ControlCoverage[];
}

export interface ProgramHealth {
  passing: number;
  warning: number;
  failing: number;
  informational: number;
  bySource: Record<string, number>;
  staleCount: number;
  connectors: Record<string, boolean>;
}

export interface ProgramFact {
  signalId: string;
  source: SignalSource;
  title: string;
  detail?: string | null;
  status: SignalStatus;
  category?: string | null;
  observedAt: string;
  stale: boolean;
  controls: string[];
}

export interface AnalyticsTotals {
  questionnaires: number;
  questionnairesCompleted: number;
  questions: number;
  questionsAnswered: number;
  questionsApproved: number;
  questionsAutoApproved: number;
  questionsHumanEdited: number;
  /** Percent of answered questions where the AI draft survived review unedited. */
  automationRate: number;
  approvalRate: number;
}

export interface RoiAssumptions {
  manualMinutesPerQuestion: number;
  reviewMinutesPerQuestion: number;
  hourlyRate: number;
  currency: string;
}

export interface Roi {
  manualHours: number;
  actualHours: number;
  hoursSaved: number;
  costAvoided: number;
  savingsPercent: number;
  assumptions: RoiAssumptions;
}

export interface TurnaroundStats {
  medianHours: number | null;
  averageHours: number | null;
  fastestHours: number | null;
  slowestHours: number | null;
  sampleSize: number;
}

export interface TrendPoint {
  date: string;
  questionsAnswered: number;
  questionsApproved: number;
  hoursSaved: number;
}

export interface CategoryBreakdown {
  category: string;
  total: number;
  approved: number;
  averageConfidence: number;
}

export interface ConfidenceBand {
  band: string;
  count: number;
}

export interface SmeWorkload {
  smeUserId: string;
  assigned: number;
  completed: number;
  open: number;
  overdue: number;
  averageHoursToComplete: number | null;
}

export interface AnalyticsDashboard {
  totals: AnalyticsTotals;
  roi: Roi;
  turnaround: TurnaroundStats;
  trend: TrendPoint[];
  categories: CategoryBreakdown[];
  confidence: ConfidenceBand[];
  smeWorkload: SmeWorkload[];
}

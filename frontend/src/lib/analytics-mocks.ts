import { AnalyticsDashboard } from '@/types/analytics';

/**
 * Offline fixture for the analytics dashboard, matching the fallback pattern the
 * rest of `api.ts` uses so the page is demoable without a running backend.
 */
export function buildMockAnalytics(days: number): AnalyticsDashboard {
  const trend = Array.from({ length: days }, (_, i) => {
    const date = new Date(Date.now() - (days - 1 - i) * 86400000);
    // A weekday-shaped series — flat synthetic data reads as fake.
    const weekday = date.getDay();
    const base = weekday === 0 || weekday === 6 ? 2 : 9;
    const answered = Math.max(0, base + Math.round(Math.sin(i / 2.2) * 4));
    const approved = Math.max(0, Math.round(answered * 0.72));
    return {
      date: date.toISOString().slice(0, 10),
      questionsAnswered: answered,
      questionsApproved: approved,
      hoursSaved: Math.round(((answered * 17) / 60) * 100) / 100,
    };
  });

  const answered = trend.reduce((sum, p) => sum + p.questionsAnswered, 0);
  const approved = trend.reduce((sum, p) => sum + p.questionsApproved, 0);
  const humanEdited = Math.round(approved * 0.28);
  const autoApproved = approved - humanEdited;

  const manualHours = Math.round(((answered * 20) / 60) * 100) / 100;
  const actualHours =
    Math.round(((autoApproved * 3 + humanEdited * 13) / 60) * 100) / 100;
  const hoursSaved = Math.max(Math.round((manualHours - actualHours) * 100) / 100, 0);

  return {
    totals: {
      questionnaires: 14,
      questionnairesCompleted: 9,
      questions: answered + 46,
      questionsAnswered: answered,
      questionsApproved: approved,
      questionsAutoApproved: autoApproved,
      questionsHumanEdited: humanEdited,
      automationRate: answered ? Math.round(((autoApproved * 100) / answered) * 100) / 100 : 0,
      approvalRate: answered ? Math.round(((approved * 100) / answered) * 100) / 100 : 0,
    },
    roi: {
      manualHours,
      actualHours,
      hoursSaved,
      costAvoided: Math.round(hoursSaved * 95 * 100) / 100,
      savingsPercent: manualHours ? Math.round((hoursSaved / manualHours) * 10000) / 100 : 0,
      assumptions: {
        manualMinutesPerQuestion: 20,
        reviewMinutesPerQuestion: 3,
        hourlyRate: 95,
        currency: 'USD',
      },
    },
    turnaround: {
      medianHours: 6.4,
      averageHours: 11.2,
      fastestHours: 1.3,
      slowestHours: 52.8,
      sampleSize: 9,
    },
    trend,
    categories: [
      { category: 'Access Control', total: 42, approved: 35, averageConfidence: 91 },
      { category: 'Encryption', total: 31, approved: 29, averageConfidence: 94 },
      { category: 'Business Continuity', total: 24, approved: 18, averageConfidence: 86 },
      { category: 'Vulnerability Management', total: 22, approved: 14, averageConfidence: 74 },
      { category: 'Secure Development', total: 19, approved: 15, averageConfidence: 82 },
      { category: 'Vendor Risk', total: 16, approved: 9, averageConfidence: 68 },
      { category: 'Privacy', total: 12, approved: 8, averageConfidence: 77 },
    ],
    confidence: [
      { band: 'High (90+)', count: 78 },
      { band: 'Medium (70-89)', count: 54 },
      { band: 'Low (<70)', count: 23 },
      { band: 'Not scored', count: 11 },
    ],
    smeWorkload: [
      { smeUserId: 'usr-2', assigned: 24, completed: 21, open: 3, overdue: 0, averageHoursToComplete: 5.2 },
      { smeUserId: 'usr-3', assigned: 17, completed: 16, open: 1, overdue: 0, averageHoursToComplete: 8.9 },
      { smeUserId: 'usr-4', assigned: 19, completed: 13, open: 6, overdue: 2, averageHoursToComplete: 14.1 },
    ],
  };
}

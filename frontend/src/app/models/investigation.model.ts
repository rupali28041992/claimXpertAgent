import { AgentFinding } from './agent-finding.model';

export type FinalVerdict = 'APPROVED' | 'REJECTED' | 'HUMAN_REVIEW_REQUIRED';

export interface FinalDecision {
  verdict: FinalVerdict;
  confidenceScore: number;
  reasoning: string;
  recommendedAction: string;
  keyReasons: string[];
}

export interface InvestigationResponse {
  claimId: string;
  requirementFinding: AgentFinding;
  policyCoverageFinding: AgentFinding;
  investigationFinding: AgentFinding;
  finalDecision: FinalDecision;
  investigatedAt: string;
}

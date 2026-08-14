export interface AgentFinding {
  agentName: string;
  verdict: string;
  confidenceScore: number;
  evidence: string[];
  explanation: string;
}

/** Mirrors com.nextgen.claims.model.ClaimAnswer on the backend. */
export interface ClaimAnswerPayload {
  questionId: string;
  questionText: string;
  answerText: string;
}

/** Mirrors com.nextgen.claims.dto.ClaimSubmitRequest on the backend. */
export interface ClaimSubmitRequest {
  customerId: string;
  policyId: string;
  claimType: string;
  claimReason: string;
  freeText: string;
  answers: ClaimAnswerPayload[];
}

/** Mirrors com.nextgen.claims.dto.ClaimSubmitResponse on the backend. */
export interface ClaimSubmitResponse {
  claimId: string;
  readinessScore: number | null;
  flags: string[] | null;
  summary: string | null;
  status: string | null;
  fileErrors: string[] | null;
}

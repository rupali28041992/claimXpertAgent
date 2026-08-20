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

/** Mirrors com.nextgen.claims.docvalidation.model.DocumentResult - only file validation and OCR happen per document now. */
export interface DocumentResult {
  fileName: string;
  documentId: string;
  valid: boolean;
  errors: string[];
  ocrText: string | null;
  status: string;
}

/** Mirrors com.nextgen.claims.docvalidation.model.ClaimDecisionResult - Ollama's final call. */
export interface ClaimDecisionResult {
  decision: 'APPROVED' | 'REJECTED' | 'MANUAL_REVIEW';
  conditions: string[];
  matchedClauses: string[];
  confidence: number;
  reason: string;
}

/** Mirrors com.nextgen.claims.docvalidation.model.ClaimResult - what POST /api/claims/submit now returns. */
export interface ClaimResult {
  claimId: string;
  status: 'RECEIVED' | 'COMPLETED' | 'PARTIALLY_COMPLETED' | 'FAILED';
  documents: DocumentResult[];
  decision: ClaimDecisionResult | null;
}

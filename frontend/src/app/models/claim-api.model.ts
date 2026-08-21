export interface PolicyLookupResponse {
  policyId: string;
  customerId: string;
  claimType: string;
  policyholderName: string;
}

export interface ClaimQuestionDef {
  questionId: string;
  questionText: string;
  fieldType: string;
}

export interface ClaimTypeConfig {
  claimType: string;
  questions: ClaimQuestionDef[];
  requiredDocuments: string[];
}

export interface ClaimAnswerPayload {
  questionId: string;
  questionText: string;
  answerText: string;
}

/** Mirrors com.nextgen.claims.docvalidation.model.DocumentResult. */
export interface DocumentResult {
  fileName: string;
  documentId: string;
  valid: boolean;
  errors: string[];
  ocrText: string | null;
  status: 'COMPLETED' | 'FAILED';
}

/** Mirrors com.nextgen.claims.docvalidation.model.ClaimDecisionResult - Ollama's final call. */
export interface ClaimDecisionResult {
  decision: 'APPROVED' | 'REJECTED' | 'MANUAL_REVIEW';
  conditions: string[];
  matchedClauses: string[];
  confidence: number;
  reason: string;
}

/** Mirrors com.nextgen.claims.docvalidation.model.ClaimResult - what POST /api/claims/submit returns. */
export interface ClaimSubmitResponse {
  claimId: string;
  status: 'RECEIVED' | 'COMPLETED' | 'PARTIALLY_COMPLETED' | 'FAILED';
  documents: DocumentResult[];
  decision: ClaimDecisionResult | null;
}

export interface PolicyCreateRequest {
  policyNumber: string;
  customerId: string;
  claimType: string;
  policyholderName: string;
  sumInsured: number;
  startDate: string;
  endDate: string;
}

export interface PolicyRecord extends PolicyCreateRequest {
  active: boolean;
}

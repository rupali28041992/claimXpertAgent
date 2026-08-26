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
  errors: string[] | null;
  ocrText: string | null;
  status: 'COMPLETED' | 'FAILED';
}

/** Mirrors com.nextgen.claims.docvalidation.model.ClaimDecisionResult - Ollama's final call. */
export interface ClaimDecisionResult {
  decision: 'APPROVED' | 'REJECTED' | 'MANUAL_REVIEW';
  conditions: string[] | null;
  matchedClauses: string[] | null;
  confidence: number;
  reason: string;
  keyFindings?: string[];
  aiError?: boolean;
}

export type ClaimStatus = 'RECEIVED' | 'PROCESSING' | 'COMPLETED' | 'PARTIALLY_COMPLETED' | 'FAILED';

/** Mirrors both ClaimResult (202 response) and ClaimEntity (GET /api/claims/{id} response). */
export interface ClaimSubmitResponse {
  claimId: string;
  status: ClaimStatus;
  documents: DocumentResult[] | null;
  decision: ClaimDecisionResult | null;
  aiFailureReason?: string | null;
}

/** Full persisted claim document — mirrors ClaimEntity from the backend. */
export interface ClaimEntityResponse {
  claimId: string;
  claimType: string;
  claimReason: string;
  answers: Record<string, unknown>;
  documents: DocumentResult[] | null;
  decision: ClaimDecisionResult | null;
  status: ClaimStatus;
  aiFailureReason: string | null;
  createdAt: string;
  updatedAt: string;
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

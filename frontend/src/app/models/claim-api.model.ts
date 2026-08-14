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

export interface ClaimSubmitResponse {
  claimId: string;
  readinessScore: number | null;
  flags: string[] | null;
  summary: string | null;
  status: string | null;
  fileErrors: string[] | null;
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

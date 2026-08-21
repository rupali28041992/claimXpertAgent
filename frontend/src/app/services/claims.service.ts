import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { InvestigationResponse } from '../models/investigation.model';
import { ClaimSubmitResponse } from '../models/claim-api.model';

export interface PolicyVerifyResponse {
  valid: boolean;
  policyId: string;
  holderName?: string;
  status?: string;
}

export interface FieldOption {
  value: string;
  label: string;
}

/** Matches Angular's FormField interface so the component can use these directly. */
export interface QuestionField {
  id: string;
  type: 'text' | 'textarea' | 'radio' | 'dropdown' | 'date' | 'file';
  label: string;
  required?: boolean;
  placeholder?: string;
  options?: FieldOption[];
  accept?: string;
  multiple?: boolean;
}

export interface DocumentCategory {
  type: string;
  description?: string;
}

export interface QuestionnaireState {
  questions: QuestionField[];
  isComplete: boolean;
  claimType: string | null;
  claimReason: string | null;
  requiredDocuments?: DocumentCategory[];
}

@Injectable({ providedIn: 'root' })
export class ClaimsService {

  private readonly base = environment.apiBaseUrl;

  constructor(private http: HttpClient) {}

  /** GoRules-driven question engine. Call after every answered field. */
  getNextQuestions(answers: Record<string, string>): Observable<QuestionnaireState> {
    return this.http.post<QuestionnaireState>(`${this.base}/claims/questions`, { answers });
  }

  submit(formData: FormData): Observable<ClaimSubmitResponse> {
    return this.http.post<ClaimSubmitResponse>(`${this.base}/claims/submit`, formData);
  }

  investigate(claimId: string): Observable<InvestigationResponse> {
    return this.http.post<InvestigationResponse>(
      `${this.base}/claims/investigate/${claimId}`, {}
    );
  }

  getInvestigation(claimId: string): Observable<InvestigationResponse> {
    return this.http.get<InvestigationResponse>(
      `${this.base}/claims/investigate/${claimId}`
    );
  }

  verifyPolicy(policyId: string): Observable<PolicyVerifyResponse> {
    return this.http.get<PolicyVerifyResponse>(
      `${this.base}/policies/${policyId}/verify`
    );
  }
}

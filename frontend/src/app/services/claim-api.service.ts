import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  ClaimSubmitResponse,
  ClaimTypeConfig,
  PolicyLookupResponse
} from '../models/claim-api.model';

const API_BASE_URL = 'http://localhost:8080/api/claims';

@Injectable({ providedIn: 'root' })
export class ClaimApiService {

  constructor(private http: HttpClient) {}

  lookupPolicy(policyNumber: string): Observable<PolicyLookupResponse> {
    return this.http.get<PolicyLookupResponse>(`${API_BASE_URL}/policy/${policyNumber}`);
  }

  getConfig(claimType: string): Observable<ClaimTypeConfig> {
    return this.http.get<ClaimTypeConfig>(`${API_BASE_URL}/config/${claimType}`);
  }

  submit(claim: object, files: File[]): Observable<ClaimSubmitResponse> {
    const formData = new FormData();
    formData.append('claim', JSON.stringify(claim));
    files.forEach(file => formData.append('files', file));
    return this.http.post<ClaimSubmitResponse>(`${API_BASE_URL}/submit`, formData);
  }
}

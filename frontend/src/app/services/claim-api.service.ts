import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { ClaimSubmitRequest, ClaimSubmitResponse } from '../models/claim-api.model';

/**
 * Talks to the existing com.nextgen.claims.controller.ClaimController -
 * POST /api/claims/submit expects a multipart request with a "claim" JSON
 * part and one or more "files" parts, exactly as built in submit() below.
 */
@Injectable({ providedIn: 'root' })
export class ClaimApiService {

  private readonly base = `${environment.apiBaseUrl}/claims`;

  constructor(private http: HttpClient) {}

  submit(claim: ClaimSubmitRequest, files: File[]): Observable<ClaimSubmitResponse> {
    const formData = new FormData();
    formData.append('claim', new Blob([JSON.stringify(claim)], { type: 'application/json' }));
    files.forEach(file => formData.append('files', file, file.name));
    return this.http.post<ClaimSubmitResponse>(`${this.base}/submit`, formData);
  }
}

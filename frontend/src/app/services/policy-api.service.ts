import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { PolicyCreateRequest, PolicyRecord } from '../models/claim-api.model';

const API_BASE_URL = 'http://localhost:8080/api/policies';

@Injectable({ providedIn: 'root' })
export class PolicyApiService {

  constructor(private http: HttpClient) {}

  create(policy: PolicyCreateRequest): Observable<PolicyRecord> {
    return this.http.post<PolicyRecord>(API_BASE_URL, policy);
  }
}

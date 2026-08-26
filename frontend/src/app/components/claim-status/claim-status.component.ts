import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ClaimApiService } from '../../services/claim-api.service';
import { ClaimEntityResponse } from '../../models/claim-api.model';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-claim-status',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './claim-status.component.html',
  styleUrls: ['./claim-status.component.scss']
})
export class ClaimStatusComponent implements OnInit {

  claims: ClaimEntityResponse[] = [];
  selectedClaim: ClaimEntityResponse | null = null;
  searchId = '';
  loadingList = false;
  loadingDetail = false;
  listError = '';
  searchError = '';

  constructor(private api: ClaimApiService, private authService: AuthService) {}

  ngOnInit(): void {
    const saved = localStorage.getItem('lastClaimId');
    if (saved) this.searchId = saved;
    this.loadAllClaims();
  }

  loadAllClaims(): void {
    this.loadingList = true;
    this.listError = '';
    const customerId = this.authService.currentUser?.customerId;
    const claims$ = customerId
      ? this.api.getClaimsByCustomer(customerId)
      : this.api.getAllClaims();

    claims$.subscribe({
      next: claims => {
        this.claims = claims;
        this.loadingList = false;
        if (this.searchId) {
          const found = claims.find(c => c.claimId === this.searchId);
          if (found) this.selectedClaim = found;
        }
      },
      error: () => {
        this.listError = 'Unable to reach the backend. Is the server running?';
        this.loadingList = false;
      }
    });
  }

  search(): void {
    const id = this.searchId.trim();
    if (!id) return;
    this.searchError = '';
    this.loadingDetail = true;
    this.api.getClaim(id).subscribe({
      next: claim => {
        this.selectedClaim = claim;
        this.loadingDetail = false;
        if (!this.claims.find(c => c.claimId === claim.claimId)) {
          this.claims.unshift(claim);
        }
      },
      error: () => {
        this.searchError = `No claim found with ID "${id}"`;
        this.loadingDetail = false;
        this.selectedClaim = null;
      }
    });
  }

  select(claim: ClaimEntityResponse): void {
    this.selectedClaim = claim;
    this.searchId = claim.claimId;
    this.searchError = '';
  }

  statusLabel(status: string): string {
    const map: Record<string, string> = {
      RECEIVED: 'Received',
      PROCESSING: 'Processing',
      COMPLETED: 'Completed',
      PARTIALLY_COMPLETED: 'Partially Completed',
      FAILED: 'Failed'
    };
    return map[status] ?? status;
  }

  statusClass(status: string): string {
    if (status === 'COMPLETED') return 'status--completed';
    if (status === 'RECEIVED' || status === 'PROCESSING') return 'status--processing';
    if (status === 'FAILED') return 'status--failed';
    return 'status--partial';
  }

  decisionClass(decision: string): string {
    if (decision === 'APPROVED') return 'verdict--approved';
    if (decision === 'REJECTED') return 'verdict--rejected';
    return 'verdict--review';
  }

  decisionLabel(decision: string): string {
    if (decision === 'APPROVED') return '✓ Approved';
    if (decision === 'REJECTED') return '✗ Rejected';
    return '⟳ Manual Review';
  }

  confidencePct(claim: ClaimEntityResponse): number {
    return Math.round((claim.decision?.confidence ?? 0) * 100);
  }

  answersEntries(answers: Record<string, unknown>): [string, string][] {
    return Object.entries(answers ?? {}).map(([k, v]) => [k, String(v)]);
  }

  formatDate(iso: string): string {
    if (!iso) return '—';
    return new Date(iso).toLocaleString('en-IN', {
      dateStyle: 'medium', timeStyle: 'short'
    });
  }
}

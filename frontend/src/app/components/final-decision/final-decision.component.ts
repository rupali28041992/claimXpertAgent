import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { ClaimsService } from '../../services/claims.service';
import { InvestigationResponse } from '../../models/investigation.model';

@Component({
  selector: 'app-final-decision',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './final-decision.component.html',
  styleUrls: ['./final-decision.component.scss']
})
export class FinalDecisionComponent implements OnInit {
  investigation: InvestigationResponse | null = null;
  isLoading = true;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private claimsService: ClaimsService
  ) {}

  ngOnInit(): void {
    const navState = history.state as { investigation?: InvestigationResponse };
    if (navState?.investigation) {
      this.investigation = navState.investigation;
      this.isLoading = false;
    } else {
      const claimId = this.route.snapshot.paramMap.get('claimId') ?? '';
      this.claimsService.getInvestigation(claimId).subscribe({
        next: data => { this.investigation = data; this.isLoading = false; },
        error: () => { this.isLoading = false; }
      });
    }
  }

  get verdictCssClass(): string {
    switch (this.investigation?.finalDecision?.verdict) {
      case 'APPROVED':              return 'verdict-approved';
      case 'REJECTED':              return 'verdict-rejected';
      case 'HUMAN_REVIEW_REQUIRED': return 'verdict-review';
      default:                      return '';
    }
  }

  get verdictLabel(): string {
    switch (this.investigation?.finalDecision?.verdict) {
      case 'APPROVED':              return 'Approved';
      case 'REJECTED':              return 'Rejected';
      case 'HUMAN_REVIEW_REQUIRED': return 'Human Review Required';
      default:                      return 'Pending';
    }
  }

  get confidencePercent(): number {
    return Math.round((this.investigation?.finalDecision?.confidenceScore ?? 0) * 100);
  }

  fileNewClaim(): void {
    this.router.navigate(['/']);
  }

  agentItems(): Array<{ title: string; finding: any }> {
    if (!this.investigation) return [];
    return [
      { title: 'Requirements',    finding: this.investigation.requirementFinding },
      { title: 'Policy Coverage', finding: this.investigation.policyCoverageFinding },
      { title: 'Investigation',   finding: this.investigation.investigationFinding }
    ];
  }
}

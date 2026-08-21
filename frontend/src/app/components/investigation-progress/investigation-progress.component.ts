import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { ClaimsService } from '../../services/claims.service';
import { InvestigationResponse } from '../../models/investigation.model';
import { AgentFinding } from '../../models/agent-finding.model';

interface AgentCard {
  label: string;
  description: string;
  status: 'running' | 'done' | 'error';
  finding?: AgentFinding;
}

@Component({
  selector: 'app-investigation-progress',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './investigation-progress.component.html',
  styleUrls: ['./investigation-progress.component.scss']
})
export class InvestigationProgressComponent implements OnInit {
  claimId = '';
  isLoading = true;
  error: string | null = null;

  agents: AgentCard[] = [
    { label: 'Requirement Agent',      description: 'Checking document completeness…',     status: 'running' },
    { label: 'Policy Coverage Agent',  description: 'Analysing policy coverage via RAG…',  status: 'running' },
    { label: 'Investigation Agent',    description: 'Cross-checking answers vs documents…', status: 'running' }
  ];

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private claimsService: ClaimsService
  ) {}

  ngOnInit(): void {
    this.claimId = this.route.snapshot.paramMap.get('claimId') ?? '';
    this.claimsService.investigate(this.claimId).subscribe({
      next: (response: InvestigationResponse) => {
        this.isLoading = false;
        this.revealAgents(response);
      },
      error: () => {
        this.isLoading = false;
        this.error = 'Investigation failed. Please try again or contact support.';
        this.agents.forEach(a => a.status = 'error');
      }
    });
  }

  private revealAgents(response: InvestigationResponse): void {
    const findings = [
      response.requirementFinding,
      response.policyCoverageFinding,
      response.investigationFinding
    ];
    findings.forEach((finding, i) => {
      setTimeout(() => {
        this.agents[i] = { ...this.agents[i], status: 'done', finding };
        if (i === findings.length - 1) {
          setTimeout(() => {
            this.router.navigate(['/decision', this.claimId], {
              state: { investigation: response }
            });
          }, 1000);
        }
      }, 300 + i * 400);
    });
  }

  verdictIcon(verdict: string): string {
    if (!verdict) return '?';
    if (verdict.includes('INCOMPLETE') || verdict.includes('NOT_COVERED') || verdict.includes('SUSPICIOUS')) return '✗';
    if (verdict.includes('UNCERTAIN') || verdict.includes('INCONSISTENT')) return '⚠';
    return '✓';
  }

  verdictClass(verdict: string): string {
    if (!verdict) return '';
    if (verdict.includes('INCOMPLETE') || verdict.includes('NOT_COVERED') || verdict.includes('SUSPICIOUS')) return 'bad';
    if (verdict.includes('UNCERTAIN') || verdict.includes('INCONSISTENT')) return 'warn';
    return 'good';
  }
}

package com.nextgen.claims.service;

import com.nextgen.claims.agent.FinalJudgeAgent;
import com.nextgen.claims.agent.InvestigationAgent;
import com.nextgen.claims.agent.PolicyCoverageAgent;
import com.nextgen.claims.agent.RequirementAgent;
import com.nextgen.claims.dto.AgentFinding;
import com.nextgen.claims.dto.FinalDecision;
import com.nextgen.claims.dto.InvestigationResponse;
import com.nextgen.claims.model.Claim;
import com.nextgen.claims.model.ClaimInvestigation;
import com.nextgen.claims.repository.ClaimInvestigationRepository;
import com.nextgen.claims.repository.ClaimRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Service
public class ClaimOrchestrationService {

    private final ClaimRepository claimRepository;
    private final ClaimInvestigationRepository investigationRepository;
    private final RequirementAgent requirementAgent;
    private final PolicyCoverageAgent policyCoverageAgent;
    private final InvestigationAgent investigationAgent;
    private final FinalJudgeAgent finalJudgeAgent;
    private final Executor agentExecutor;

    public ClaimOrchestrationService(
            ClaimRepository claimRepository,
            ClaimInvestigationRepository investigationRepository,
            RequirementAgent requirementAgent,
            PolicyCoverageAgent policyCoverageAgent,
            InvestigationAgent investigationAgent,
            FinalJudgeAgent finalJudgeAgent,
            @Qualifier("agentExecutor") Executor agentExecutor) {
        this.claimRepository = claimRepository;
        this.investigationRepository = investigationRepository;
        this.requirementAgent = requirementAgent;
        this.policyCoverageAgent = policyCoverageAgent;
        this.investigationAgent = investigationAgent;
        this.finalJudgeAgent = finalJudgeAgent;
        this.agentExecutor = agentExecutor;
    }

    public InvestigationResponse investigate(String claimId) {
        Claim claim = claimRepository.findById(claimId)
                .orElseThrow(() -> new IllegalArgumentException("Claim not found: " + claimId));

        // Fire all three agents in parallel
        CompletableFuture<AgentFinding> reqFuture =
                CompletableFuture.supplyAsync(() -> requirementAgent.analyze(claim), agentExecutor);
        CompletableFuture<AgentFinding> covFuture =
                CompletableFuture.supplyAsync(() -> policyCoverageAgent.analyze(claim), agentExecutor);
        CompletableFuture<AgentFinding> invFuture =
                CompletableFuture.supplyAsync(() -> investigationAgent.analyze(claim), agentExecutor);

        CompletableFuture.allOf(reqFuture, covFuture, invFuture).join();

        AgentFinding requirementFinding    = reqFuture.join();
        AgentFinding policyCoverageFinding = covFuture.join();
        AgentFinding investigationFinding  = invFuture.join();

        FinalDecision finalDecision = finalJudgeAgent.synthesize(
                requirementFinding, policyCoverageFinding, investigationFinding);

        Instant now = Instant.now();
        ClaimInvestigation investigation = ClaimInvestigation.builder()
                .claimId(claimId)
                .requirementFinding(requirementFinding)
                .policyCoverageFinding(policyCoverageFinding)
                .investigationFinding(investigationFinding)
                .finalDecision(finalDecision)
                .investigatedAt(now)
                .build();
        investigationRepository.save(investigation);

        return toResponse(investigation);
    }

    public InvestigationResponse getInvestigation(String claimId) {
        return investigationRepository.findByClaimId(claimId)
                .map(this::toResponse)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Investigation not found for claim: " + claimId));
    }

    private InvestigationResponse toResponse(ClaimInvestigation inv) {
        return InvestigationResponse.builder()
                .claimId(inv.getClaimId())
                .requirementFinding(inv.getRequirementFinding())
                .policyCoverageFinding(inv.getPolicyCoverageFinding())
                .investigationFinding(inv.getInvestigationFinding())
                .finalDecision(inv.getFinalDecision())
                .investigatedAt(inv.getInvestigatedAt())
                .build();
    }
}

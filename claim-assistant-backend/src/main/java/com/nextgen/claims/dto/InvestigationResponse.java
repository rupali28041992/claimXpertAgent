package com.nextgen.claims.dto;

import lombok.*;
import java.time.Instant;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class InvestigationResponse {
    private String claimId;
    private AgentFinding requirementFinding;
    private AgentFinding policyCoverageFinding;
    private AgentFinding investigationFinding;
    private FinalDecision finalDecision;
    private Instant investigatedAt;
}

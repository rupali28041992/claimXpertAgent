package com.nextgen.claims.model;

import com.nextgen.claims.dto.AgentFinding;
import com.nextgen.claims.dto.FinalDecision;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
@Document(collection = "claim_investigations")
public class ClaimInvestigation {

    @Id
    private String id;

    @Indexed(unique = true)
    private String claimId;

    private AgentFinding requirementFinding;
    private AgentFinding policyCoverageFinding;
    private AgentFinding investigationFinding;
    private FinalDecision finalDecision;
    private Instant investigatedAt;
}

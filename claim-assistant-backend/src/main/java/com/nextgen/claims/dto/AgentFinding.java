package com.nextgen.claims.dto;

import lombok.*;
import java.util.List;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class AgentFinding {
    private String agentName;
    private String verdict;
    private double confidenceScore;
    private List<String> evidence;
    private String explanation;
}

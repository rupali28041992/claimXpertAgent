package com.nextgen.claims.dto;

import lombok.*;
import java.util.List;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class FinalDecision {
    private String verdict;
    private double confidenceScore;
    private String reasoning;
    private String recommendedAction;
    private List<String> keyReasons;
}

package com.nextgen.claims.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** One answer from the Screen 3 dynamic form. Nested inside Claim.answers. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClaimAnswer {
    private String questionId;
    private String questionText;
    private String answerText;
}

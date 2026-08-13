package com.nextgen.claims.agent;

import java.util.List;

/** Structured JSON the model is forced to return - Step 2c output. */
public record ValidationResult(
        List<String> flags,
        boolean clauseSatisfied,
        String explanation
) {
}

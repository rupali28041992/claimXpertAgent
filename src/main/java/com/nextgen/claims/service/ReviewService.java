package com.nextgen.claims.service;

import com.nextgen.claims.model.Claim;
import com.nextgen.claims.model.ClaimStatus;
import com.nextgen.claims.model.StatusChange;
import com.nextgen.claims.repository.ClaimRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;

/** Adjuster console actions - human-driven status updates on an UNDER_REVIEW claim. No AI. */
@Service
@RequiredArgsConstructor
public class ReviewService {

    private final ClaimRepository claimRepository;

    public Claim approve(String claimId, String adjusterId) {
        Claim claim = claimRepository.findById(claimId)
                .orElseThrow(() -> new IllegalArgumentException("Claim not found: " + claimId));

        appendStatus(claim, ClaimStatus.APPROVED, "Approved by adjuster", adjusterId);

        return claimRepository.save(claim);
    }

    public Claim reject(String claimId, String adjusterId, String reason) {
        Claim claim = claimRepository.findById(claimId)
                .orElseThrow(() -> new IllegalArgumentException("Claim not found: " + claimId));

        appendStatus(claim, ClaimStatus.REJECTED, reason, adjusterId);
        return claimRepository.save(claim);
    }

    private void appendStatus(Claim claim, ClaimStatus status, String reason, String changedBy) {
        claim.setStatus(status);
        if (claim.getStatusHistory() == null) {
            claim.setStatusHistory(new ArrayList<>());
        }
        claim.getStatusHistory().add(new StatusChange(status, Instant.now(), reason, changedBy));
    }
}

package com.nextgen.claims.docvalidation.agent;

import com.nextgen.claims.docvalidation.model.ClaimEntity;
import com.nextgen.claims.docvalidation.repository.ClaimEntityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class ClaimStatusAgent {

    private final ClaimEntityRepository repository;

    public List<ClaimEntity> getAllClaims() {
        log.info("[ClaimStatusAgent] fetching all claims ordered by date");
        return repository.findAllByOrderByCreatedAtDesc();
    }

    public Optional<ClaimEntity> getClaimById(String claimId) {
        log.info("[ClaimStatusAgent] fetching claim={}", claimId);
        return repository.findById(claimId);
    }
}

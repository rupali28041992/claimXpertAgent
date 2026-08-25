package com.nextgen.claims.docvalidation.scheduler;

import com.nextgen.claims.docvalidation.model.ClaimEntity;
import com.nextgen.claims.docvalidation.model.ClaimProcessingStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class StuckClaimWatchdog {

    private final MongoTemplate mongoTemplate;

    /** Mark PROCESSING claims older than 5 minutes as FAILED. */
    @Scheduled(fixedDelay = 60_000)
    public void failStuckClaims() {
        Instant cutoff = Instant.now().minus(5, ChronoUnit.MINUTES);

        Query query = new Query(
                Criteria.where("status").in(
                        ClaimProcessingStatus.PROCESSING.name(),
                        ClaimProcessingStatus.RECEIVED.name())
                        .and("updatedAt").lt(cutoff));

        Update update = new Update()
                .set("status", ClaimProcessingStatus.FAILED)
                .set("aiFailureReason", "Processing timed out after 5 minutes")
                .set("updatedAt", Instant.now());

        var result = mongoTemplate.updateMulti(query, update, ClaimEntity.class);

        if (result.getModifiedCount() > 0) {
            log.warn("[StuckClaimWatchdog] Marked {} stuck claim(s) as FAILED", result.getModifiedCount());
        }
    }
}

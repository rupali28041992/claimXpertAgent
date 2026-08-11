package com.nextgen.claims.controller;

import com.nextgen.claims.model.Claim;
import com.nextgen.claims.service.ReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** Adjuster console APIs - human review of UNDER_REVIEW claims. No AI here. */
@RestController
@RequestMapping("/api/review")
@RequiredArgsConstructor
@CrossOrigin(origins = "${claims.cors.allowed-origin:http://localhost:4200}")
public class ReviewController {

    private final ReviewService reviewService;

    public record ApproveRequest(String adjusterId) {
    }

    public record RejectRequest(String adjusterId, String reason) {
    }

    @PostMapping("/{claimId}/approve")
    public Claim approve(@PathVariable String claimId, @RequestBody ApproveRequest request) {
        return reviewService.approve(claimId, request.adjusterId());
    }

    @PostMapping("/{claimId}/reject")
    public Claim reject(@PathVariable String claimId, @RequestBody RejectRequest request) {
        return reviewService.reject(claimId, request.adjusterId(), request.reason());
    }
}

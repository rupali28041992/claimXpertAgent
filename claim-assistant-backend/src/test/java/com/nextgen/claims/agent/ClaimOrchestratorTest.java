package com.nextgen.claims.agent;

import com.nextgen.claims.dto.ClaimDecision;
import com.nextgen.claims.dto.ClaimRequest;
import com.nextgen.claims.dto.DocumentResult;
import com.nextgen.claims.model.Claim;
import com.nextgen.claims.model.ClaimStatus;
import com.nextgen.claims.model.PolicyClauseVector;
import com.nextgen.claims.repository.ClaimRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ClaimOrchestratorTest {

    private ClaimRepository claimRepository;
    private DocumentAgent documentAgent;
    private PolicyRagAgent policyRagAgent;
    private ValidationAgent validationAgent;
    private ClaimDecisionAgent claimDecisionAgent;
    private ClaimOrchestrator orchestrator;

    private final MultipartFile relevantFile = new MockMultipartFile("files", "discharge.pdf", "application/pdf", new byte[]{1});
    private final MultipartFile irrelevantFile = new MockMultipartFile("files", "car.pdf", "application/pdf", new byte[]{2});
    private final MultipartFile invalidFile = new MockMultipartFile("files", "empty.pdf", "application/pdf", new byte[0]);

    private final List<PolicyClauseVector> clauses = List.of(
            PolicyClauseVector.builder().productType("HEALTH").section("Section 4.1").clauseText("...").build());

    @BeforeEach
    void setUp() {
        claimRepository = Mockito.mock(ClaimRepository.class);
        documentAgent = Mockito.mock(DocumentAgent.class);
        policyRagAgent = Mockito.mock(PolicyRagAgent.class);
        validationAgent = Mockito.mock(ValidationAgent.class);
        claimDecisionAgent = Mockito.mock(ClaimDecisionAgent.class);
        orchestrator = new ClaimOrchestrator(claimRepository, documentAgent, policyRagAgent, validationAgent, claimDecisionAgent);

        when(policyRagAgent.findClauses(anyString(), anyString())).thenReturn(clauses);
        when(claimRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(claimDecisionAgent.decide(anyString(), anyList())).thenReturn(
                ClaimDecision.builder().verdict("AUTO_APPROVED").confidenceScore(0.9)
                        .reasoning("all good").recommendedAction("pay out").keyReasons(List.of("ok"))
                        .build());
    }

    @Test
    void onlyValidAndRelevantDocumentsReachValidationAgent_oneRagLookupPerClaim() {
        when(documentAgent.process(eq(relevantFile), anyString(), anyString(), anyString(), anyMap()))
                .thenReturn(DocumentResult.builder().documentId("doc_1").fileName("discharge.pdf")
                        .valid(true).relevant(true).ocrText("Apollo Hospital discharge summary")
                        .errors(List.of()).status("RELEVANT").build());
        when(documentAgent.process(eq(irrelevantFile), anyString(), anyString(), anyString(), anyMap()))
                .thenReturn(DocumentResult.builder().documentId("doc_2").fileName("car.pdf")
                        .valid(true).relevant(false).errors(List.of("UNRELATED_DOCUMENT")).status("IRRELEVANT").build());
        when(documentAgent.process(eq(invalidFile), anyString(), anyString(), anyString(), anyMap()))
                .thenReturn(DocumentResult.builder().documentId("doc_3").fileName("empty.pdf")
                        .valid(false).relevant(false).errors(List.of("FILE_EMPTY")).status("FAILED").build());

        var validationResult = new ValidationResult(List.of(), "APPROVE", List.of(), "looks fine", 0.9);
        when(validationAgent.validate(any(), anyString(), anyString(), anyString(), anyMap(), anyList()))
                .thenReturn(validationResult);

        ClaimRequest request = new ClaimRequest();
        request.setClaimType("HEALTH");
        request.setClaimReason("HOSPITALIZATION");
        request.setAnswers(Map.of("hospitalName", "Apollo Hospital"));

        var result = orchestrator.process(request, List.of(relevantFile, irrelevantFile, invalidFile));

        assertThat(result.getClaimId()).startsWith("clm_");
        assertThat(result.getStatus()).isEqualTo("COMPLETED"); // no OCR_FAILED among the 3 -> COMPLETED
        assertThat(result.getDocuments()).hasSize(3);

        // ValidationAgent called exactly once - only for the valid+relevant document.
        verify(validationAgent, times(1)).validate(any(), anyString(), anyString(), anyString(), anyMap(), anyList());
        // Policy RAG lookup happens exactly once per claim, not once per document.
        verify(policyRagAgent, times(1)).findClauses(anyString(), anyString());

        ArgumentCaptor<Claim> savedClaim = ArgumentCaptor.forClass(Claim.class);
        verify(claimRepository).save(savedClaim.capture());
        assertThat(savedClaim.getValue().getProcessingStatus()).isEqualTo("COMPLETED");
        assertThat(savedClaim.getValue().getDocuments()).hasSize(3);

        // ClaimDecisionAgent runs exactly once per claim, and its verdict becomes Claim.status.
        verify(claimDecisionAgent, times(1)).decide(anyString(), anyList());
        assertThat(savedClaim.getValue().getStatus()).isEqualTo(ClaimStatus.AUTO_APPROVED);
        assertThat(savedClaim.getValue().getFinalDecision().getVerdict()).isEqualTo("AUTO_APPROVED");
        assertThat(result.getFinalDecision().getVerdict()).isEqualTo("AUTO_APPROVED");
    }

    @Test
    void unrecognizedVerdictStringDefaultsToUnderReviewInsteadOfCrashing() {
        when(documentAgent.process(any(), anyString(), anyString(), anyString(), anyMap()))
                .thenReturn(DocumentResult.builder().documentId("doc_1").fileName("x.pdf")
                        .valid(true).relevant(true).ocrText("text").errors(List.of()).status("RELEVANT").build());
        when(validationAgent.validate(any(), anyString(), anyString(), anyString(), anyMap(), anyList()))
                .thenReturn(new ValidationResult(List.of(), "APPROVE", List.of(), "ok", 0.9));
        when(claimDecisionAgent.decide(anyString(), anyList())).thenReturn(
                ClaimDecision.builder().verdict("NOT_A_REAL_STATUS").confidenceScore(0.5).build());

        ClaimRequest request = new ClaimRequest();
        request.setClaimType("HEALTH");
        request.setClaimReason("HOSPITALIZATION");
        request.setAnswers(Map.of());

        orchestrator.process(request, List.of(relevantFile));

        ArgumentCaptor<Claim> savedClaim = ArgumentCaptor.forClass(Claim.class);
        verify(claimRepository).save(savedClaim.capture());
        assertThat(savedClaim.getValue().getStatus()).isEqualTo(ClaimStatus.UNDER_REVIEW);
    }

    @Test
    void allDocumentsOcrFailedMeansClaimFailed() {
        when(documentAgent.process(any(), anyString(), anyString(), anyString(), anyMap()))
                .thenReturn(DocumentResult.builder().documentId("doc_1").fileName("x.pdf")
                        .valid(true).relevant(false).errors(List.of("OCR_FAILED")).status("FAILED").build());

        ClaimRequest request = new ClaimRequest();
        request.setClaimType("HEALTH");
        request.setClaimReason("HOSPITALIZATION");
        request.setAnswers(Map.of());

        var result = orchestrator.process(request, List.of(relevantFile));

        assertThat(result.getStatus()).isEqualTo("FAILED");
        Mockito.verifyNoInteractions(validationAgent);
    }

    @Test
    void mixOfOcrFailureAndSuccessIsPartiallyCompleted() {
        when(documentAgent.process(eq(relevantFile), anyString(), anyString(), anyString(), anyMap()))
                .thenReturn(DocumentResult.builder().documentId("doc_1").fileName("discharge.pdf")
                        .valid(true).relevant(true).ocrText("Apollo Hospital").errors(List.of()).status("RELEVANT").build());
        when(documentAgent.process(eq(irrelevantFile), anyString(), anyString(), anyString(), anyMap()))
                .thenReturn(DocumentResult.builder().documentId("doc_2").fileName("scan.pdf")
                        .valid(true).relevant(false).errors(List.of("OCR_FAILED")).status("FAILED").build());
        when(validationAgent.validate(any(), anyString(), anyString(), anyString(), anyMap(), anyList()))
                .thenReturn(new ValidationResult(List.of(), "APPROVE", List.of(), "ok", 0.9));

        ClaimRequest request = new ClaimRequest();
        request.setClaimType("HEALTH");
        request.setClaimReason("HOSPITALIZATION");
        request.setAnswers(Map.of());

        var result = orchestrator.process(request, List.of(relevantFile, irrelevantFile));

        assertThat(result.getStatus()).isEqualTo("PARTIALLY_COMPLETED");
    }
}

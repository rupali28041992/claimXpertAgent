package com.nextgen.claims.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nextgen.claims.dto.ClaimResult;
import com.nextgen.claims.dto.DocumentResult;
import com.nextgen.claims.model.Claim;
import com.nextgen.claims.repository.ClaimRepository;
import com.nextgen.claims.support.TestPdfBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * REAL end-to-end test of POST /api/claims - hits the actual ClaimOrchestrator
 * pipeline (file validation -> OCR via PDFBox -> DocumentRelevanceAgent's real
 * embedding call -> PolicyRagAgent -> ValidationAgent's real Ollama call) and
 * persists to the real `claims` collection.
 *
 * Requirements to run this class (same as running the app itself):
 *   - MongoDB reachable at the URI in application.yml
 *   - `ollama serve` running locally with `qwen2.5:3b` and `nomic-embed-text` pulled
 *     (`ollama pull qwen2.5:3b`, `ollama pull nomic-embed-text`)
 *
 * File validation and OCR text extraction are deterministic and asserted
 * strictly (no live-model variance). Relevance/validation verdicts come from
 * the real model, so those assertions are intentionally loose to avoid
 * flakiness from wording differences between model runs/versions.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class ClaimControllerEndToEndTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ClaimRepository claimRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void matchingHospitalDischargeSummaryIsOcrdAndFlowsThroughTheWholePipeline() throws Exception {
        byte[] pdf = TestPdfBuilder.withText(
                "APOLLO HOSPITAL",
                "Discharge Summary",
                "Patient Name: John Doe",
                "Admission Date: 2026-07-10",
                "Diagnosis: Acute Appendicitis",
                "Patient was admitted for 3 days and discharged in stable condition.");
        MockMultipartFile file = new MockMultipartFile("files", "discharge.pdf", "application/pdf", pdf);
        String answers = "{\"hospitalName\":\"Apollo Hospital\",\"patientName\":\"John Doe\",\"admissionDate\":\"2026-07-10\"}";

        MvcResult result = mockMvc.perform(multipart("/api/claims")
                        .file(file)
                        .param("claimType", "MEDICAL")
                        .param("claimReason", "HOSPITALIZATION")
                        .param("answers", answers))
                .andExpect(status().isOk())
                .andReturn();

        ClaimResult claimResult = objectMapper.readValue(result.getResponse().getContentAsString(), ClaimResult.class);
        assertThat(claimResult.getClaimId()).startsWith("clm_");
        assertThat(claimResult.getDocuments()).hasSize(1);

        DocumentResult doc = claimResult.getDocuments().get(0);
        assertThat(doc.isValid()).isTrue(); // deterministic - FileValidationService
        assertThat(doc.getOcrText()).contains("Apollo Hospital", "John Doe", "2026-07-10"); // deterministic - PDFBox
        assertThat(doc.getDecisionSource()).isIn("RULE", "VECTOR", "OLLAMA"); // came from the real relevance pipeline

        // ClaimDecisionAgent always produces exactly one claim-level verdict, even if no
        // document made it to ValidationAgent (then it's UNDER_REVIEW without calling Ollama).
        assertThat(claimResult.getFinalDecision()).isNotNull();
        assertThat(claimResult.getFinalDecision().getVerdict())
                .isIn("AUTO_APPROVED", "AUTO_REJECTED", "UNDER_REVIEW");

        Claim saved = claimRepository.findById(claimResult.getClaimId()).orElseThrow();
        assertThat(saved.getProcessingStatus()).isEqualTo(claimResult.getStatus());
        assertThat(saved.getDocuments()).hasSize(1);
        assertThat(saved.getDocuments().get(0).getOcrText()).contains("Apollo Hospital");
        assertThat(saved.getStatus().name()).isEqualTo(claimResult.getFinalDecision().getVerdict());
        assertThat(saved.getFinalDecision().getVerdict()).isEqualTo(claimResult.getFinalDecision().getVerdict());

        claimRepository.deleteById(claimResult.getClaimId()); // don't leave test data behind
    }

    @Test
    void clearlyUnrelatedDocumentNeverReachesValidationAgent() throws Exception {
        byte[] pdf = TestPdfBuilder.withText(
                "VEHICLE REGISTRATION CERTIFICATE",
                "Chassis Number: XYZ123456",
                "Engine Number: ABC987654",
                "Vehicle Class: Motor Car",
                "Registered Owner: Jane Smith");
        MockMultipartFile file = new MockMultipartFile("files", "vehicle.pdf", "application/pdf", pdf);

        MvcResult result = mockMvc.perform(multipart("/api/claims")
                        .file(file)
                        .param("claimType", "MEDICAL")
                        .param("claimReason", "HOSPITALIZATION")
                        .param("answers", "{}"))
                .andExpect(status().isOk())
                .andReturn();

        ClaimResult claimResult = objectMapper.readValue(result.getResponse().getContentAsString(), ClaimResult.class);
        DocumentResult doc = claimResult.getDocuments().get(0);

        assertThat(doc.isRelevant()).isFalse();
        assertThat(doc.getErrors()).contains("UNRELATED_DOCUMENT");
        assertThat(doc.getValidationResult()).isNull(); // ValidationAgent must never run for an irrelevant document

        // No document was validated, so ClaimDecisionAgent must route to human review
        // deterministically, without itself calling Ollama.
        assertThat(claimResult.getFinalDecision().getVerdict()).isEqualTo("UNDER_REVIEW");

        claimRepository.deleteById(claimResult.getClaimId());
    }

    @Test
    void emptyFileIsRejectedDeterministicallyWithoutTouchingOcrOrOllama() throws Exception {
        MockMultipartFile empty = new MockMultipartFile("files", "empty.pdf", "application/pdf", new byte[0]);

        MvcResult result = mockMvc.perform(multipart("/api/claims")
                        .file(empty)
                        .param("claimType", "MEDICAL")
                        .param("claimReason", "HOSPITALIZATION")
                        .param("answers", "{}"))
                .andExpect(status().isOk())
                .andReturn();

        ClaimResult claimResult = objectMapper.readValue(result.getResponse().getContentAsString(), ClaimResult.class);
        DocumentResult doc = claimResult.getDocuments().get(0);

        assertThat(doc.isValid()).isFalse();
        assertThat(doc.getErrors()).containsExactly("FILE_EMPTY");
        assertThat(doc.getOcrText()).isNull(); // OCR never ran

        claimRepository.deleteById(claimResult.getClaimId());
    }
}

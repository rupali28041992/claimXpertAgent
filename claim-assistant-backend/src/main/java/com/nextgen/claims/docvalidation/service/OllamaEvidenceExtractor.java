package com.nextgen.claims.docvalidation.service;

import com.nextgen.claims.docvalidation.model.DocumentEvidence;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OllamaEvidenceExtractor {

    private final OllamaService ollamaService;

    /**
     * Returns true when the regex extraction left critical decision-relevant
     * fields empty and an Ollama fallback call is warranted.
     */
    public boolean needsFallback(DocumentEvidence evidence, String claimType) {
        if (evidence == null) return true;
        // Always fall back if document type is still generic — means regex didn't recognise the format.
        // A generic type means Ollama decision agent won't know what kind of document this is,
        // which directly causes "Discharge summary: NOT FOUND" even when one was uploaded.
        if (isGenericDocumentType(evidence.getDocumentType())) return true;
        return switch (claimType == null ? "" : claimType.toUpperCase()) {
            case "MEDICAL" -> evidence.getDiagnosis() == null && evidence.getBillAmount() == null;
            case "MOTOR"   -> evidence.getDiagnosis() == null && evidence.getBillAmount() == null;
            case "TRAVEL"  -> evidence.getBillAmount() == null && evidence.getPatientName() == null;
            case "LIFE"    -> evidence.getDiagnosis() == null;
            default        -> evidence.getDiagnosis() == null && evidence.getBillAmount() == null;
        };
    }

    private boolean isGenericDocumentType(String documentType) {
        return documentType == null
                || documentType.equalsIgnoreCase("MEDICAL_DOCUMENT")
                || documentType.equalsIgnoreCase("UNKNOWN");
    }

    /**
     * Calls Ollama to extract structured fields from raw OCR text.
     * Only invoked when regex left critical fields null.
     * Merges back into partial evidence — regex values are never overwritten.
     */
    public DocumentEvidence extract(String ocrText, String claimType, DocumentEvidence partial) {
        String prompt = buildPrompt(ocrText, claimType);
        log.info("[OllamaEvidenceExtractor] claimType={} ocrChars={} calling Ollama for extraction",
                claimType, ocrText == null ? 0 : ocrText.length());

        try {
            DocumentEvidence extracted = ollamaService.generateStructured(prompt, DocumentEvidence.class);
            if (extracted == null) return partial;
            DocumentEvidence merged = merge(partial, extracted);
            log.info("[OllamaEvidenceExtractor] merged evidence: docType={} diagnosis={} billAmount={}",
                    merged.getDocumentType(), merged.getDiagnosis(), merged.getBillAmount());
            return merged;
        } catch (OllamaServiceException e) {
            log.warn("[OllamaEvidenceExtractor] extraction failed code={} — keeping regex result", e.getCode());
            return partial;
        }
    }

    private String buildPrompt(String ocrText, String claimType) {
        return """
                /no_think
                Extract structured fields from the insurance document OCR text below.
                Return ONLY a JSON object. Use null for any field not found. Do not guess or invent values.

                Claim type: %s

                FIELD DEFINITIONS:
                documentType  — DISCHARGE_SUMMARY | HOSPITAL_BILL | PRESCRIPTION | DIAGNOSTIC_REPORT | DEATH_CERTIFICATE | FIR | REPAIR_INVOICE | BOARDING_PASS | MEDICAL_CERTIFICATE | OTHER
                patientName   — full name of patient or insured person
                hospitalName  — name of hospital, garage, airline, or service provider
                diagnosis     — medical diagnosis, cause of death, vehicle damage description, or flight disruption reason
                treatment     — procedure, repair work, or service rendered
                billAmount    — total billed amount with currency symbol (e.g. Rs.45000, $1200)
                admissionDate — date of admission, incident, or departure (any format found in text)
                dischargeDate — date of discharge, repair completion, or return (any format found in text)
                policyNumber  — policy number if visible in document
                claimNumber   — claim/reference number if visible in document

                OCR TEXT:
                %s

                Output ONLY this JSON with values filled in (null where not found):
                {"documentType":null,"patientName":null,"hospitalName":null,"diagnosis":null,"treatment":null,"billAmount":null,"admissionDate":null,"dischargeDate":null,"policyNumber":null,"claimNumber":null}
                """
                .formatted(claimType, truncate(ocrText, 2000));
    }

    /**
     * Merge strategy: keep any non-null value from regex (partial),
     * fill only null slots from Ollama (extracted).
     */
    private DocumentEvidence merge(DocumentEvidence partial, DocumentEvidence extracted) {
        return DocumentEvidence.builder()
                .documentType( firstNonNull(partial.getDocumentType(),  extracted.getDocumentType()))
                .patientName(  firstNonNull(partial.getPatientName(),   extracted.getPatientName()))
                .hospitalName( firstNonNull(partial.getHospitalName(),  extracted.getHospitalName()))
                .diagnosis(    firstNonNull(partial.getDiagnosis(),     extracted.getDiagnosis()))
                .treatment(    firstNonNull(partial.getTreatment(),     extracted.getTreatment()))
                .billAmount(   firstNonNull(partial.getBillAmount(),    extracted.getBillAmount()))
                .admissionDate(firstNonNull(partial.getAdmissionDate(), extracted.getAdmissionDate()))
                .dischargeDate(firstNonNull(partial.getDischargeDate(), extracted.getDischargeDate()))
                .policyNumber( firstNonNull(partial.getPolicyNumber(),  extracted.getPolicyNumber()))
                .claimNumber(  firstNonNull(partial.getClaimNumber(),   extracted.getClaimNumber()))
                .build();
    }

    private String firstNonNull(String a, String b) {
        return a != null ? a : b;
    }

    private String truncate(String text, int maxChars) {
        if (text == null) return "";
        return text.length() > maxChars ? text.substring(0, maxChars) : text;
    }
}

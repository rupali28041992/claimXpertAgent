package com.nextgen.claims.docvalidation.service;

import com.nextgen.claims.docvalidation.model.DocumentEvidence;
import org.springframework.stereotype.Service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class DocumentEvidenceExtractor {

    public DocumentEvidence extract(String claimType, String ocrText) {

        if (ocrText == null || ocrText.isBlank()) {
            return DocumentEvidence.builder()
                    .documentType(claimType)
                    .build();
        }

        return switch (claimType == null ? "" : claimType.toUpperCase()) {
            case "MEDICAL" -> extractMedical(ocrText);
            case "MOTOR" -> extractMotor(ocrText);
            case "TRAVEL" -> extractTravel(ocrText);
            case "LIFE" -> extractLife(ocrText);
            default -> extractGeneric(ocrText);
        };
    }

    private DocumentEvidence extractMedical(String text) {

        return DocumentEvidence.builder()
                .documentType(detectMedicalDocumentType(text))
                .patientName(firstMatch(text,
                        "Patient Name",
                        "Patient",
                        "Name of Patient"))
                .hospitalName(firstMatch(text,
                        "Hospital Name",
                        "Hospital",
                        "Hospital Name & Address"))
                .diagnosis(firstMatch(text,
                        "Final Diagnosis",
                        "Diagnosis",
                        "Diagnosed With"))
                .admissionDate(firstMatch(text,
                        "Admission Date",
                        "Date of Admission",
                        "Admitted On"))
                .dischargeDate(firstMatch(text,
                        "Discharge Date",
                        "Date of Discharge",
                        "Discharged On"))
                .treatment(firstMatch(text,
                        "Treatment",
                        "Procedure",
                        "Treatment Given"))
                .billAmount(firstMatch(text,
                        "Total Amount",
                        "Net Amount",
                        "Bill Amount",
                        "Total Bill"))
                .policyNumber(firstMatch(text,
                        "Policy Number",
                        "Policy No",
                        "Policy No."))
                .claimNumber(firstMatch(text,
                        "Claim Number",
                        "Claim No",
                        "Claim No."))
                .build();
    }

    private DocumentEvidence extractMotor(String text) {

        return DocumentEvidence.builder()
                .documentType(detectDocumentType(text,
                        "vehicle", "accident", "repair", "damage"))
                .patientName(null)
                .hospitalName(null)
                .diagnosis(firstMatch(text,
                        "Damage",
                        "Description of Damage",
                        "Accident Description"))
                .admissionDate(null)
                .dischargeDate(null)
                .treatment(firstMatch(text,
                        "Repair",
                        "Repair Details",
                        "Work Done"))
                .billAmount(firstMatch(text,
                        "Total Amount",
                        "Repair Amount",
                        "Invoice Amount"))
                .policyNumber(firstMatch(text,
                        "Policy Number",
                        "Policy No"))
                .claimNumber(firstMatch(text,
                        "Claim Number",
                        "Claim No"))
                .build();
    }

    private DocumentEvidence extractTravel(String text) {

        return DocumentEvidence.builder()
                .documentType(detectDocumentType(text,
                        "flight",
                        "boarding pass",
                        "airline",
                        "itinerary"))
                .hospitalName(null)
                .diagnosis(null)
                .admissionDate(null)
                .dischargeDate(null)
                .treatment(null)
                .patientName(firstMatch(text,
                        "Passenger Name",
                        "Passenger",
                        "Name"))
                .billAmount(firstMatch(text,
                        "Total Amount",
                        "Fare",
                        "Amount"))
                .policyNumber(firstMatch(text,
                        "Policy Number",
                        "Policy No"))
                .claimNumber(firstMatch(text,
                        "Claim Number",
                        "Claim No"))
                .build();
    }

    private DocumentEvidence extractLife(String text) {

        return DocumentEvidence.builder()
                .documentType(detectDocumentType(text,
                        "death certificate",
                        "deceased",
                        "nominee"))
                .patientName(firstMatch(text,
                        "Name of Deceased",
                        "Deceased Name",
                        "Name"))
                .diagnosis(firstMatch(text,
                        "Cause of Death",
                        "Cause"))
                .policyNumber(firstMatch(text,
                        "Policy Number",
                        "Policy No"))
                .claimNumber(firstMatch(text,
                        "Claim Number",
                        "Claim No"))
                .build();
    }

    private DocumentEvidence extractGeneric(String text) {

        return DocumentEvidence.builder()
                .documentType("UNKNOWN")
                .patientName(firstMatch(text,
                        "Patient Name",
                        "Passenger Name",
                        "Name"))
                .hospitalName(firstMatch(text,
                        "Hospital Name",
                        "Hospital"))
                .diagnosis(firstMatch(text,
                        "Diagnosis",
                        "Description"))
                .admissionDate(firstMatch(text,
                        "Admission Date",
                        "Date of Admission"))
                .dischargeDate(firstMatch(text,
                        "Discharge Date",
                        "Date of Discharge"))
                .treatment(firstMatch(text,
                        "Treatment",
                        "Procedure"))
                .billAmount(firstMatch(text,
                        "Total Amount",
                        "Net Amount",
                        "Amount"))
                .policyNumber(firstMatch(text,
                        "Policy Number",
                        "Policy No"))
                .claimNumber(firstMatch(text,
                        "Claim Number",
                        "Claim No"))
                .build();
    }

    private String detectMedicalDocumentType(String text) {

        String lower = text.toLowerCase();

        if (containsAny(lower, "discharge summary", "discharge date")) {
            return "DISCHARGE_SUMMARY";
        }

        if (containsAny(lower, "hospital bill", "bill amount", "total amount")) {
            return "HOSPITAL_BILL";
        }

        if (containsAny(lower, "prescription", "prescribed")) {
            return "PRESCRIPTION";
        }

        if (containsAny(lower, "diagnostic report", "laboratory report",
                "lab report", "test result")) {
            return "DIAGNOSTIC_REPORT";
        }

        if (containsAny(lower, "medical certificate")) {
            return "MEDICAL_CERTIFICATE";
        }

        return "MEDICAL_DOCUMENT";
    }

    private String detectDocumentType(String text, String... keywords) {

        String lower = text.toLowerCase();

        for (String keyword : keywords) {
            if (lower.contains(keyword.toLowerCase())) {
                return keyword.toUpperCase().replace(" ", "_");
            }
        }

        return "UNKNOWN";
    }

    private boolean containsAny(String text, String... values) {

        for (String value : values) {
            if (text.contains(value.toLowerCase())) {
                return true;
            }
        }

        return false;
    }

    private String firstMatch(String text, String... labels) {

        for (String label : labels) {

            Pattern pattern = Pattern.compile(
                    "(?im)^\\s*" +
                            Pattern.quote(label) +
                            "\\s*[:\\-]?\\s*(.+?)\\s*$"
            );

            Matcher matcher = pattern.matcher(text);

            if (matcher.find()) {

                String value = matcher.group(1).trim();

                if (!value.isBlank()) {
                    return cleanValue(value);
                }
            }
        }

        return null;
    }

    private String cleanValue(String value) {

        if (value.length() > 250) {
            return value.substring(0, 250);
        }

        return value;
    }
}
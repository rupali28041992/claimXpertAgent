package com.nextgen.claims.docvalidation.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentEvidence {

    private String documentType;

    private String patientName;

    private String hospitalName;

    private String diagnosis;

    private String admissionDate;

    private String dischargeDate;

    private String treatment;

    private String billAmount;

    private String policyNumber;

    private String claimNumber;
}
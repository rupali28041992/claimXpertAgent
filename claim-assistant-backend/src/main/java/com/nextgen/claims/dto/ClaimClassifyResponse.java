package com.nextgen.claims.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * Output of POST /api/claims/classify.
 * Fields are populated directly from the GoRules questionnaire.json decision graph:
 *   switchNode  → routes by policyType
 *   decisionTableNode → resolves claimType + claimReason
 *   decisionTableNode → resolves baseDocuments
 *   expressionNode    → enriches requiredDocuments based on flags
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ClaimClassifyResponse {

    private String policyType;
    private String incidentType;
    private Boolean injuryInvolved;
    private Boolean thirdPartyInvolved;
    private Boolean policeReportFiled;

    /** Derived by GoRules — e.g. "property_damage", "bodily_injury", "medical", "trip_disruption" */
    private String claimType;

    /** Human-readable reason — e.g. "collision", "theft", "storm_damage" */
    private String claimReason;

    /**
     * Final required-document list after flag-based enrichment.
     * e.g. ["claim_form", "photo_evidence", "repair_estimate", "police_report"]
     */
    private List<String> requiredDocuments;

    public String getPolicyType() { return policyType; }
    public void setPolicyType(String policyType) { this.policyType = policyType; }

    public String getIncidentType() { return incidentType; }
    public void setIncidentType(String incidentType) { this.incidentType = incidentType; }

    public Boolean getInjuryInvolved() { return injuryInvolved; }
    public void setInjuryInvolved(Boolean injuryInvolved) { this.injuryInvolved = injuryInvolved; }

    public Boolean getThirdPartyInvolved() { return thirdPartyInvolved; }
    public void setThirdPartyInvolved(Boolean thirdPartyInvolved) { this.thirdPartyInvolved = thirdPartyInvolved; }

    public Boolean getPoliceReportFiled() { return policeReportFiled; }
    public void setPoliceReportFiled(Boolean policeReportFiled) { this.policeReportFiled = policeReportFiled; }

    public String getClaimType() { return claimType; }
    public void setClaimType(String claimType) { this.claimType = claimType; }

    public String getClaimReason() { return claimReason; }
    public void setClaimReason(String claimReason) { this.claimReason = claimReason; }

    public List<String> getRequiredDocuments() { return requiredDocuments; }
    public void setRequiredDocuments(List<String> requiredDocuments) { this.requiredDocuments = requiredDocuments; }
}

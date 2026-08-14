package com.nextgen.claims.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Input to POST /api/claims/classify.
 * Evaluated by the GoRules questionnaire.json decision graph.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ClaimClassifyRequest {

    /** Policy type: auto | home | health | travel */
    private String policyType;

    /** Incident type — valid values depend on the policyType branch in questionnaire.json */
    private String incidentType;

    /** Optional flags that influence required-document enrichment */
    private Boolean injuryInvolved;
    private Boolean thirdPartyInvolved;
    private Boolean policeReportFiled;

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
}

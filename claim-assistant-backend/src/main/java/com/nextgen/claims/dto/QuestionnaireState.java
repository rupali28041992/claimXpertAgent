package com.nextgen.claims.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Returned by POST /api/claims/questions.
 * GoRules populates all fields from question-definitions.json; no Java logic here.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class QuestionnaireState {

    private List<QuestionField> questions;
    private boolean isComplete;
    private String claimType;
    private String claimReason;
    private List<DocumentCategory> requiredDocuments;

    public List<QuestionField> getQuestions() { return questions; }
    public void setQuestions(List<QuestionField> questions) { this.questions = questions; }

    @JsonProperty("isComplete")
    public boolean isComplete() { return isComplete; }

    @JsonProperty("isComplete")
    public void setComplete(boolean complete) { isComplete = complete; }

    public String getClaimType() { return claimType; }
    public void setClaimType(String claimType) { this.claimType = claimType; }

    public String getClaimReason() { return claimReason; }
    public void setClaimReason(String claimReason) { this.claimReason = claimReason; }

    public List<DocumentCategory> getRequiredDocuments() { return requiredDocuments; }
    public void setRequiredDocuments(List<DocumentCategory> requiredDocuments) { this.requiredDocuments = requiredDocuments; }

    /** Mirrors the Angular FormField interface so the component can use these directly. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class QuestionField {
        private String id;
        private String type;
        private String label;
        private Boolean required;
        private String placeholder;
        private List<FieldOption> options;
        private String accept;
        private Boolean multiple;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getLabel() { return label; }
        public void setLabel(String label) { this.label = label; }
        public Boolean getRequired() { return required; }
        public void setRequired(Boolean required) { this.required = required; }
        public String getPlaceholder() { return placeholder; }
        public void setPlaceholder(String placeholder) { this.placeholder = placeholder; }
        public List<FieldOption> getOptions() { return options; }
        public void setOptions(List<FieldOption> options) { this.options = options; }
        public String getAccept() { return accept; }
        public void setAccept(String accept) { this.accept = accept; }
        public Boolean getMultiple() { return multiple; }
        public void setMultiple(Boolean multiple) { this.multiple = multiple; }
    }

    public static class FieldOption {
        private String value;
        private String label;

        public String getValue() { return value; }
        public void setValue(String value) { this.value = value; }
        public String getLabel() { return label; }
        public void setLabel(String label) { this.label = label; }
    }

    /** Document category shown in the upload UI — driven entirely by question-definitions.json. */
    public static class DocumentCategory {
        private String type;
        private String description;

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
    }
}

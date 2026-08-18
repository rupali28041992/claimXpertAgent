package com.nextgen.claims.docvalidation.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Configurable properties for this module only (Section 41 of the spec) -
 * distinct prefix "docvalidation" so nothing here collides with the
 * existing "claims.*" properties used by the live /api/claims/submit flow.
 */
@Configuration
@ConfigurationProperties(prefix = "docvalidation")
@Data
public class DocValidationProperties {

    private Document document = new Document();
    private Relevance relevance = new Relevance();
    private Mongodb mongodb = new Mongodb();
    private Rag rag = new Rag();
    private Decision decision = new Decision();
    private Ingestion ingestion = new Ingestion();

    @Data
    public static class Document {
        private long maxFileSize = 10 * 1024 * 1024; // 10 MB
        private List<String> allowedMimeTypes = List.of("application/pdf", "image/jpeg", "image/png");
    }

    @Data
    public static class Relevance {
        private double highThreshold = 0.80;
        private double lowThreshold = 0.60;
    }

    @Data
    public static class Mongodb {
        private String policyVectorIndex = "policy_clause_vector_index";
    }

    @Data
    public static class Rag {
        private int topK = 4;
    }

    @Data
    public static class Decision {
        private int maxOcrCharsPerDoc = 4000;
    }

    @Data
    public static class Ingestion {
        private boolean enabled = false;
        private String medicalPolicyPath = "logs/RAG_IngestionPolicies/Medical_Insurance_Policy_ClaimXpert.pdf";
    }
}

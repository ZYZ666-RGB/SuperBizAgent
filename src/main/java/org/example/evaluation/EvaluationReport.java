package org.example.evaluation;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class EvaluationReport {

    private String reportId;
    private Instant generatedAt;
    private Instant refreshedAt;
    private String mode;
    private String overallStatus;
    private double overallScore;
    private String summary;
    private EnvironmentSnapshot environment = new EnvironmentSnapshot();
    private QualityGateSnapshot qualityGate = new QualityGateSnapshot();
    private List<ModelEvaluation> modelEvaluations = new ArrayList<>();
    private RagEvaluation ragEvaluation = new RagEvaluation();
    private MemoryEvaluation memoryEvaluation = new MemoryEvaluation();
    private List<String> recommendations = new ArrayList<>();

    @Getter
    @Setter
    public static class EnvironmentSnapshot {
        private boolean dashScopeApiKeyConfigured;
        private boolean databaseReachable;
        private long ragDocumentCount;
        private long ragChunkCount;
        private long enabledMemoryCount;
        private String message;
    }

    @Getter
    @Setter
    public static class QualityGateSnapshot {
        private String status;
        private String reportPath;
        private int testsRun;
        private int failures;
        private int errors;
        private int skipped;
        private String message;
    }

    @Getter
    @Setter
    public static class ModelEvaluation {
        private String model;
        private String status;
        private double score;
        private long latencyMs;
        private String responsePreview;
        private String errorMessage;
        private List<CheckResult> checks = new ArrayList<>();
    }

    @Getter
    @Setter
    public static class RagEvaluation {
        private String namespace;
        private String status;
        private double score;
        private String seedDocumentId;
        private String seedStatus;
        private long documentCount;
        private long chunkCount;
        private RagCaseResult answerableCase = new RagCaseResult();
        private RagCaseResult refusalCase = new RagCaseResult();
        private List<CheckResult> checks = new ArrayList<>();
        private String message;
    }

    @Getter
    @Setter
    public static class RagCaseResult {
        private String caseId;
        private String query;
        private String status;
        private double score;
        private String answerPreview;
        private Boolean supported;
        private Double confidence;
        private int citationCount;
        private int usedChunkCount;
        private int denseHitCount;
        private int sparseHitCount;
        private int rerankCount;
        private long totalTimeMs;
        private List<String> usedChunkIds = new ArrayList<>();
        private List<String> citationFiles = new ArrayList<>();
        private List<CheckResult> checks = new ArrayList<>();
    }

    @Getter
    @Setter
    public static class MemoryEvaluation {
        private String status;
        private double score;
        private String userId;
        private String otherUserId;
        private String marker;
        private String memoryId;
        private long enabledMemoryCount;
        private List<CheckResult> checks = new ArrayList<>();
        private String message;
    }

    @Getter
    @Setter
    public static class CheckResult {
        private String name;
        private String status;
        private double score;
        private String message;
        private String evidence;
        private long latencyMs;
    }
}

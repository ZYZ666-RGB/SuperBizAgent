package org.example.memory;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "memory")
public class MemoryProperties {

    private boolean enabled = true;
    private String appId = "super_biz_agent";
    private int windowSize = 6;
    private int summaryThreshold = 12;
    private LongTerm longTerm = new LongTerm();
    private Vector vector = new Vector();
    private Graph graph = new Graph();
    private Consolidation consolidation = new Consolidation();
    private Forgetting forgetting = new Forgetting();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    public int getWindowSize() {
        return windowSize;
    }

    public void setWindowSize(int windowSize) {
        this.windowSize = windowSize;
    }

    public int getSummaryThreshold() {
        return summaryThreshold;
    }

    public void setSummaryThreshold(int summaryThreshold) {
        this.summaryThreshold = summaryThreshold;
    }

    public int getRecentMessageLimit() {
        return Math.max(1, windowSize) * 2;
    }

    public LongTerm getLongTerm() {
        return longTerm;
    }

    public void setLongTerm(LongTerm longTerm) {
        this.longTerm = longTerm;
    }

    public Vector getVector() {
        return vector;
    }

    public void setVector(Vector vector) {
        this.vector = vector;
    }

    public Graph getGraph() {
        return graph;
    }

    public void setGraph(Graph graph) {
        this.graph = graph;
    }

    public Consolidation getConsolidation() {
        return consolidation;
    }

    public void setConsolidation(Consolidation consolidation) {
        this.consolidation = consolidation;
    }

    public Forgetting getForgetting() {
        return forgetting;
    }

    public void setForgetting(Forgetting forgetting) {
        this.forgetting = forgetting;
    }

    public static class LongTerm {
        private boolean enabled = true;
        private int topK = 5;
        private double minConfidence = 0.75;
        private double minImportance = 0.6;
        private double minEvidenceScore = 0.8;
        private double minStabilityScore = 0.6;
        private double minFutureUsefulnessScore = 0.7;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getTopK() {
            return topK;
        }

        public void setTopK(int topK) {
            this.topK = topK;
        }

        public double getMinConfidence() {
            return minConfidence;
        }

        public void setMinConfidence(double minConfidence) {
            this.minConfidence = minConfidence;
        }

        public double getMinImportance() {
            return minImportance;
        }

        public void setMinImportance(double minImportance) {
            this.minImportance = minImportance;
        }

        public double getMinEvidenceScore() {
            return minEvidenceScore;
        }

        public void setMinEvidenceScore(double minEvidenceScore) {
            this.minEvidenceScore = minEvidenceScore;
        }

        public double getMinStabilityScore() {
            return minStabilityScore;
        }

        public void setMinStabilityScore(double minStabilityScore) {
            this.minStabilityScore = minStabilityScore;
        }

        public double getMinFutureUsefulnessScore() {
            return minFutureUsefulnessScore;
        }

        public void setMinFutureUsefulnessScore(double minFutureUsefulnessScore) {
            this.minFutureUsefulnessScore = minFutureUsefulnessScore;
        }
    }

    public static class Vector {
        private boolean enabled = true;
        private String collectionName = "user_memory";
        private int dimension = 1024;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getCollectionName() {
            return collectionName;
        }

        public void setCollectionName(String collectionName) {
            this.collectionName = collectionName;
        }

        public int getDimension() {
            return dimension;
        }

        public void setDimension(int dimension) {
            this.dimension = dimension;
        }
    }

    public static class Graph {
        private boolean enabled = true;
        private String neo4jUri = "bolt://localhost:7687";
        private String username = "neo4j";
        private String password = "neo4j1234";
        private int topK = 20;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getNeo4jUri() {
            return neo4jUri;
        }

        public void setNeo4jUri(String neo4jUri) {
            this.neo4jUri = neo4jUri;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public int getTopK() {
            return topK;
        }

        public void setTopK(int topK) {
            this.topK = topK;
        }
    }

    public static class Consolidation {
        private boolean enabled = true;
        private double importanceThreshold = 0.7;
        private int maxSourceMemories = 5;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public double getImportanceThreshold() {
            return importanceThreshold;
        }

        public void setImportanceThreshold(double importanceThreshold) {
            this.importanceThreshold = importanceThreshold;
        }

        public int getMaxSourceMemories() {
            return maxSourceMemories;
        }

        public void setMaxSourceMemories(int maxSourceMemories) {
            this.maxSourceMemories = maxSourceMemories;
        }
    }

    public static class Forgetting {
        private boolean enabled = true;
        private int maxAgeDays = 30;
        private double minImportance = 0.2;
        private int maxMemoriesPerUser = 500;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getMaxAgeDays() {
            return maxAgeDays;
        }

        public void setMaxAgeDays(int maxAgeDays) {
            this.maxAgeDays = maxAgeDays;
        }

        public double getMinImportance() {
            return minImportance;
        }

        public void setMinImportance(double minImportance) {
            this.minImportance = minImportance;
        }

        public int getMaxMemoriesPerUser() {
            return maxMemoriesPerUser;
        }

        public void setMaxMemoriesPerUser(int maxMemoriesPerUser) {
            this.maxMemoriesPerUser = maxMemoriesPerUser;
        }
    }
}

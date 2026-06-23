package org.example.context;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "context.engineering")
public class ContextConfig {

    private int maxTokens = 6000;
    private int reservedOutputTokens = 1200;
    private int maxHistoryTokens = 1000;
    private int maxMemoryTokens = 1200;
    private int maxRagTokens = 2500;
    private int maxToolResultTokens = 1000;
    private double minRelevance = 0.2;
    private double relevanceWeight = 0.65;
    private double recencyWeight = 0.2;
    private double importanceWeight = 0.15;
    private boolean enableCompression = true;
    private int recentRounds = 6;
    private int memoryTopK = 8;
    private int ragTopK = 6;

    public int inputBudgetTokens() {
        return Math.max(1000, maxTokens - reservedOutputTokens);
    }

    public int getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(int maxTokens) {
        this.maxTokens = maxTokens;
    }

    public int getReservedOutputTokens() {
        return reservedOutputTokens;
    }

    public void setReservedOutputTokens(int reservedOutputTokens) {
        this.reservedOutputTokens = reservedOutputTokens;
    }

    public int getMaxHistoryTokens() {
        return maxHistoryTokens;
    }

    public void setMaxHistoryTokens(int maxHistoryTokens) {
        this.maxHistoryTokens = maxHistoryTokens;
    }

    public int getMaxMemoryTokens() {
        return maxMemoryTokens;
    }

    public void setMaxMemoryTokens(int maxMemoryTokens) {
        this.maxMemoryTokens = maxMemoryTokens;
    }

    public int getMaxRagTokens() {
        return maxRagTokens;
    }

    public void setMaxRagTokens(int maxRagTokens) {
        this.maxRagTokens = maxRagTokens;
    }

    public int getMaxToolResultTokens() {
        return maxToolResultTokens;
    }

    public void setMaxToolResultTokens(int maxToolResultTokens) {
        this.maxToolResultTokens = maxToolResultTokens;
    }

    public double getMinRelevance() {
        return minRelevance;
    }

    public void setMinRelevance(double minRelevance) {
        this.minRelevance = minRelevance;
    }

    public double getRelevanceWeight() {
        return relevanceWeight;
    }

    public void setRelevanceWeight(double relevanceWeight) {
        this.relevanceWeight = relevanceWeight;
    }

    public double getRecencyWeight() {
        return recencyWeight;
    }

    public void setRecencyWeight(double recencyWeight) {
        this.recencyWeight = recencyWeight;
    }

    public double getImportanceWeight() {
        return importanceWeight;
    }

    public void setImportanceWeight(double importanceWeight) {
        this.importanceWeight = importanceWeight;
    }

    public boolean isEnableCompression() {
        return enableCompression;
    }

    public void setEnableCompression(boolean enableCompression) {
        this.enableCompression = enableCompression;
    }

    public int getRecentRounds() {
        return recentRounds;
    }

    public void setRecentRounds(int recentRounds) {
        this.recentRounds = recentRounds;
    }

    public int getMemoryTopK() {
        return memoryTopK;
    }

    public void setMemoryTopK(int memoryTopK) {
        this.memoryTopK = memoryTopK;
    }

    public int getRagTopK() {
        return ragTopK;
    }

    public void setRagTopK(int ragTopK) {
        this.ragTopK = ragTopK;
    }
}

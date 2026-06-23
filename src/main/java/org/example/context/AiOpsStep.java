package org.example.context;

import java.util.ArrayList;
import java.util.List;

public class AiOpsStep {

    private int order;
    private String name;
    private String status;
    private String objective;
    private String result;
    private List<String> evidenceIds = new ArrayList<>();

    public int getOrder() {
        return order;
    }

    public void setOrder(int order) {
        this.order = order;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getObjective() {
        return objective;
    }

    public void setObjective(String objective) {
        this.objective = objective;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public List<String> getEvidenceIds() {
        return evidenceIds;
    }

    public void setEvidenceIds(List<String> evidenceIds) {
        this.evidenceIds = evidenceIds == null ? new ArrayList<>() : evidenceIds;
    }
}

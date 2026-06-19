package org.example.memory.graph;

public class GraphRelation {

    private String source;
    private String sourceType;
    private String relation;
    private String target;
    private String targetType;

    public GraphRelation() {
    }

    public GraphRelation(String source, String sourceType, String relation, String target, String targetType) {
        this.source = source;
        this.sourceType = sourceType;
        this.relation = relation;
        this.target = target;
        this.targetType = targetType;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public String getRelation() {
        return relation;
    }

    public void setRelation(String relation) {
        this.relation = relation;
    }

    public String getTarget() {
        return target;
    }

    public void setTarget(String target) {
        this.target = target;
    }

    public String getTargetType() {
        return targetType;
    }

    public void setTargetType(String targetType) {
        this.targetType = targetType;
    }
}

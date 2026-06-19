package org.example.memory.graph;

import java.util.ArrayList;
import java.util.List;

public class GraphExtractionResult {

    private List<GraphEntity> entities = new ArrayList<>();
    private List<GraphRelation> relations = new ArrayList<>();

    public List<GraphEntity> getEntities() {
        return entities;
    }

    public void setEntities(List<GraphEntity> entities) {
        this.entities = entities == null ? new ArrayList<>() : entities;
    }

    public List<GraphRelation> getRelations() {
        return relations;
    }

    public void setRelations(List<GraphRelation> relations) {
        this.relations = relations == null ? new ArrayList<>() : relations;
    }
}

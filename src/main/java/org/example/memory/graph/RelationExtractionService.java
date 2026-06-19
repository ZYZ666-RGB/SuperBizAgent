package org.example.memory.graph;

import org.example.memory.UserMemory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class RelationExtractionService {

    public List<GraphRelation> extractRelations(UserMemory memory, List<GraphEntity> entities) {
        List<GraphRelation> relations = new ArrayList<>();
        Optional<GraphEntity> project = firstOfType(entities, "Project");

        project.ifPresent(projectEntity -> {
            for (GraphEntity entity : entities) {
                if ("Technology".equals(entity.getType())) {
                    relations.add(new GraphRelation(
                            projectEntity.getName(), projectEntity.getType(),
                            "USES",
                            entity.getName(), entity.getType()));
                }
                if ("Module".equals(entity.getType())) {
                    relations.add(new GraphRelation(
                            projectEntity.getName(), projectEntity.getType(),
                            "CONTAINS",
                            entity.getName(), entity.getType()));
                }
            }
        });

        addKnownCrossRelations(memory, entities, relations);
        return relations;
    }

    private void addKnownCrossRelations(
            UserMemory memory,
            List<GraphEntity> entities,
            List<GraphRelation> relations) {
        String content = defaultText(memory.getContent()).toLowerCase(Locale.ROOT);
        Optional<GraphEntity> rag = findByName(entities, "RAG");
        Optional<GraphEntity> milvus = findByName(entities, "Milvus");
        if (rag.isPresent() && milvus.isPresent()
                && (content.contains("rag") || content.contains("向量") || content.contains("vector"))) {
            relations.add(new GraphRelation(
                    rag.get().getName(), rag.get().getType(),
                    "DEPENDS_ON",
                    milvus.get().getName(), milvus.get().getType()));
        }
    }

    private Optional<GraphEntity> firstOfType(List<GraphEntity> entities, String type) {
        return entities.stream()
                .filter(entity -> type.equals(entity.getType()))
                .findFirst();
    }

    private Optional<GraphEntity> findByName(List<GraphEntity> entities, String name) {
        return entities.stream()
                .filter(entity -> name.equalsIgnoreCase(entity.getName()))
                .findFirst();
    }

    private String defaultText(String value) {
        return value == null ? "" : value;
    }
}

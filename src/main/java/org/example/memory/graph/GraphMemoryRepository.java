package org.example.memory.graph;

import org.example.memory.UserMemory;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.Session;
import org.neo4j.driver.Values;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;

@Repository
@ConditionalOnProperty(prefix = "memory.graph", name = "enabled", havingValue = "true", matchIfMissing = true)
public class GraphMemoryRepository {

    private static final Set<String> NODE_TYPES = Set.of(
            "User", "Project", "Technology", "Module", "Agent", "Tool",
            "Goal", "Preference", "Task", "Document", "Memory");
    private static final Set<String> RELATION_TYPES = Set.of(
            "OWNS", "DEVELOPS", "USES", "CONTAINS", "DEPENDS_ON", "CALLS",
            "HAS_GOAL", "HAS_PREFERENCE", "MENTIONS", "PRODUCES", "RELATED_TO");

    private final Driver driver;

    public GraphMemoryRepository(Driver driver) {
        this.driver = driver;
    }

    public void upsertMemory(UserMemory memory, GraphExtractionResult extraction) {
        try (Session session = driver.session()) {
            session.executeWrite(tx -> {
                tx.run("""
                        MERGE (u:User {userId: $userId})
                        MERGE (m:Memory {userId: $userId, memoryId: $memoryId})
                        SET m.content = $content,
                            m.memoryType = $memoryType,
                            m.importance = $importance,
                            m.enabled = true,
                            m.updatedAt = datetime()
                        MERGE (u)-[:OWNS]->(m)
                        """, Values.parameters(
                        "userId", memory.getUserId(),
                        "memoryId", memory.getMemoryId(),
                        "content", memory.getContent(),
                        "memoryType", memory.getMemoryType(),
                        "importance", defaultDouble(memory.getImportance())));

                for (GraphEntity entity : extraction.getEntities()) {
                    mergeEntity(tx, memory, entity);
                }
                for (GraphRelation relation : extraction.getRelations()) {
                    mergeRelation(tx, memory.getUserId(), relation);
                }
                return null;
            });
        }
    }

    public List<String> findRelations(String userId, List<String> entityNames, int limit) {
        if (entityNames == null || entityNames.isEmpty()) {
            return List.of();
        }
        try (Session session = driver.session()) {
            return session.executeRead(tx -> tx.run("""
                            MATCH (u:User {userId: $userId})-[:OWNS]->(m:Memory)-[:MENTIONS]->(e)
                            WHERE e.name IN $entityNames
                              AND coalesce(m.enabled, true) = true
                            MATCH (e)-[r]-(related)
                            WHERE related.userId = $userId OR related:User
                            RETURN DISTINCT e.name AS source,
                                   type(r) AS relation,
                                   coalesce(related.name, related.memoryId, related.userId) AS target
                            LIMIT $limit
                            """, Values.parameters(
                            "userId", userId,
                            "entityNames", entityNames,
                            "limit", Math.max(1, limit)))
                    .list(this::formatRelation));
        }
    }

    public void disableMemory(String userId, String memoryId) {
        try (Session session = driver.session()) {
            session.executeWrite(tx -> {
                tx.run("""
                        MATCH (m:Memory {userId: $userId, memoryId: $memoryId})
                        SET m.enabled = false,
                            m.updatedAt = datetime()
                        """, Values.parameters(
                        "userId", userId,
                        "memoryId", memoryId));
                return null;
            });
        }
    }

    private void mergeEntity(
            org.neo4j.driver.TransactionContext tx,
            UserMemory memory,
            GraphEntity entity) {
        String label = nodeType(entity.getType());
        tx.run("""
                MERGE (e:%s {userId: $userId, name: $name})
                SET e.type = $type,
                    e.updatedAt = datetime()
                """.formatted(label), Values.parameters(
                "userId", memory.getUserId(),
                "name", entity.getName(),
                "type", label));

        tx.run("""
                MATCH (m:Memory {userId: $userId, memoryId: $memoryId})
                MATCH (e:%s {userId: $userId, name: $name})
                MERGE (m)-[:MENTIONS]->(e)
                """.formatted(label), Values.parameters(
                "userId", memory.getUserId(),
                "memoryId", memory.getMemoryId(),
                "name", entity.getName()));

        mergeUserShortcut(tx, memory.getUserId(), entity, label);
    }

    private void mergeUserShortcut(
            org.neo4j.driver.TransactionContext tx,
            String userId,
            GraphEntity entity,
            String label) {
        String relation = switch (label) {
            case "Project" -> "DEVELOPS";
            case "Goal" -> "HAS_GOAL";
            case "Preference" -> "HAS_PREFERENCE";
            default -> null;
        };
        if (relation == null) {
            return;
        }
        tx.run("""
                MATCH (u:User {userId: $userId})
                MATCH (e:%s {userId: $userId, name: $name})
                MERGE (u)-[:%s]->(e)
                """.formatted(label, relation), Values.parameters(
                "userId", userId,
                "name", entity.getName()));
    }

    private void mergeRelation(
            org.neo4j.driver.TransactionContext tx,
            String userId,
            GraphRelation relation) {
        String sourceType = nodeType(relation.getSourceType());
        String targetType = nodeType(relation.getTargetType());
        String relationType = relationType(relation.getRelation());
        tx.run("""
                MATCH (source:%s {userId: $userId, name: $source})
                MATCH (target:%s {userId: $userId, name: $target})
                MERGE (source)-[r:%s]->(target)
                SET r.userId = $userId,
                    r.updatedAt = datetime()
                """.formatted(sourceType, targetType, relationType), Values.parameters(
                "userId", userId,
                "source", relation.getSource(),
                "target", relation.getTarget()));
    }

    private String formatRelation(Record record) {
        return record.get("source").asString()
                + " -["
                + record.get("relation").asString()
                + "]- "
                + record.get("target").asString();
    }

    private String nodeType(String value) {
        if (value != null && NODE_TYPES.contains(value)) {
            return value;
        }
        return "Memory";
    }

    private String relationType(String value) {
        if (value != null && RELATION_TYPES.contains(value)) {
            return value;
        }
        return "RELATED_TO";
    }

    private double defaultDouble(Double value) {
        return value == null ? 0.5 : value;
    }
}

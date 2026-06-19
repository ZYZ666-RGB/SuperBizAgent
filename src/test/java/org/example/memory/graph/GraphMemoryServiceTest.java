package org.example.memory.graph;

import org.example.memory.MemoryProperties;
import org.example.memory.UserMemory;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class GraphMemoryServiceTest {

    private final EntityExtractionService entityExtractionService = new EntityExtractionService();
    private final RelationExtractionService relationExtractionService = new RelationExtractionService();

    @Test
    void graphExtractionCapturesProjectTechnologyAndModuleRelations() {
        GraphMemoryRepository repository = mock(GraphMemoryRepository.class);
        GraphMemoryService service = new GraphMemoryService(
                new MemoryProperties(),
                entityExtractionService,
                relationExtractionService,
                repository);
        UserMemory memory = projectMemory();

        service.indexMemory(memory);

        ArgumentCaptor<GraphExtractionResult> captor = ArgumentCaptor.forClass(GraphExtractionResult.class);
        verify(repository).upsertMemory(eq(memory), captor.capture());
        GraphExtractionResult result = captor.getValue();

        assertThat(result.getEntities())
                .extracting(GraphEntity::getName, GraphEntity::getType)
                .contains(
                        tuple("SuperBizAgent", "Project"),
                        tuple("MySQL", "Technology"),
                        tuple("Milvus", "Technology"),
                        tuple("Neo4j", "Technology"),
                        tuple("RAG", "Module"));
        assertThat(result.getRelations())
                .extracting(GraphRelation::getSource, GraphRelation::getRelation, GraphRelation::getTarget)
                .contains(
                        tuple("SuperBizAgent", "USES", "MySQL"),
                        tuple("SuperBizAgent", "USES", "Milvus"),
                        tuple("SuperBizAgent", "USES", "Neo4j"),
                        tuple("SuperBizAgent", "CONTAINS", "RAG"),
                        tuple("RAG", "DEPENDS_ON", "Milvus"));
    }

    @Test
    void disabledGraphSkipsRepositoryCalls() {
        MemoryProperties properties = new MemoryProperties();
        properties.getGraph().setEnabled(false);
        GraphMemoryRepository repository = mock(GraphMemoryRepository.class);
        GraphMemoryService service = new GraphMemoryService(
                properties,
                entityExtractionService,
                relationExtractionService,
                repository);

        service.indexMemory(projectMemory());

        verifyNoInteractions(repository);
    }

    @Test
    void lowImportanceEpisodicMemoryDoesNotEnterGraph() {
        GraphMemoryRepository repository = mock(GraphMemoryRepository.class);
        GraphMemoryService service = new GraphMemoryService(
                new MemoryProperties(),
                entityExtractionService,
                relationExtractionService,
                repository);
        UserMemory memory = projectMemory();
        memory.setMemoryType("episodic");
        memory.setImportance(0.3);

        service.indexMemory(memory);

        verifyNoInteractions(repository);
    }

    @Test
    @SuppressWarnings("unchecked")
    void retrieverSearchesRelationsUnderCurrentUserScope() {
        MemoryProperties properties = new MemoryProperties();
        GraphMemoryRepository repository = mock(GraphMemoryRepository.class);
        GraphMemoryRetriever retriever = new GraphMemoryRetriever(
                properties,
                entityExtractionService,
                repository);
        org.mockito.Mockito.when(repository.findRelations(
                        eq("user-a"),
                        anyList(),
                        eq(properties.getGraph().getTopK())))
                .thenReturn(List.of("SuperBizAgent -[USES]- Milvus"));

        List<String> relations = retriever.searchRelations("user-a", "SuperBizAgent 和 Milvus 是什么关系？");

        assertThat(relations).containsExactly("SuperBizAgent -[USES]- Milvus");
        ArgumentCaptor<List<String>> entitiesCaptor = ArgumentCaptor.forClass(List.class);
        verify(repository).findRelations(
                eq("user-a"),
                entitiesCaptor.capture(),
                eq(properties.getGraph().getTopK()));
        assertThat(entitiesCaptor.getValue()).containsExactlyInAnyOrder("SuperBizAgent", "Milvus");
    }

    @Test
    void retrieverFallsBackToEmptyRelationsWhenNeo4jFails() {
        MemoryProperties properties = new MemoryProperties();
        GraphMemoryRepository repository = mock(GraphMemoryRepository.class);
        GraphMemoryRetriever retriever = new GraphMemoryRetriever(
                properties,
                entityExtractionService,
                repository);
        org.mockito.Mockito.when(repository.findRelations(
                        "user-a",
                        List.of("SuperBizAgent"),
                        properties.getGraph().getTopK()))
                .thenThrow(new RuntimeException("neo4j down"));

        List<String> relations = retriever.searchRelations("user-a", "SuperBizAgent");

        assertThat(relations).isEmpty();
    }

    private UserMemory projectMemory() {
        UserMemory memory = new UserMemory();
        memory.setUserId("user-a");
        memory.setSessionId("session-a");
        memory.setMemoryId("memory-1");
        memory.setMemoryType("project_context");
        memory.setContent("SuperBizAgent uses MySQL, Milvus and Neo4j. RAG depends on Milvus for vector search.");
        memory.setEntities("[\"SuperBizAgent\", \"MySQL\", \"Milvus\", \"Neo4j\", \"RAG\"]");
        memory.setImportance(0.9);
        memory.setEnabled(true);
        return memory;
    }
}

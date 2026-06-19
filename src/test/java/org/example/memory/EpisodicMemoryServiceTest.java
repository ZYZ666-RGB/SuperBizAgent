package org.example.memory;

import org.example.memory.graph.GraphMemoryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class EpisodicMemoryServiceTest {

    private EmbeddedDatabase database;
    private UserMemoryRepository userMemoryRepository;
    private MemoryVectorService memoryVectorService;
    private GraphMemoryService graphMemoryService;
    private MemoryProperties memoryProperties;

    @BeforeEach
    void setUp() {
        database = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .setName("episodic-memory-test-" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE")
                .build();
        new ResourceDatabasePopulator(new ClassPathResource("db/schema-memory-test.sql"))
                .execute(database);

        JdbcTemplate jdbcTemplate = new JdbcTemplate(database);
        userMemoryRepository = new UserMemoryRepository(jdbcTemplate);
        memoryVectorService = mock(MemoryVectorService.class);
        graphMemoryService = mock(GraphMemoryService.class);
        memoryProperties = new MemoryProperties();
    }

    @AfterEach
    void tearDown() {
        database.shutdown();
    }

    @Test
    void saveEventCreatesEpisodicMemoryWithTaskScopeAndMetadata() {
        EpisodicMemoryService service = new EpisodicMemoryService(
                memoryProperties,
                userMemoryRepository,
                memoryVectorService,
                new MemorySafetyService(),
                graphMemoryService);

        UserMemory saved = service.saveEvent(
                "user-a",
                "session-a",
                "task-1",
                "aiops_agent",
                "aiops_final_report_generated",
                "AIOps analysis task task-1 generated a final report.",
                Map.of("reportLength", 128),
                0.85);

        assertThat(saved).isNotNull();
        assertThat(saved.getMemoryType()).isEqualTo("episodic");
        assertThat(saved.getScopeType()).isEqualTo("task");
        assertThat(saved.getTaskId()).isEqualTo("task-1");
        assertThat(saved.getMetadata()).contains("aiops_final_report_generated");
        assertThat(saved.getMetadata()).contains("reportLength");
        assertThat(userMemoryRepository.findRecentEpisodicForPrompt("user-a", 5))
                .extracting(UserMemory::getContent)
                .containsExactly("AIOps analysis task task-1 generated a final report.");
        verify(memoryVectorService).indexMemory(saved);
        verify(graphMemoryService).indexMemory(saved);
    }

    @Test
    void disabledMemorySkipsEpisodicEventPersistence() {
        memoryProperties.setEnabled(false);
        EpisodicMemoryService service = new EpisodicMemoryService(
                memoryProperties,
                userMemoryRepository,
                memoryVectorService,
                new MemorySafetyService(),
                graphMemoryService);

        UserMemory saved = service.saveEvent(
                "user-a",
                "session-a",
                null,
                "upload_agent",
                "file_upload",
                "User uploaded file runbook.md.",
                Map.of("fileName", "runbook.md"),
                0.75);

        assertThat(saved).isNull();
        assertThat(userMemoryRepository.countEnabledByUser("user-a")).isZero();
        verifyNoInteractions(memoryVectorService, graphMemoryService);
    }

    @Test
    void unsafeEventContentIsNotPersisted() {
        EpisodicMemoryService service = new EpisodicMemoryService(
                memoryProperties,
                userMemoryRepository,
                memoryVectorService,
                new MemorySafetyService(),
                graphMemoryService);

        UserMemory saved = service.saveEvent(
                "user-a",
                "session-a",
                "task-1",
                "aiops_agent",
                "aiops_analysis_failed",
                "AIOps failed because api_key=abc123456789 leaked in an error.",
                Map.of("stage", "EXCEPTION"),
                0.75);

        assertThat(saved).isNull();
        assertThat(userMemoryRepository.countEnabledByUser("user-a")).isZero();
        verifyNoInteractions(memoryVectorService, graphMemoryService);
    }
}

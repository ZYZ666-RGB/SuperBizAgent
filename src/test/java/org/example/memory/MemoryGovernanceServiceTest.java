package org.example.memory;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.example.memory.graph.GraphMemoryService;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class MemoryGovernanceServiceTest {

    private EmbeddedDatabase database;
    private JdbcTemplate jdbcTemplate;
    private UserMemoryRepository userMemoryRepository;
    private LongTermMemoryService longTermMemoryService;
    private EpisodicMemoryService episodicMemoryService;
    private MemoryProperties memoryProperties;

    @BeforeEach
    void setUp() {
        database = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .setName("memory-governance-test-" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE")
                .build();
        new ResourceDatabasePopulator(new ClassPathResource("db/schema-memory-test.sql"))
                .execute(database);

        jdbcTemplate = new JdbcTemplate(database);
        userMemoryRepository = new UserMemoryRepository(jdbcTemplate);
        memoryProperties = new MemoryProperties();
        MemoryVectorService vectorService = mock(MemoryVectorService.class);
        MemorySafetyService safetyService = new MemorySafetyService();
        MemoryAdmissionService admissionService = new MemoryAdmissionService(memoryProperties, safetyService);
        longTermMemoryService = new LongTermMemoryService(
                memoryProperties,
                userMemoryRepository,
                new MemoryExtractorService(),
                admissionService,
                new MemoryDedupService(userMemoryRepository),
                vectorService);
        episodicMemoryService = new EpisodicMemoryService(
                memoryProperties,
                userMemoryRepository,
                vectorService,
                safetyService,
                (GraphMemoryService) null);
    }

    @AfterEach
    void tearDown() {
        database.shutdown();
    }

    @Test
    void consolidationPromotesHighImportanceEpisodicEventsToSemanticMemory() {
        episodicMemoryService.saveEvent(
                "user-a",
                "session-a",
                "task-1",
                "aiops_agent",
                "aiops_final_report_generated",
                "AIOps task task-1 generated the final report for SuperBizAgent.",
                Map.of("stage", "FINISHED"),
                0.9);
        MemoryConsolidationService service = new MemoryConsolidationService(
                memoryProperties,
                userMemoryRepository,
                longTermMemoryService);

        var saved = service.consolidateUser("user-a");

        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getMemoryType()).isEqualTo("semantic");
        assertThat(saved.get(0).getSource()).isEqualTo("user_explicit");
        assertThat(saved.get(0).getContent()).contains("SuperBizAgent");
    }

    @Test
    void forgettingDisablesLowImportanceUnaccessedMemories() {
        UserMemory memory = longTermMemoryService.addManualMemory(
                "user-a",
                "Temporary low-value note.",
                "semantic",
                "user",
                0.1).get(0);
        MemoryForgettingService service = new MemoryForgettingService(
                memoryProperties,
                userMemoryRepository,
                longTermMemoryService);

        int disabled = service.forget("user-a", "importance_based");

        assertThat(disabled).isEqualTo(1);
        assertThat(userMemoryRepository.findEnabledByMemoryId("user-a", memory.getMemoryId())).isEmpty();
    }

    @Test
    void timeBasedForgettingDisablesOldLowImportanceEpisodicMemory() {
        UserMemory memory = episodicMemoryService.saveEvent(
                "user-a",
                "session-a",
                null,
                "upload_agent",
                "file_upload",
                "User uploaded a temporary file.",
                Map.of("fileName", "tmp.md"),
                0.3);
        jdbcTemplate.update(
                "UPDATE user_memory SET updated_at = ? WHERE memory_id = ?",
                java.sql.Timestamp.valueOf(LocalDateTime.now().minusDays(45)),
                memory.getMemoryId());
        MemoryForgettingService service = new MemoryForgettingService(
                memoryProperties,
                userMemoryRepository,
                longTermMemoryService);

        int disabled = service.forget("user-a", "time_based");

        assertThat(disabled).isEqualTo(1);
        assertThat(userMemoryRepository.findEnabledByMemoryId("user-a", memory.getMemoryId())).isEmpty();
    }

    @Test
    void capacityBasedForgettingKeepsHighestValueMemories() {
        memoryProperties.getForgetting().setMaxMemoriesPerUser(1);
        UserMemory low = longTermMemoryService.addManualMemory(
                "user-a",
                "Low priority memory.",
                "semantic",
                "user",
                0.3).get(0);
        UserMemory high = longTermMemoryService.addManualMemory(
                "user-a",
                "High priority memory.",
                "semantic",
                "user",
                0.9).get(0);
        MemoryForgettingService service = new MemoryForgettingService(
                memoryProperties,
                userMemoryRepository,
                longTermMemoryService);

        int disabled = service.forget("user-a", "capacity_based");

        assertThat(disabled).isEqualTo(1);
        assertThat(userMemoryRepository.findEnabledByMemoryId("user-a", high.getMemoryId())).isPresent();
        assertThat(userMemoryRepository.findEnabledByMemoryId("user-a", low.getMemoryId())).isEmpty();
    }
}

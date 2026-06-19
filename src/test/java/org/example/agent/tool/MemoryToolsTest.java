package org.example.agent.tool;

import org.example.memory.LongTermMemoryService;
import org.example.memory.MemoryAdmissionService;
import org.example.memory.MemoryConsolidationService;
import org.example.memory.MemoryDedupService;
import org.example.memory.MemoryExtractorService;
import org.example.memory.MemoryForgettingService;
import org.example.memory.MemoryProperties;
import org.example.memory.MemorySafetyService;
import org.example.memory.MemoryUserContext;
import org.example.memory.MemoryVectorService;
import org.example.memory.UserMemory;
import org.example.memory.UserMemoryRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class MemoryToolsTest {

    private EmbeddedDatabase database;
    private UserMemoryRepository userMemoryRepository;
    private LongTermMemoryService longTermMemoryService;
    private MemoryTools memoryTools;

    @BeforeEach
    void setUp() {
        database = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .setName("memory-tools-test-" + java.util.UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE")
                .build();
        new ResourceDatabasePopulator(new ClassPathResource("db/schema-memory-test.sql"))
                .execute(database);

        userMemoryRepository = new UserMemoryRepository(new JdbcTemplate(database));
        MemoryProperties properties = new MemoryProperties();
        MemoryVectorService vectorService = mock(MemoryVectorService.class);
        MemorySafetyService safetyService = new MemorySafetyService();
        longTermMemoryService = new LongTermMemoryService(
                properties,
                userMemoryRepository,
                new MemoryExtractorService(),
                new MemoryAdmissionService(properties, safetyService),
                new MemoryDedupService(userMemoryRepository),
                vectorService);
        memoryTools = new MemoryTools(
                longTermMemoryService,
                new MemoryConsolidationService(properties, userMemoryRepository, longTermMemoryService),
                new MemoryForgettingService(properties, userMemoryRepository, longTermMemoryService));
        MemoryUserContext.setUserId("user-a");
    }

    @AfterEach
    void tearDown() {
        MemoryUserContext.clear();
        database.shutdown();
    }

    @Test
    void memoryToolsOperateOnlyOnCurrentUser() {
        String addResponse = memoryTools.addMemory(
                "Prefer MySQL context when answering SuperBizAgent questions.",
                "preference",
                0.85);
        longTermMemoryService.addManualMemory(
                "user-b",
                "Other user's memory.",
                "preference",
                "user",
                0.9);

        String searchResponse = memoryTools.searchMemory("SuperBizAgent", "preference", 5);
        String statsResponse = memoryTools.memoryStats();

        assertThat(addResponse).contains("\"status\":\"ok\"");
        assertThat(searchResponse).contains("SuperBizAgent");
        assertThat(searchResponse).doesNotContain("Other user's memory");
        assertThat(statsResponse).contains("\"totalEnabled\":1");
    }

    @Test
    void memoryToolsUpdateAndRemoveCurrentUserMemory() {
        UserMemory memory = longTermMemoryService.addManualMemory(
                "user-a",
                "Old memory content.",
                "semantic",
                "user",
                0.8).get(0);

        String updateResponse = memoryTools.updateMemory(memory.getMemoryId(), "New memory content.");
        String removeResponse = memoryTools.removeMemory(memory.getMemoryId());

        assertThat(updateResponse).contains("\"status\":\"ok\"");
        assertThat(removeResponse).contains("\"status\":\"ok\"");
        assertThat(userMemoryRepository.findEnabledByMemoryId("user-a", memory.getMemoryId())).isEmpty();
    }
}

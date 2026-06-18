package org.example.memory;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LongTermMemoryServiceTest {

    private EmbeddedDatabase database;
    private LongTermMemoryService longTermMemoryService;
    private UserMemoryRepository userMemoryRepository;

    @BeforeEach
    void setUp() {
        database = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .setName("long-term-memory-test-" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE")
                .build();
        new ResourceDatabasePopulator(new ClassPathResource("db/schema-memory-test.sql"))
                .execute(database);

        JdbcTemplate jdbcTemplate = new JdbcTemplate(database);
        userMemoryRepository = new UserMemoryRepository(jdbcTemplate);
        MemoryProperties memoryProperties = new MemoryProperties();
        MemorySafetyService memorySafetyService = new MemorySafetyService();
        MemoryAdmissionService memoryAdmissionService = new MemoryAdmissionService(memoryProperties, memorySafetyService);
        MemoryDedupService memoryDedupService = new MemoryDedupService(userMemoryRepository);
        longTermMemoryService = new LongTermMemoryService(
                memoryProperties,
                userMemoryRepository,
                new MemoryExtractorService(),
                memoryAdmissionService,
                memoryDedupService);
    }

    @AfterEach
    void tearDown() {
        database.shutdown();
    }

    @Test
    void explicitSaveCreatesLongTermMemoryAndDeduplicates() {
        List<UserMemory> saved = longTermMemoryService.extractAndSaveAfterChat(
                "user-a",
                "session-a",
                "记住，我希望你以后回答 Agent 问题时先从基础开始。",
                "好的，我会记住。",
                null);

        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getSource()).isEqualTo("user_explicit");
        assertThat(saved.get(0).getMemoryType()).isEqualTo("preference");
        assertThat(saved.get(0).getContent()).contains("Agent");
        assertThat(longTermMemoryService.countEnabledByUser("user-a")).isEqualTo(1);

        List<UserMemory> duplicate = longTermMemoryService.extractAndSaveAfterChat(
                "user-a",
                "session-a",
                "记住，我希望你以后回答 Agent 问题时先从基础开始。",
                "好的，我会记住。",
                null);

        assertThat(duplicate).isEmpty();
        assertThat(longTermMemoryService.countEnabledByUser("user-a")).isEqualTo(1);
    }

    @Test
    void explicitSaveRejectsSensitiveSecrets() {
        List<UserMemory> saved = longTermMemoryService.extractAndSaveAfterChat(
                "user-a",
                "session-a",
                "记住，api_key=abc123456789",
                "好的。",
                null);

        assertThat(saved).isEmpty();
        assertThat(longTermMemoryService.countEnabledByUser("user-a")).isZero();
    }

    @Test
    void autoExtractionSavesOnlyCandidatesPassingAdmissionThresholds() {
        DashScopeChatModel chatModel = mock(DashScopeChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse("""
                [
                  {
                    "memoryType": "project_context",
                    "scopeType": "project",
                    "content": "SuperBizAgent uses MySQL for persistent memory.",
                    "evidence": "The user confirmed MySQL persistent memory.",
                    "entities": ["SuperBizAgent", "MySQL"],
                    "explicitSave": false,
                    "source": "auto_extracted",
                    "evidenceScore": 0.9,
                    "stabilityScore": 0.8,
                    "futureUsefulnessScore": 0.9,
                    "safetyScore": 1,
                    "importance": 0.8,
                    "confidence": 0.9,
                    "shouldSave": true,
                    "reason": "Stable project fact."
                  },
                  {
                    "memoryType": "semantic",
                    "scopeType": "user",
                    "content": "The user is briefly tired today.",
                    "evidence": "The user said they are tired.",
                    "entities": [],
                    "explicitSave": false,
                    "source": "auto_extracted",
                    "evidenceScore": 0.4,
                    "stabilityScore": 0.1,
                    "futureUsefulnessScore": 0.1,
                    "safetyScore": 1,
                    "importance": 0.2,
                    "confidence": 0.4,
                    "shouldSave": true,
                    "reason": "Temporary state."
                  }
                ]
                """));

        List<UserMemory> saved = longTermMemoryService.extractAndSaveAfterChat(
                "user-a",
                "session-a",
                "We decided to use MySQL for memory.",
                "Confirmed.",
                chatModel);

        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getContent()).contains("MySQL");
        assertThat(longTermMemoryService.countEnabledByUser("user-a")).isEqualTo(1);
        assertThat(longTermMemoryService.countEnabledByUser("user-b")).isZero();
    }

    @Test
    void promptMemoriesAreScopedByUser() {
        longTermMemoryService.extractAndSaveAfterChat(
                "user-a",
                "session-a",
                "记住，我的项目叫 SuperBizAgent。",
                "好的。",
                null);
        longTermMemoryService.extractAndSaveAfterChat(
                "user-b",
                "session-a",
                "记住，我的项目叫 OtherProject。",
                "好的。",
                null);

        assertThat(userMemoryRepository.findEnabledForPrompt("user-a", 5))
                .extracting(UserMemory::getContent)
                .anyMatch(content -> content.contains("SuperBizAgent"))
                .noneMatch(content -> content.contains("OtherProject"));
    }

    private ChatResponse chatResponse(String content) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(content))));
    }
}

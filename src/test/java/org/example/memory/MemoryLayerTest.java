package org.example.memory;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import org.example.memory.dto.ChatMessageDTO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MemoryLayerTest {

    private EmbeddedDatabase database;
    private ConversationMessageRepository messageRepository;
    private ConversationSummaryRepository summaryRepository;
    private JdbcConversationMemoryService conversationMemoryService;
    private SummaryMemoryService summaryMemoryService;
    private MemoryContextBuilder memoryContextBuilder;
    private LongTermMemoryService longTermMemoryService;
    private MemoryProperties memoryProperties;

    @BeforeEach
    void setUp() {
        database = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .setName("memory-test-" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE")
                .build();
        new ResourceDatabasePopulator(new ClassPathResource("db/schema-memory-test.sql"))
                .execute(database);

        JdbcTemplate jdbcTemplate = new JdbcTemplate(database);
        messageRepository = new ConversationMessageRepository(jdbcTemplate);
        summaryRepository = new ConversationSummaryRepository(jdbcTemplate);
        UserMemoryRepository userMemoryRepository = new UserMemoryRepository(jdbcTemplate);
        conversationMemoryService = new JdbcConversationMemoryService(messageRepository, summaryRepository);

        memoryProperties = new MemoryProperties();
        memoryProperties.setWindowSize(2);
        memoryProperties.setSummaryThreshold(8);

        summaryMemoryService = new SummaryMemoryService(
                memoryProperties,
                messageRepository,
                summaryRepository,
                new SummaryPromptBuilder());
        MemorySafetyService memorySafetyService = new MemorySafetyService();
        MemoryAdmissionService memoryAdmissionService = new MemoryAdmissionService(memoryProperties, memorySafetyService);
        MemoryDedupService memoryDedupService = new MemoryDedupService(userMemoryRepository);
        longTermMemoryService = new LongTermMemoryService(
                memoryProperties,
                userMemoryRepository,
                new MemoryExtractorService(),
                memoryAdmissionService,
                memoryDedupService);
        memoryContextBuilder = new MemoryContextBuilder(
                memoryProperties,
                conversationMemoryService,
                summaryMemoryService,
                longTermMemoryService);
    }

    @AfterEach
    void tearDown() {
        database.shutdown();
    }

    @Test
    void recentMessagesArePersistedWindowedAndScopedByUserAndSession() {
        saveTurn("user-a", "session-a", "q1", "a1");
        saveTurn("user-a", "session-a", "q2", "a2");
        saveTurn("user-a", "session-a", "q3", "a3");
        saveTurn("user-a", "session-b", "other-session-question", "other-session-answer");
        saveTurn("user-b", "session-a", "other-user-question", "other-user-answer");

        List<ChatMessageDTO> recent = conversationMemoryService.getRecentMessages("user-a", "session-a", 2);

        assertThat(recent)
                .extracting(ChatMessageDTO::getContent)
                .containsExactly("q2", "a2", "q3", "a3");
        assertThat(recent)
                .extracting(ChatMessageDTO::getContent)
                .doesNotContain("other-session-question", "other-user-question");
        assertThat(conversationMemoryService.countMessages("user-a", "session-a")).isEqualTo(6);
    }

    @Test
    void clearSessionDeletesOnlyMatchingUserAndSessionMemory() {
        saveTurn("user-a", "session-a", "q1", "a1");
        saveTurn("user-a", "session-b", "q2", "a2");
        saveTurn("user-b", "session-a", "q3", "a3");
        summaryRepository.upsert("user-a", "session-a", "summary-a", 2L, 2);
        summaryRepository.upsert("user-a", "session-b", "summary-b", 4L, 2);

        conversationMemoryService.clearSession("user-a", "session-a");

        assertThat(conversationMemoryService.countMessages("user-a", "session-a")).isZero();
        assertThat(summaryRepository.findBySession("user-a", "session-a")).isEmpty();
        assertThat(conversationMemoryService.countMessages("user-a", "session-b")).isEqualTo(2);
        assertThat(conversationMemoryService.countMessages("user-b", "session-a")).isEqualTo(2);
        assertThat(summaryRepository.findBySession("user-a", "session-b")).isPresent();
    }

    @Test
    void memoryContextContainsSessionSummaryAndOnlyRecentMessages() {
        saveTurn("user-a", "session-a", "q1", "a1");
        saveTurn("user-a", "session-a", "q2", "a2");
        saveTurn("user-a", "session-a", "q3", "a3");
        summaryRepository.upsert("user-a", "session-a", "project summary", 2L, 2);

        MemoryPromptContext context = memoryContextBuilder.buildForChat("user-a", "session-a");

        assertThat(context.getSessionSummary()).isEqualTo("project summary");
        assertThat(context.getRecentMessages())
                .extracting(ChatMessageDTO::getContent)
                .containsExactly("q2", "a2", "q3", "a3");
    }

    @Test
    void summaryRefreshSkipsSessionsBelowThreshold() {
        DashScopeChatModel chatModel = mock(DashScopeChatModel.class);
        saveTurn("user-a", "session-a", "q1", "a1");
        saveTurn("user-a", "session-a", "q2", "a2");

        summaryMemoryService.refreshSummaryIfNeeded("user-a", "session-a", chatModel);

        verify(chatModel, never()).call(any(Prompt.class));
        assertThat(summaryRepository.findBySession("user-a", "session-a")).isEmpty();
    }

    @Test
    void summaryRefreshRollsForwardAndKeepsRecentMessagesUnSummarized() {
        memoryProperties.setWindowSize(3);
        DashScopeChatModel chatModel = mock(DashScopeChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(
                chatResponse("summary v1"),
                chatResponse("summary v2"));

        for (int i = 1; i <= 8; i++) {
            saveTurn("user-a", "session-a", "q" + i, "a" + i);
        }

        summaryMemoryService.refreshSummaryIfNeeded("user-a", "session-a", chatModel);

        ConversationSummary firstSummary = summaryRepository.findBySession("user-a", "session-a").orElseThrow();
        assertThat(firstSummary.getSummary()).isEqualTo("summary v1");
        assertThat(firstSummary.getSummarizedUntilMessageId()).isEqualTo(10L);
        assertThat(firstSummary.getSummarizedMessageCount()).isEqualTo(10);

        saveTurn("user-a", "session-a", "q9", "a9");
        saveTurn("user-a", "session-a", "q10", "a10");

        summaryMemoryService.refreshSummaryIfNeeded("user-a", "session-a", chatModel);

        ConversationSummary secondSummary = summaryRepository.findBySession("user-a", "session-a").orElseThrow();
        assertThat(secondSummary.getSummary()).isEqualTo("summary v2");
        assertThat(secondSummary.getSummarizedUntilMessageId()).isEqualTo(14L);
        assertThat(secondSummary.getSummarizedMessageCount()).isEqualTo(14);

        assertThat(conversationMemoryService.getRecentMessages("user-a", "session-a", 3))
                .extracting(ChatMessageDTO::getContent)
                .containsExactly("q8", "a8", "q9", "a9", "q10", "a10");

        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel, times(2)).call(promptCaptor.capture());
        String secondPrompt = promptCaptor.getAllValues().get(1).getContents();
        assertThat(secondPrompt).contains("summary v1");
        assertThat(secondPrompt).contains("q6");
        assertThat(secondPrompt).doesNotContain("q8");
    }

    private void saveTurn(String userId, String sessionId, String question, String answer) {
        conversationMemoryService.saveMessage(userId, sessionId, "user", question);
        conversationMemoryService.saveMessage(userId, sessionId, "assistant", answer);
    }

    private ChatResponse chatResponse(String content) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(content))));
    }
}

package org.example.controller;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.streaming.OutputType;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;
import org.example.memory.ConversationMemoryService;
import org.example.memory.EpisodicMemoryService;
import org.example.memory.LongTermMemoryService;
import org.example.memory.MemoryContextBuilder;
import org.example.memory.MemoryPromptContext;
import org.example.memory.SummaryMemoryService;
import org.example.memory.MemoryUserContext;
import org.example.memory.task.AgentTaskState;
import org.example.memory.task.AgentTaskStateService;
import org.example.service.AiOpsService;
import org.example.service.ChatService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RestController
@RequestMapping("/api")
public class ChatController {

    private static final Logger logger = LoggerFactory.getLogger(ChatController.class);
    private static final String DEFAULT_USER_ID = "default_user";

    @Autowired
    private AiOpsService aiOpsService;

    @Autowired
    private ChatService chatService;

    @Autowired
    private ConversationMemoryService conversationMemoryService;

    @Autowired
    private MemoryContextBuilder memoryContextBuilder;

    @Autowired
    private SummaryMemoryService summaryMemoryService;

    @Autowired
    private LongTermMemoryService longTermMemoryService;

    @Autowired
    private EpisodicMemoryService episodicMemoryService;

    @Autowired
    private AgentTaskStateService agentTaskStateService;

    @Autowired
    private ToolCallbackProvider tools;

    private final ExecutorService executor = Executors.newCachedThreadPool();

    @PostMapping("/chat")
    public ResponseEntity<ApiResponse<ChatResponse>> chat(
            @RequestBody ChatRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String headerUserId) {
        try {
            if (request.getQuestion() == null || request.getQuestion().trim().isEmpty()) {
                logger.warn("Question is empty");
                return ResponseEntity.ok(ApiResponse.success(ChatResponse.error("问题内容不能为空")));
            }

            String userId = resolveUserId(headerUserId, request.getUserId());
            String sessionId = normalizeSessionId(request.getId());
            logger.info("Received chat request. userId={}, sessionId={}, question={}",
                    userId, sessionId, request.getQuestion());

            DashScopeApi dashScopeApi = chatService.createDashScopeApi();
            DashScopeChatModel chatModel = chatService.createStandardChatModel(dashScopeApi);
            chatService.logAvailableTools();

            MemoryPromptContext memoryContext = memoryContextBuilder.buildForChat(
                    userId, sessionId, request.getQuestion());
            String systemPrompt = chatService.buildSystemPrompt(memoryContext);
            ReactAgent agent = chatService.createReactAgent(chatModel, systemPrompt);

            String fullAnswer;
            MemoryUserContext.setUserId(userId);
            try {
                fullAnswer = chatService.executeChat(agent, request.getQuestion());
            } finally {
                MemoryUserContext.clear();
            }
            saveConversationTurn(userId, sessionId, request.getQuestion(), fullAnswer);
            runPostChatMemoryPipelineAsync(userId, sessionId, request.getQuestion(), fullAnswer, chatModel);

            return ResponseEntity.ok(ApiResponse.success(ChatResponse.success(fullAnswer)));
        } catch (Exception e) {
            logger.error("Chat failed", e);
            return ResponseEntity.ok(ApiResponse.success(ChatResponse.error(e.getMessage())));
        }
    }

    @PostMapping("/chat/clear")
    public ResponseEntity<ApiResponse<String>> clearChatHistory(
            @RequestBody ClearRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String headerUserId) {
        try {
            if (request.getId() == null || request.getId().isBlank()) {
                return ResponseEntity.ok(ApiResponse.error("会话ID不能为空"));
            }

            String userId = resolveUserId(headerUserId, request.getUserId());
            String sessionId = normalizeSessionId(request.getId());
            conversationMemoryService.clearSession(userId, sessionId);
            logger.info("Cleared conversation memory. userId={}, sessionId={}", userId, sessionId);
            return ResponseEntity.ok(ApiResponse.success("会话历史已清空"));
        } catch (Exception e) {
            logger.error("Clear chat history failed", e);
            return ResponseEntity.ok(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping(value = "/chat_stream", produces = "text/event-stream;charset=UTF-8")
    public SseEmitter chatStream(
            @RequestBody ChatRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String headerUserId) {
        SseEmitter emitter = new SseEmitter(300000L);

        if (request.getQuestion() == null || request.getQuestion().trim().isEmpty()) {
            try {
                emitter.send(SseEmitter.event().name("message")
                        .data(SseMessage.error("问题内容不能为空"), MediaType.APPLICATION_JSON));
                emitter.complete();
            } catch (IOException e) {
                emitter.completeWithError(e);
            }
            return emitter;
        }

        String userId = resolveUserId(headerUserId, request.getUserId());
        String sessionId = normalizeSessionId(request.getId());

        executor.execute(() -> {
            try {
                logger.info("Received streaming chat request. userId={}, sessionId={}, question={}",
                        userId, sessionId, request.getQuestion());

                DashScopeApi dashScopeApi = chatService.createDashScopeApi();
                DashScopeChatModel chatModel = chatService.createStandardChatModel(dashScopeApi);
                chatService.logAvailableTools();

                MemoryPromptContext memoryContext = memoryContextBuilder.buildForChat(
                        userId, sessionId, request.getQuestion());
                String systemPrompt = chatService.buildSystemPrompt(memoryContext);
                ReactAgent agent = chatService.createReactAgent(chatModel, systemPrompt);

                StringBuilder fullAnswerBuilder = new StringBuilder();
                MemoryUserContext.setUserId(userId);
                Flux<NodeOutput> stream = agent.stream(request.getQuestion());

                stream.subscribe(
                        output -> handleStreamingOutput(output, emitter, fullAnswerBuilder),
                        error -> {
                            try {
                                handleStreamingError(error, emitter);
                            } finally {
                                MemoryUserContext.clear();
                            }
                        },
                        () -> {
                            try {
                                handleStreamingComplete(
                                        userId, sessionId, request.getQuestion(), fullAnswerBuilder, chatModel, emitter);
                            } finally {
                                MemoryUserContext.clear();
                            }
                        }
                );
            } catch (Exception e) {
                MemoryUserContext.clear();
                logger.error("Streaming chat initialization failed", e);
                try {
                    emitter.send(SseEmitter.event().name("message")
                            .data(SseMessage.error(e.getMessage()), MediaType.APPLICATION_JSON));
                } catch (IOException ex) {
                    logger.error("Failed to send streaming initialization error", ex);
                }
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    @PostMapping(value = "/ai_ops", produces = "text/event-stream;charset=UTF-8")
    public SseEmitter aiOps(
            @RequestHeader(value = "X-User-Id", required = false) String headerUserId,
            @RequestParam(value = "sessionId", required = false) String requestSessionId,
            @RequestParam(value = "taskId", required = false) String requestTaskId) {
        SseEmitter emitter = new SseEmitter(600000L);
        String userId = resolveUserId(headerUserId, null);
        String sessionId = normalizeSessionId(requestSessionId);
        String taskId = requestTaskId == null || requestTaskId.isBlank()
                ? UUID.randomUUID().toString()
                : requestTaskId.trim();

        executor.execute(() -> {
            try {
                logger.info("Received AI Ops request. userId={}, sessionId={}, taskId={}",
                        userId, sessionId, taskId);
                agentTaskStateService.startTask(userId, sessionId, taskId, "aiops_agent", "REQUEST_RECEIVED");
                episodicMemoryService.saveEvent(
                        userId,
                        sessionId,
                        taskId,
                        "aiops_agent",
                        "aiops_analysis_started",
                        "AIOps analysis task " + taskId + " started.",
                        Map.of("stage", "REQUEST_RECEIVED"),
                        0.7);

                DashScopeApi dashScopeApi = chatService.createDashScopeApi();
                DashScopeChatModel chatModel = DashScopeChatModel.builder()
                        .dashScopeApi(dashScopeApi)
                        .defaultOptions(DashScopeChatOptions.builder()
                                .withModel(DashScopeChatModel.DEFAULT_MODEL_NAME)
                                .withTemperature(0.3)
                                .withMaxToken(8000)
                                .withTopP(0.9)
                                .build())
                        .build();

                ToolCallback[] toolCallbacks = tools.getToolCallbacks();
                emitter.send(SseEmitter.event().name("message")
                        .data(SseMessage.content("taskId: " + taskId + "\n"), MediaType.APPLICATION_JSON));
                emitter.send(SseEmitter.event().name("message")
                        .data(SseMessage.content("正在读取告警并拆解任务...\n"), MediaType.APPLICATION_JSON));

                Optional<OverAllState> overAllStateOptional = aiOpsService.executeAiOpsAnalysis(
                        chatModel, toolCallbacks, userId, sessionId, taskId);
                if (overAllStateOptional.isEmpty()) {
                    agentTaskStateService.failTask(
                            userId, sessionId, taskId, "aiops_agent", "Supervisor returned empty state.");
                    episodicMemoryService.saveEvent(
                            userId,
                            sessionId,
                            taskId,
                            "aiops_agent",
                            "aiops_analysis_failed",
                            "AIOps analysis task " + taskId + " failed because Supervisor returned empty state.",
                            Map.of("stage", "SUPERVISOR_EMPTY_STATE"),
                            0.75);
                    emitter.send(SseEmitter.event().name("message")
                            .data(SseMessage.error("多 Agent 编排未获取到有效结果"), MediaType.APPLICATION_JSON));
                    emitter.complete();
                    return;
                }

                Optional<String> finalReportOptional = aiOpsService.extractFinalReport(overAllStateOptional.get());
                if (finalReportOptional.isPresent()) {
                    String finalReportText = finalReportOptional.get();
                    agentTaskStateService.finishTask(userId, sessionId, taskId, "aiops_agent", finalReportText);
                    episodicMemoryService.saveEvent(
                            userId,
                            sessionId,
                            taskId,
                            "aiops_agent",
                            "aiops_final_report_generated",
                            "AIOps analysis task " + taskId + " generated a final report.",
                            Map.of("stage", "FINISHED", "reportLength", finalReportText.length()),
                            0.85);
                    emitter.send(SseEmitter.event().name("message")
                            .data(SseMessage.content("\n\n" + "=".repeat(60) + "\n"), MediaType.APPLICATION_JSON));
                    emitter.send(SseEmitter.event().name("message")
                            .data(SseMessage.content("**告警分析报告**\n\n"), MediaType.APPLICATION_JSON));

                    int chunkSize = 50;
                    for (int i = 0; i < finalReportText.length(); i += chunkSize) {
                        int end = Math.min(i + chunkSize, finalReportText.length());
                        emitter.send(SseEmitter.event().name("message")
                                .data(SseMessage.content(finalReportText.substring(i, end)), MediaType.APPLICATION_JSON));
                    }

                    emitter.send(SseEmitter.event().name("message")
                            .data(SseMessage.content("\n" + "=".repeat(60) + "\n\n"), MediaType.APPLICATION_JSON));
                } else {
                    agentTaskStateService.failTask(
                            userId, sessionId, taskId, "aiops_agent", "Final report was not generated.");
                    episodicMemoryService.saveEvent(
                            userId,
                            sessionId,
                            taskId,
                            "aiops_agent",
                            "aiops_analysis_failed",
                            "AIOps analysis task " + taskId + " finished without a final report.",
                            Map.of("stage", "NO_FINAL_REPORT"),
                            0.75);
                    emitter.send(SseEmitter.event().name("message")
                            .data(SseMessage.content("多 Agent 流程已完成，但未能生成最终报告。"), MediaType.APPLICATION_JSON));
                }

                emitter.send(SseEmitter.event().name("message")
                        .data(SseMessage.done(), MediaType.APPLICATION_JSON));
                emitter.complete();
            } catch (Exception e) {
                logger.error("AI Ops failed", e);
                agentTaskStateService.failTask(userId, sessionId, taskId, "aiops_agent", e.getMessage());
                episodicMemoryService.saveEvent(
                        userId,
                        sessionId,
                        taskId,
                        "aiops_agent",
                        "aiops_analysis_failed",
                        "AIOps analysis task " + taskId + " failed: " + e.getMessage(),
                        Map.of("stage", "EXCEPTION"),
                        0.75);
                try {
                    emitter.send(SseEmitter.event().name("message")
                            .data(SseMessage.error("AI Ops 流程失败: " + e.getMessage()), MediaType.APPLICATION_JSON));
                } catch (IOException ex) {
                    logger.error("Failed to send AI Ops error", ex);
                }
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    @GetMapping("/ai_ops/task/{taskId}")
    public ResponseEntity<ApiResponse<AgentTaskState>> getAiOpsTaskState(
            @PathVariable String taskId,
            @RequestHeader(value = "X-User-Id", required = false) String headerUserId) {
        String userId = resolveUserId(headerUserId, null);
        return agentTaskStateService.findByUserAndTask(userId, taskId)
                .map(state -> ResponseEntity.ok(ApiResponse.success(state)))
                .orElseGet(() -> ResponseEntity.ok(ApiResponse.error("task not found")));
    }

    @GetMapping("/chat/session/{sessionId}")
    public ResponseEntity<ApiResponse<SessionInfoResponse>> getSessionInfo(
            @PathVariable String sessionId,
            @RequestHeader(value = "X-User-Id", required = false) String headerUserId) {
        try {
            String userId = resolveUserId(headerUserId, null);
            long messageCount = conversationMemoryService.countMessages(userId, sessionId);
            if (messageCount == 0) {
                return ResponseEntity.ok(ApiResponse.error("会话不存在"));
            }

            SessionInfoResponse response = new SessionInfoResponse();
            response.setSessionId(sessionId);
            response.setMessagePairCount((int) (messageCount / 2));
            response.setCreateTime(conversationMemoryService.getSessionCreatedAt(userId, sessionId)
                    .map(time -> time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
                    .orElse(System.currentTimeMillis()));
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (Exception e) {
            logger.error("Get session info failed", e);
            return ResponseEntity.ok(ApiResponse.error(e.getMessage()));
        }
    }

    private void handleStreamingOutput(NodeOutput output, SseEmitter emitter, StringBuilder fullAnswerBuilder) {
        try {
            if (output instanceof StreamingOutput streamingOutput) {
                OutputType type = streamingOutput.getOutputType();
                if (type == OutputType.AGENT_MODEL_STREAMING) {
                    String chunk = streamingOutput.message().getText();
                    if (chunk != null && !chunk.isEmpty()) {
                        fullAnswerBuilder.append(chunk);
                        emitter.send(SseEmitter.event().name("message")
                                .data(SseMessage.content(chunk), MediaType.APPLICATION_JSON));
                    }
                } else if (type == OutputType.AGENT_TOOL_FINISHED) {
                    logger.info("Agent tool finished: {}", output.node());
                }
            }
        } catch (IOException e) {
            logger.error("Failed to send streaming chunk", e);
            throw new RuntimeException(e);
        }
    }

    private void handleStreamingError(Throwable error, SseEmitter emitter) {
        logger.error("Streaming chat failed", error);
        try {
            emitter.send(SseEmitter.event().name("message")
                    .data(SseMessage.error(error.getMessage()), MediaType.APPLICATION_JSON));
        } catch (IOException ex) {
            logger.error("Failed to send streaming error", ex);
        }
        emitter.completeWithError(error);
    }

    private void handleStreamingComplete(
            String userId,
            String sessionId,
            String question,
            StringBuilder fullAnswerBuilder,
            DashScopeChatModel chatModel,
            SseEmitter emitter) {
        try {
            String fullAnswer = fullAnswerBuilder.toString();
            saveConversationTurn(userId, sessionId, question, fullAnswer);

            emitter.send(SseEmitter.event().name("message")
                    .data(SseMessage.done(), MediaType.APPLICATION_JSON));
            emitter.complete();
            runPostChatMemoryPipelineAsync(userId, sessionId, question, fullAnswer, chatModel);
        } catch (IOException e) {
            logger.error("Failed to complete streaming response", e);
            emitter.completeWithError(e);
        }
    }

    private void saveConversationTurn(String userId, String sessionId, String question, String answer) {
        conversationMemoryService.saveMessage(userId, sessionId, "user", question);
        conversationMemoryService.saveMessage(userId, sessionId, "assistant", answer == null ? "" : answer);
        logger.info("Saved conversation turn. userId={}, sessionId={}", userId, sessionId);
    }

    private void runPostChatMemoryPipelineAsync(
            String userId,
            String sessionId,
            String question,
            String answer,
            DashScopeChatModel chatModel) {
        executor.execute(() -> {
            summaryMemoryService.refreshSummaryIfNeeded(userId, sessionId, chatModel);
            longTermMemoryService.extractAndSaveAfterChat(userId, sessionId, question, answer, chatModel);
        });
    }

    private String normalizeSessionId(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return UUID.randomUUID().toString();
        }
        return sessionId;
    }

    private String resolveUserId(String headerUserId, String requestUserId) {
        if (headerUserId != null && !headerUserId.isBlank()) {
            return headerUserId.trim();
        }
        if (requestUserId != null && !requestUserId.isBlank()) {
            return requestUserId.trim();
        }
        return DEFAULT_USER_ID;
    }

    @Setter
    @Getter
    public static class ChatRequest {
        @JsonProperty(value = "Id")
        @JsonAlias({"id", "ID"})
        private String Id;

        @JsonProperty(value = "Question")
        @JsonAlias({"question", "QUESTION"})
        private String Question;

        @JsonProperty(value = "UserId")
        @JsonAlias({"userId", "user_id", "USER_ID"})
        private String UserId;
    }

    @Setter
    @Getter
    public static class ClearRequest {
        @JsonProperty(value = "Id")
        @JsonAlias({"id", "ID"})
        private String Id;

        @JsonProperty(value = "UserId")
        @JsonAlias({"userId", "user_id", "USER_ID"})
        private String UserId;
    }

    @Setter
    @Getter
    public static class SessionInfoResponse {
        private String sessionId;
        private int messagePairCount;
        private long createTime;
    }

    @Setter
    @Getter
    public static class ChatResponse {
        private boolean success;
        private String answer;
        private String errorMessage;

        public static ChatResponse success(String answer) {
            ChatResponse response = new ChatResponse();
            response.setSuccess(true);
            response.setAnswer(answer);
            return response;
        }

        public static ChatResponse error(String errorMessage) {
            ChatResponse response = new ChatResponse();
            response.setSuccess(false);
            response.setErrorMessage(errorMessage);
            return response;
        }
    }

    @Setter
    @Getter
    public static class SseMessage {
        private String type;
        private String data;

        public static SseMessage content(String data) {
            SseMessage message = new SseMessage();
            message.setType("content");
            message.setData(data);
            return message;
        }

        public static SseMessage error(String errorMessage) {
            SseMessage message = new SseMessage();
            message.setType("error");
            message.setData(errorMessage);
            return message;
        }

        public static SseMessage done() {
            SseMessage message = new SseMessage();
            message.setType("done");
            message.setData(null);
            return message;
        }
    }

    @Getter
    @Setter
    public static class ApiResponse<T> {
        private int code;
        private String message;
        private T data;

        public static <T> ApiResponse<T> success(T data) {
            ApiResponse<T> response = new ApiResponse<>();
            response.setCode(200);
            response.setMessage("success");
            response.setData(data);
            return response;
        }

        public static <T> ApiResponse<T> error(String message) {
            ApiResponse<T> response = new ApiResponse<>();
            response.setCode(500);
            response.setMessage(message);
            return response;
        }
    }
}

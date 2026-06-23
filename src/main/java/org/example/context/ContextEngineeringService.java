package org.example.context;

import org.example.memory.ConversationMemoryService;
import org.example.memory.SummaryMemoryService;
import org.example.memory.dto.ChatMessageDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class ContextEngineeringService {

    private static final Logger logger = LoggerFactory.getLogger(ContextEngineeringService.class);

    private final ConversationMemoryService conversationMemoryService;
    private final SummaryMemoryService summaryMemoryService;
    private final MemoryAdapter memoryAdapter;
    private final RagEvidenceAdapter ragEvidenceAdapter;
    private final ContextBuilder contextBuilder;
    private final ContextConfig config;

    public ContextEngineeringService(
            ConversationMemoryService conversationMemoryService,
            SummaryMemoryService summaryMemoryService,
            MemoryAdapter memoryAdapter,
            RagEvidenceAdapter ragEvidenceAdapter,
            ContextBuilder contextBuilder,
            ContextConfig config) {
        this.conversationMemoryService = conversationMemoryService;
        this.summaryMemoryService = summaryMemoryService;
        this.memoryAdapter = memoryAdapter;
        this.ragEvidenceAdapter = ragEvidenceAdapter;
        this.contextBuilder = contextBuilder;
        this.config = config;
    }

    public ContextBuildResult buildForChat(String userId, String sessionId, String query) {
        RuntimeContext runtimeContext = runtimeContext(userId, sessionId, null, "chat_agent", query, "default");
        ContextBuildRequest request = new ContextBuildRequest();
        request.setRuntimeContext(runtimeContext);
        request.setScenario(ContextBuildRequest.Scenario.CHAT);
        request.setRoleAndPolicies(chatRolePolicy());
        request.setTask("回答用户当前问题，并按需使用记忆、知识库证据和工具。当前问题：\n" + defaultText(query, ""));
        request.setOutputInstructions(chatOutputInstruction());
        request.setIncludeRag(shouldLoadRag(query));
        return build(request);
    }

    public ContextBuildResult buildForAiOps(AiOpsContext aiOpsContext, ContextBuildRequest.Scenario scenario) {
        String task = defaultText(aiOpsContext.getCurrentStep(), aiOpsContext.getTaskDescription());
        RuntimeContext runtimeContext = runtimeContext(
                aiOpsContext.getUserId(),
                aiOpsContext.getSessionId(),
                aiOpsContext.getTaskId(),
                defaultText(aiOpsContext.getAgentId(), "aiops_agent"),
                task,
                defaultText(aiOpsContext.getNamespace(), "default"));
        ContextBuildRequest request = new ContextBuildRequest();
        request.setRuntimeContext(runtimeContext);
        request.setScenario(scenario);
        request.setRoleAndPolicies(aiOpsRolePolicy(scenario));
        request.setTask(defaultText(task, "执行 AIOps 告警分析任务。"));
        request.setOutputInstructions(aiOpsOutputInstruction(scenario));
        request.setIncludeRag(true);
        request.setExternalPackets(aiOpsPackets(aiOpsContext));
        return build(request);
    }

    public ContextBuildResult build(ContextBuildRequest request) {
        ConversationState conversationState = loadConversationState(request);
        List<ContextPacket> packets = new ArrayList<>(request.getExternalPackets());
        RuntimeContext runtimeContext = request.getRuntimeContext();

        if (request.isIncludeMemory()) {
            try {
                packets.addAll(memoryAdapter.fetchRelevantMemories(runtimeContext));
            } catch (Exception e) {
                logger.warn("Memory context retrieval skipped. userId={}, error={}",
                        runtimeContext == null ? null : runtimeContext.getUserId(), e.getMessage());
            }
        }
        if (request.isIncludeRag()) {
            packets.addAll(ragEvidenceAdapter.fetchEvidence(runtimeContext));
        }
        return contextBuilder.build(request, conversationState, packets);
    }

    private ConversationState loadConversationState(ContextBuildRequest request) {
        ConversationState state = new ConversationState();
        RuntimeContext runtime = request.getRuntimeContext();
        if (runtime == null || isBlank(runtime.getUserId()) || isBlank(runtime.getSessionId())
                || !request.isIncludeConversation()) {
            return state;
        }
        summaryMemoryService.getSummary(runtime.getUserId(), runtime.getSessionId())
                .ifPresent(state::setSessionSummary);
        List<ChatMessageDTO> recentMessages = conversationMemoryService.getRecentMessages(
                runtime.getUserId(), runtime.getSessionId(), config.getRecentRounds());
        state.setRecentMessages(recentMessages);
        state.setMessageCount(conversationMemoryService.countMessages(runtime.getUserId(), runtime.getSessionId()));
        return state;
    }

    private List<ContextPacket> aiOpsPackets(AiOpsContext aiOpsContext) {
        List<ContextPacket> packets = new ArrayList<>();
        if (aiOpsContext == null) {
            return packets;
        }
        for (AiOpsEvidence evidence : aiOpsContext.getEvidence()) {
            ContextPacket packet = new ContextPacket();
            packet.setType(ContextSourceType.RAG_EVIDENCE);
            packet.setSourceId(evidence.getEvidenceId());
            packet.setTitle(defaultText(evidence.getTitle(), evidence.getSource()));
            packet.setContent("""
                    <evidence source="%s" title="%s" chunk="%s" score="%.3f">
                    %s
                    </evidence>
                    """.formatted(
                    defaultText(evidence.getSource(), "aiops"),
                    defaultText(evidence.getTitle(), "evidence"),
                    defaultText(evidence.getEvidenceId(), ""),
                    evidence.getScore(),
                    defaultText(evidence.getContent(), "")));
            packet.setMetadata(evidence.getMetadata());
            packet.setRelevanceScore(evidence.getScore());
            packet.setRecencyScore(1.0);
            packet.setImportanceScore(0.8);
            packets.add(packet);
        }
        for (AiOpsStep step : aiOpsContext.getSteps()) {
            ContextPacket packet = new ContextPacket();
            packet.setType(ContextSourceType.AGENT_STATE);
            packet.setSourceId(step.getName());
            packet.setTitle("step-" + step.getOrder() + ":" + defaultText(step.getName(), "unnamed"));
            packet.setContent("""
                    step=%s
                    status=%s
                    objective=%s
                    result=%s
                    evidenceIds=%s
                    """.formatted(
                    defaultText(step.getName(), ""),
                    defaultText(step.getStatus(), ""),
                    defaultText(step.getObjective(), ""),
                    defaultText(step.getResult(), ""),
                    step.getEvidenceIds()));
            packet.setRelevanceScore(0.8);
            packet.setRecencyScore(1.0);
            packet.setImportanceScore(0.8);
            packets.add(packet);
        }
        return packets;
    }

    private RuntimeContext runtimeContext(
            String userId,
            String sessionId,
            String taskId,
            String agentId,
            String query,
            String namespace) {
        RuntimeContext runtimeContext = new RuntimeContext();
        runtimeContext.setUserId(defaultText(userId, "default_user"));
        runtimeContext.setSessionId(sessionId);
        runtimeContext.setTaskId(taskId);
        runtimeContext.setAgentId(agentId);
        runtimeContext.setQuery(query);
        runtimeContext.setNamespace(defaultText(namespace, "default"));
        return runtimeContext;
    }

    private boolean shouldLoadRag(String query) {
        if (isBlank(query)) {
            return false;
        }
        String lower = query.toLowerCase(Locale.ROOT);
        return containsAny(lower,
                "文档", "知识库", "手册", "配置", "错误", "故障", "异常", "排查", "告警", "日志",
                "服务", "依赖", "cpu", "内存", "磁盘", "超时", "error", "exception", "runbook");
    }

    private String chatRolePolicy() {
        return """
                你是 SuperBizAgent 智能助手。
                - 区分 RuntimeContext、ConversationState、LongTermMemory，不要混淆用户、session 或 task。
                - 最近对话优先使用原文，旧历史只依赖 Conversation Summary。
                - Memory 是长期偏好、项目状态和稳定事实；如果和当前用户输入冲突，以当前输入为准。
                - Evidence 是知识库/RAG 检索证据；涉及事实、配置、排障、手册和告警时必须优先依据 Evidence。
                - Tool Results 是工具压缩结果；不要把没有进入上下文的原始工具输出当成事实。
                - 不确定时说明缺口，不要编造。
                """;
    }

    private String chatOutputInstruction() {
        return """
                用中文回答用户。
                如果使用 Evidence，请自然说明依据；如果 Evidence 不足，请明确说知识库证据不足。
                回答要聚焦当前任务，不要暴露内部上下文调度细节。
                """;
    }

    private String aiOpsRolePolicy(ContextBuildRequest.Scenario scenario) {
        return """
                你是 SuperBizAgent AIOps 上下文工程层中的 %s。
                - Planner、Executor、Replanner、FinalReporter 必须基于 Evidence 和 Tool Results 工作。
                - 日志、告警、文档工具结果只能作为压缩后的 summary/keyFindings/evidence 进入上下文。
                - 严禁编造指标、日志、告警和根因；证据不足时必须说明无法完成的原因。
                - 当前 taskId 只允许读取本任务、本用户和本 session 的上下文。
                """.formatted(scenario.name());
    }

    private String aiOpsOutputInstruction(ContextBuildRequest.Scenario scenario) {
        if (scenario == ContextBuildRequest.Scenario.AIOPS_REPORTER) {
            return "输出最终 Markdown 报告；每个根因和建议都必须能追溯到 Evidence。";
        }
        if (scenario == ContextBuildRequest.Scenario.AIOPS_EXECUTOR) {
            return "只执行当前步骤需要的工具调用，返回结构化 summary、keyFindings、evidence，不输出大段原文。";
        }
        return "输出下一步计划或重规划结论，明确需要的工具、参数、预期证据和停止条件。";
    }

    private boolean containsAny(String value, String... needles) {
        for (String needle : needles) {
            if (value.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}

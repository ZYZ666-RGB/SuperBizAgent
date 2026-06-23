package org.example.context;

import org.example.memory.dto.ChatMessageDTO;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class ContextBuilder {

    private final ContextConfig config;
    private final ContextCompressor compressor;

    public ContextBuilder(ContextConfig config, ContextCompressor compressor) {
        this.config = config;
        this.compressor = compressor;
    }

    public ContextBuildResult build(
            ContextBuildRequest request,
            ConversationState conversationState,
            List<ContextPacket> packets) {
        List<ContextPacket> selected = new ArrayList<>();
        List<ContextPacket> dropped = new ArrayList<>();
        List<ContextPacket> scoredPackets = scorePackets(packets == null ? List.of() : packets);

        StringBuilder builder = new StringBuilder();
        appendSection(builder, "Role & Policies", defaultText(request.getRoleAndPolicies(), defaultRolePolicy()));
        appendSection(builder, "Task", defaultText(request.getTask(), runtimeQuery(request)));
        appendSection(builder, "Conversation Summary", conversationSummary(conversationState));
        appendSection(builder, "Recent Conversation", recentConversation(conversationState, request));
        appendSection(builder, "Memory", packetSection(
                scoredPackets, ContextSourceType.MEMORY, config.getMaxMemoryTokens(), selected, dropped));
        appendSection(builder, "Evidence", packetSection(
                scoredPackets, ContextSourceType.RAG_EVIDENCE, config.getMaxRagTokens(), selected, dropped));
        appendSection(builder, "Tool Results", packetSection(
                scoredPackets, ContextSourceType.TOOL_RESULT, config.getMaxToolResultTokens(), selected, dropped));
        appendSection(builder, "Agent State", agentState(conversationState, scoredPackets, selected, dropped));
        appendSection(builder, "Output", defaultText(request.getOutputInstructions(), defaultOutputInstruction()));

        String finalContext = compressor.fitText(builder.toString(), config.inputBudgetTokens());
        ContextBuildResult result = new ContextBuildResult();
        result.setRuntimeContext(request.getRuntimeContext());
        result.setConversationState(conversationState);
        result.setFinalContext(finalContext);
        result.setSelectedPackets(selected);
        result.setDroppedPackets(dropped);
        result.setEstimatedTokens(compressor.estimate(finalContext));
        return result;
    }

    List<ContextPacket> scorePackets(List<ContextPacket> packets) {
        List<ContextPacket> scored = new ArrayList<>();
        for (ContextPacket packet : packets) {
            double score = config.getRelevanceWeight() * clamp(packet.getRelevanceScore())
                    + config.getRecencyWeight() * clamp(packet.getRecencyScore())
                    + config.getImportanceWeight() * clamp(packet.getImportanceScore());
            packet.setFinalScore(score);
            if (packet.getTokenEstimate() <= 0) {
                packet.setTokenEstimate(compressor.estimate(defaultText(packet.getContent(), packet.getSummary())));
            }
            scored.add(packet);
        }
        scored.sort(Comparator.comparingDouble(ContextPacket::getFinalScore).reversed());
        return scored;
    }

    private String packetSection(
            List<ContextPacket> packets,
            ContextSourceType type,
            int budget,
            List<ContextPacket> selected,
            List<ContextPacket> dropped) {
        List<ContextPacket> candidates = packets.stream()
                .filter(packet -> packet.getType() == type)
                .collect(Collectors.toList());
        if (candidates.isEmpty()) {
            return "无";
        }

        StringBuilder section = new StringBuilder();
        int used = 0;
        for (ContextPacket packet : candidates) {
            if (requiresRelevance(type) && packet.getRelevanceScore() < config.getMinRelevance()) {
                dropped.add(packet);
                continue;
            }
            int remaining = Math.max(0, budget - used);
            if (remaining <= 0) {
                dropped.add(packet);
                continue;
            }
            String content = compressor.compressPacket(packet, remaining);
            int tokens = compressor.estimate(content);
            if (tokens > remaining && config.isEnableCompression()) {
                content = compressor.fitText(content, remaining);
                tokens = compressor.estimate(content);
            }
            if (tokens > remaining) {
                dropped.add(packet);
                continue;
            }
            section.append("- source=")
                    .append(defaultText(packet.getTitle(), defaultText(packet.getSourceId(), type.name())))
                    .append(", score=")
                    .append(String.format(java.util.Locale.ROOT, "%.3f", packet.getFinalScore()))
                    .append("\n")
                    .append(content)
                    .append("\n\n");
            used += tokens;
            selected.add(packet);
        }
        return section.isEmpty() ? "无" : section.toString().stripTrailing();
    }

    private String agentState(
            ConversationState state,
            List<ContextPacket> packets,
            List<ContextPacket> selected,
            List<ContextPacket> dropped) {
        String packetState = packetSection(packets, ContextSourceType.AGENT_STATE, 600, selected, dropped);
        if (!"无".equals(packetState)) {
            return packetState;
        }
        if (state == null || state.getAgentState().isEmpty()) {
            return "无";
        }
        return state.getAgentState().toString();
    }

    private String recentConversation(ConversationState state, ContextBuildRequest request) {
        if (request != null && !request.isIncludeConversation()) {
            return "无";
        }
        if (state == null || state.getRecentMessages().isEmpty()) {
            return "无";
        }
        StringBuilder builder = new StringBuilder();
        for (ChatMessageDTO message : state.getRecentMessages()) {
            builder.append(formatRole(message.getRole()))
                    .append(": ")
                    .append(defaultText(message.getContent(), ""))
                    .append("\n");
        }
        return compressor.fitText(builder.toString().stripTrailing(), config.getMaxHistoryTokens());
    }

    private String conversationSummary(ConversationState state) {
        if (state == null || isBlank(state.getSessionSummary())) {
            return "无";
        }
        return state.getSessionSummary();
    }

    private String runtimeQuery(ContextBuildRequest request) {
        if (request == null || request.getRuntimeContext() == null) {
            return "回答用户当前请求。";
        }
        return defaultText(request.getRuntimeContext().getQuery(), "回答用户当前请求。");
    }

    private void appendSection(StringBuilder builder, String title, String content) {
        builder.append("[")
                .append(title)
                .append("]\n")
                .append(defaultText(content, "无"))
                .append("\n\n");
    }

    private boolean requiresRelevance(ContextSourceType type) {
        return type == ContextSourceType.MEMORY || type == ContextSourceType.RAG_EVIDENCE;
    }

    private String formatRole(String role) {
        if ("user".equals(role)) {
            return "用户";
        }
        if ("assistant".equals(role)) {
            return "助手";
        }
        if ("tool".equals(role)) {
            return "工具";
        }
        return "系统";
    }

    private String defaultRolePolicy() {
        return "你是 SuperBizAgent 智能助手。只使用当前上下文、可用工具和明确证据回答；如果证据不足，说明不确定性。";
    }

    private String defaultOutputInstruction() {
        return "用中文回答。涉及事实、文档、日志、告警或运维结论时，优先引用 Evidence；不要编造上下文中不存在的信息。";
    }

    private double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}

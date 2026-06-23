package org.example.service;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.agent.tool.DateTimeTools;
import org.example.agent.tool.InternalDocsTools;
import org.example.agent.tool.QueryLogsTools;
import org.example.agent.tool.QueryMetricsTools;
import org.example.aiops.AiOpsDecision;
import org.example.aiops.AiOpsDecisionParser;
import org.example.aiops.AiOpsDecisionType;
import org.example.aiops.AiOpsExecutionResult;
import org.example.aiops.AiOpsWorkflowStage;
import org.example.aiops.AiOpsWorkflowState;
import org.example.context.AiOpsContext;
import org.example.context.AiOpsEvidence;
import org.example.context.AiOpsStep;
import org.example.context.ContextBuildRequest;
import org.example.context.ContextEngineeringService;
import org.example.memory.task.AgentTaskStateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * AI Ops 智能运维服务
 * 负责多 Agent 协作的告警分析流程
 */
@Service
public class AiOpsService {

    private static final Logger logger = LoggerFactory.getLogger(AiOpsService.class);
    private static final int MAX_WORKFLOW_ITERATIONS = 6;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Autowired
    private DateTimeTools dateTimeTools;

    @Autowired
    private InternalDocsTools internalDocsTools;

    @Autowired
    private QueryMetricsTools queryMetricsTools;

    @Autowired(required = false)  // Mock 模式下才注册
    private QueryLogsTools queryLogsTools;

    @Autowired
    private AgentTaskStateService agentTaskStateService;

    @Autowired
    private ContextEngineeringService contextEngineeringService;

    @Autowired
    private AiOpsDecisionParser aiOpsDecisionParser;

    /**
     * 执行 AI Ops 告警分析流程
     *
     * @param chatModel      大模型实例
     * @param toolCallbacks  工具回调数组
     * @return 分析结果状态
     * @throws GraphRunnerException 如果 Agent 执行失败
     */
    public Optional<OverAllState> executeAiOpsAnalysis(DashScopeChatModel chatModel, ToolCallback[] toolCallbacks) throws GraphRunnerException {
        return executeAiOpsAnalysis(
                chatModel,
                toolCallbacks,
                "default_user",
                null,
                UUID.randomUUID().toString());
    }

    public Optional<OverAllState> executeAiOpsAnalysis(
            DashScopeChatModel chatModel,
            ToolCallback[] toolCallbacks,
            String userId,
            String sessionId,
            String taskId) throws GraphRunnerException {
        logger.info("开始执行 AI Ops 多 Agent 协作流程");
        agentTaskStateService.startTask(userId, sessionId, taskId, "aiops_agent", "SUPERVISOR_STARTED");
        String taskPrompt = "你是企业级 SRE，接到了自动化告警排查任务。请结合工具调用，执行**规划→执行→再规划**的闭环，并最终按照固定模板输出《告警分析报告》。禁止编造虚假数据，如连续多次查询失败需诚实反馈无法完成的原因。";

        AiOpsWorkflowState workflowState = newWorkflowState(userId, sessionId, taskId);

        logger.info("显式 AIOps 状态机开始编排...");
        try {
            for (int i = 1; i <= MAX_WORKFLOW_ITERATIONS; i++) {
                workflowState.setIteration(i);
                workflowState.setStage(i == 1 ? AiOpsWorkflowStage.PLANNING : AiOpsWorkflowStage.REPLANNING);

                ReactAgent plannerAgent = buildPlannerAgent(
                        chatModel, userId, sessionId, taskId, taskPrompt, workflowState);
                String plannerOutput = callAgent(plannerAgent, buildPlannerInput(taskPrompt, workflowState));
                workflowState.setLastPlannerOutput(plannerOutput);
                AiOpsDecision decision = aiOpsDecisionParser.parseDecision(plannerOutput);
                logger.info("AIOps planner decision. taskId={}, iteration={}, decision={}",
                        taskId, i, decision.getDecision());

                if (decision.getDecision() == AiOpsDecisionType.FAILED) {
                    workflowState.setStage(AiOpsWorkflowStage.FAILED);
                    workflowState.setFailureReason(defaultText(decision.getFailureReason(), decision.getRationale()));
                    workflowState.setFinalReport(buildUnableToCompleteReport(workflowState));
                    saveWorkflowSnapshot(workflowState);
                    return Optional.of(toOverallState(workflowState));
                }

                if (decision.getDecision() == AiOpsDecisionType.FINISH) {
                    workflowState.setStage(AiOpsWorkflowStage.REPORTING);
                    ReactAgent finalReporterAgent = buildFinalReporterAgent(
                            chatModel, userId, sessionId, taskId, taskPrompt, workflowState);
                    String finalReport = callAgent(finalReporterAgent, buildFinalReporterInput(taskPrompt, workflowState));
                    workflowState.setFinalReport(finalReport);
                    workflowState.setStage(AiOpsWorkflowStage.FINISHED);
                    saveWorkflowSnapshot(workflowState);
                    return Optional.of(toOverallState(workflowState));
                }

                AiOpsStep step = addStep(workflowState, decision);
                if (decision.getDecision() == AiOpsDecisionType.PLAN) {
                    step.setStatus("PLANNED");
                    step.setResult(defaultText(decision.getRationale(), "Planner produced a plan and requested replanning."));
                    saveWorkflowSnapshot(workflowState);
                    continue;
                }

                workflowState.setStage(AiOpsWorkflowStage.EXECUTING);
                step.setStatus("RUNNING");
                ReactAgent executorAgent = buildExecutorAgent(
                        chatModel, toolCallbacks, userId, sessionId, taskId, taskPrompt, workflowState);
                String executorOutput = callAgent(executorAgent, buildExecutorInput(decision, workflowState));
                workflowState.setLastExecutorOutput(executorOutput);
                AiOpsExecutionResult executionResult = aiOpsDecisionParser.parseExecutionResult(executorOutput);
                AiOpsEvidence evidence = addEvidence(workflowState, decision, executionResult);
                step.getEvidenceIds().add(evidence.getEvidenceId());
                step.setStatus(defaultText(executionResult.getStatus(), "UNKNOWN"));
                step.setResult(defaultText(executionResult.getSummary(), executorOutput));
                saveWorkflowSnapshot(workflowState);
            }

            workflowState.setStage(AiOpsWorkflowStage.REPORTING);
            workflowState.setFailureReason("AIOps workflow reached max iteration limit: " + MAX_WORKFLOW_ITERATIONS);
            ReactAgent finalReporterAgent = buildFinalReporterAgent(
                    chatModel, userId, sessionId, taskId, taskPrompt, workflowState);
            String finalReport = callAgent(finalReporterAgent, buildFinalReporterInput(taskPrompt, workflowState));
            workflowState.setFinalReport(finalReport);
            workflowState.setStage(AiOpsWorkflowStage.FINISHED);
            saveWorkflowSnapshot(workflowState);
            return Optional.of(toOverallState(workflowState));
        } catch (GraphRunnerException e) {
            agentTaskStateService.failTask(userId, sessionId, taskId, "aiops_agent", e.getMessage());
            throw e;
        } catch (RuntimeException e) {
            agentTaskStateService.failTask(userId, sessionId, taskId, "aiops_agent", e.getMessage());
            throw e;
        }
    }

    /**
     * 从执行结果中提取最终报告文本
     *
     * @param state 执行状态
     * @return 报告文本（如果存在）
     */
    public Optional<String> extractFinalReport(OverAllState state) {
        logger.info("开始提取最终报告...");

        Optional<AssistantMessage> finalReportOutput = state.value("final_report")
                .filter(AssistantMessage.class::isInstance)
                .map(AssistantMessage.class::cast);
        if (finalReportOutput.isPresent()) {
            String reportText = finalReportOutput.get().getText();
            logger.info("成功提取到 FinalReporter 报告，长度: {}", reportText.length());
            return Optional.of(reportText);
        }

        Optional<String> finalReportText = state.value("final_report", String.class);
        if (finalReportText.isPresent() && !finalReportText.get().isBlank()) {
            logger.info("成功提取到 final_report 文本，长度: {}", finalReportText.get().length());
            return finalReportText;
        }

        // 提取 Planner 最终输出（包含完整的告警分析报告）
        Optional<AssistantMessage> plannerFinalOutput = state.value("planner_plan")
                .filter(AssistantMessage.class::isInstance)
                .map(AssistantMessage.class::cast);

        if (plannerFinalOutput.isPresent()) {
            String reportText = plannerFinalOutput.get().getText();
            logger.info("成功提取到 Planner 最终报告，长度: {}", reportText.length());
            return Optional.of(reportText);
        } else {
            logger.warn("未能提取到 Planner 最终报告");
            return Optional.empty();
        }
    }

    /**
     * 构建 Planner Agent
     */
    private ReactAgent buildPlannerAgent(
            DashScopeChatModel chatModel,
            String userId,
            String sessionId,
            String taskId,
            String taskPrompt,
            AiOpsWorkflowState workflowState) {
        return ReactAgent.builder()
                .name("planner_agent")
                .description("负责拆解告警、规划与再规划步骤")
                .model(chatModel)
                .systemPrompt(buildAiOpsSystemPrompt(
                        userId, sessionId, taskId, "planner_agent", taskPrompt,
                        ContextBuildRequest.Scenario.AIOPS_PLANNER, workflowState)
                        + "\n\n" + buildPlannerPrompt())
                .methodTools(new Object[]{})
                .tools(new ToolCallback[0])
                .outputKey("planner_plan")
                .build();
    }

    /**
     * 构建 Executor Agent
     */
    private ReactAgent buildExecutorAgent(
            DashScopeChatModel chatModel,
            ToolCallback[] toolCallbacks,
            String userId,
            String sessionId,
            String taskId,
            String taskPrompt,
            AiOpsWorkflowState workflowState) {
        return ReactAgent.builder()
                .name("executor_agent")
                .description("负责执行 Planner 的首个步骤并及时反馈")
                .model(chatModel)
                .systemPrompt(buildAiOpsSystemPrompt(
                        userId, sessionId, taskId, "executor_agent", taskPrompt,
                        ContextBuildRequest.Scenario.AIOPS_EXECUTOR, workflowState)
                        + "\n\n" + buildExecutorPrompt())
                .methodTools(buildMethodToolsArray())
                .tools(toolCallbacks)
                .outputKey("executor_feedback")
                .build();
    }

    private ReactAgent buildFinalReporterAgent(
            DashScopeChatModel chatModel,
            String userId,
            String sessionId,
            String taskId,
            String taskPrompt,
            AiOpsWorkflowState workflowState) {
        return ReactAgent.builder()
                .name("final_reporter_agent")
                .description("负责基于证据生成最终告警分析报告")
                .model(chatModel)
                .systemPrompt(buildAiOpsSystemPrompt(
                        userId, sessionId, taskId, "final_reporter_agent", taskPrompt,
                        ContextBuildRequest.Scenario.AIOPS_REPORTER, workflowState)
                        + "\n\n" + buildFinalReporterPrompt())
                .methodTools(new Object[]{})
                .tools(new ToolCallback[0])
                .outputKey("final_report")
                .build();
    }

    private String buildAiOpsSystemPrompt(
            String userId,
            String sessionId,
            String taskId,
            String agentId,
            String taskPrompt,
            ContextBuildRequest.Scenario scenario,
            AiOpsWorkflowState workflowState) {
        AiOpsContext aiOpsContext = new AiOpsContext();
        aiOpsContext.setUserId(userId);
        aiOpsContext.setSessionId(sessionId);
        aiOpsContext.setTaskId(taskId);
        aiOpsContext.setAgentId(agentId);
        aiOpsContext.setTaskDescription(taskPrompt);
        aiOpsContext.setCurrentStep(taskPrompt);
        aiOpsContext.setEvidence(workflowState == null ? List.of() : workflowState.getEvidence());
        aiOpsContext.setSteps(workflowState == null ? List.of() : workflowState.getSteps());
        return contextEngineeringService.buildForAiOps(aiOpsContext, scenario).getFinalContext();
    }

    /**
     * 动态构建方法工具数组
     * 根据 cls.mock-enabled 决定是否包含 QueryLogsTools
     */
    private Object[] buildMethodToolsArray() {
        if (queryLogsTools != null) {
            // Mock 模式：包含 QueryLogsTools
            return new Object[]{dateTimeTools, internalDocsTools, queryMetricsTools, queryLogsTools};
        } else {
            // 真实模式：不包含 QueryLogsTools（由 MCP 提供日志查询功能）
            return new Object[]{dateTimeTools, internalDocsTools, queryMetricsTools};
        }
    }

    private String callAgent(ReactAgent agent, String input) throws GraphRunnerException {
        AssistantMessage response = agent.call(input);
        return response == null ? "" : defaultText(response.getText(), "");
    }

    private AiOpsWorkflowState newWorkflowState(String userId, String sessionId, String taskId) {
        AiOpsWorkflowState state = new AiOpsWorkflowState();
        state.setUserId(defaultText(userId, "default_user"));
        state.setSessionId(sessionId);
        state.setTaskId(taskId);
        state.setStage(AiOpsWorkflowStage.PLANNING);
        return state;
    }

    private AiOpsStep addStep(AiOpsWorkflowState workflowState, AiOpsDecision decision) {
        AiOpsStep step = new AiOpsStep();
        step.setOrder(workflowState.getSteps().size() + 1);
        step.setName(defaultText(decision.getStep(), "AIOps step " + step.getOrder()));
        step.setObjective(defaultText(decision.getExpectedEvidence(), decision.getRationale()));
        step.setStatus("PENDING");
        workflowState.getSteps().add(step);
        return step;
    }

    private AiOpsEvidence addEvidence(
            AiOpsWorkflowState workflowState,
            AiOpsDecision decision,
            AiOpsExecutionResult executionResult) {
        AiOpsEvidence evidence = new AiOpsEvidence();
        evidence.setEvidenceId("evidence-" + (workflowState.getEvidence().size() + 1));
        evidence.setSource(defaultText(decision.getToolName(), "executor_agent"));
        evidence.setTitle(defaultText(decision.getStep(), "AIOps execution evidence"));
        evidence.setContent(formatExecutionEvidence(executionResult));
        evidence.setScore("SUCCESS".equalsIgnoreCase(executionResult.getStatus()) ? 0.85 : 0.45);
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("status", executionResult.getStatus());
        metadata.put("toolName", decision.getToolName());
        metadata.put("toolParameters", decision.getToolParameters());
        metadata.put("nextHint", executionResult.getNextHint());
        metadata.put("iteration", workflowState.getIteration());
        evidence.setMetadata(metadata);
        workflowState.getEvidence().add(evidence);
        return evidence;
    }

    private String formatExecutionEvidence(AiOpsExecutionResult result) {
        StringBuilder builder = new StringBuilder();
        builder.append("summary: ").append(defaultText(result.getSummary(), "无")).append("\n");
        if (!result.getKeyFindings().isEmpty()) {
            builder.append("keyFindings:\n");
            for (String finding : result.getKeyFindings()) {
                builder.append("- ").append(finding).append("\n");
            }
        }
        builder.append("evidence:\n").append(defaultText(result.getEvidence(), result.getRawText()));
        return builder.toString().stripTrailing();
    }

    private String buildPlannerInput(String taskPrompt, AiOpsWorkflowState state) {
        return """
                当前任务：
                %s

                当前 workflow stage: %s
                当前 iteration: %d
                已有步骤数: %d
                已有 evidence 数: %d
                最近 Executor 反馈：
                %s

                请只输出一个 JSON 对象，字段必须符合 Planner schema。
                """.formatted(
                taskPrompt,
                state.getStage(),
                state.getIteration(),
                state.getSteps().size(),
                state.getEvidence().size(),
                defaultText(state.getLastExecutorOutput(), "无"));
    }

    private String buildExecutorInput(AiOpsDecision decision, AiOpsWorkflowState state) {
        return """
                请执行 Planner 给出的当前步骤，不要执行额外步骤。

                step: %s
                rationale: %s
                expectedEvidence: %s
                suggestedTool: %s
                suggestedToolParameters: %s
                iteration: %d

                执行完成后只输出 JSON，包含 status、summary、keyFindings、evidence、nextHint。
                """.formatted(
                defaultText(decision.getStep(), ""),
                defaultText(decision.getRationale(), ""),
                defaultText(decision.getExpectedEvidence(), ""),
                defaultText(decision.getToolName(), ""),
                decision.getToolParameters(),
                state.getIteration());
    }

    private String buildFinalReporterInput(String taskPrompt, AiOpsWorkflowState state) {
        return """
                请基于 Evidence Ledger 输出最终告警分析报告。

                原始任务：
                %s

                workflowStage: %s
                failureReason: %s
                evidenceCount: %d
                stepsCount: %d

                只允许引用 Evidence Ledger 中出现的证据；如果证据不足，报告必须明确说明。
                """.formatted(
                taskPrompt,
                state.getStage(),
                defaultText(state.getFailureReason(), "无"),
                state.getEvidence().size(),
                state.getSteps().size());
    }

    private OverAllState toOverallState(AiOpsWorkflowState workflowState) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("planner_plan", new AssistantMessage(defaultText(workflowState.getLastPlannerOutput(), "")));
        data.put("executor_feedback", new AssistantMessage(defaultText(workflowState.getLastExecutorOutput(), "")));
        data.put("final_report", new AssistantMessage(defaultText(workflowState.getFinalReport(), "")));
        data.put("aiops_workflow_stage", workflowState.getStage().name());
        data.put("aiops_workflow_state", serialize(workflowState));
        data.put("aiops_evidence", serialize(workflowState.getEvidence()));
        return new OverAllState(data);
    }

    private void saveWorkflowSnapshot(AiOpsWorkflowState workflowState) {
        agentTaskStateService.saveSnapshot(
                workflowState.getUserId(),
                workflowState.getSessionId(),
                workflowState.getTaskId(),
                "aiops_agent",
                workflowState.getStage().name(),
                workflowState.getLastPlannerOutput(),
                workflowState.getLastExecutorOutput(),
                serialize(workflowState));
    }

    private String buildUnableToCompleteReport(AiOpsWorkflowState workflowState) {
        return """
                # 告警分析报告

                ---

                ## 结论

                任务无法完成。

                ### 失败原因
                %s

                ### 已收集证据数量
                %d

                ### 已执行步骤数量
                %d

                ### 后续建议
                1. 检查工具可用性与参数。
                2. 补充必要的告警、日志或文档证据后重新执行。
                """.formatted(
                defaultText(workflowState.getFailureReason(), "未获取到足够证据。"),
                workflowState.getEvidence().size(),
                workflowState.getSteps().size());
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "{\"serializationError\":\"" + e.getMessage().replace("\"", "'") + "\"}";
        }
    }

    /**
     * 构建 Planner Agent 系统提示词
     */
    private String buildPlannerPrompt() {
        return """
                你是 Planner Agent，同时承担 Replanner 角色，负责：
                1. 读取当前输入任务 {input} 以及 Executor 的最近反馈 {executor_feedback}。
                2. 分析已有 Evidence、Agent State、Prometheus 告警摘要、日志摘要、内部文档证据，制定可执行的下一步步骤。
                3. 你不能直接调用工具；需要工具时，只能输出 decision=EXECUTE 并指定工具建议，由 Executor 执行。
                4. 调用任何腾讯云日志/主题相关工具时，region 参数必须使用连字符格式（如 ap-guangzhou），若不确定请省略以使用默认值。
                5. 严格禁止编造数据，只能引用工具返回的真实内容；如果连续 3 次调用同一工具仍失败或返回空结果，需停止该方向并在最终报告的结论部分说明"无法完成"的原因。

                ## Planner 输出 JSON Schema（CRITICAL）
                你每次只能输出一个 JSON 对象，不要输出 Markdown，不要包裹 ```。
                {
                  "decision": "PLAN|EXECUTE|FINISH|FAILED",
                  "step": "下一步要执行的单一步骤；decision=FINISH 时说明为什么证据已足够",
                  "rationale": "为什么这样做",
                  "expectedEvidence": "期望 Executor 返回的证据",
                  "toolName": "建议工具名，可为空",
                  "toolParameters": {
                    "region": "ap-guangzhou",
                    "logTopic": "application-logs",
                    "query": "level:ERROR"
                  },
                  "failureReason": "decision=FAILED 时填写"
                }
                """;
    }

    /**
     * 构建 Executor Agent 系统提示词
     */
    private String buildExecutorPrompt() {
        return """
                你是 Executor Agent，负责读取 Planner 最新输出 {planner_plan}，只执行其中的第一步。
                - 确认步骤所需的工具与参数，尤其是 region 参数要使用连字符格式（ap-guangzhou）；若 Planner 未给出则使用默认区域。
                - 调用相应的工具并收集结果，如工具返回错误或空数据，需要将失败原因、请求参数一并记录，并停止进一步调用该工具（同一工具失败达到 3 次时应直接返回 FAILED）。
                - 将日志、指标、文档等证据整理成结构化摘要，标注对应的告警名称或资源，方便 Planner 填充"告警根因分析 / 处理方案执行"章节。
                - 以 JSON 形式返回执行状态、证据以及给 Planner 的建议，写入 executor_feedback，严禁编造未实际查询到的内容。
                - 不要返回大段原始日志，必须压缩成 summary、keyFindings、evidence。


                输出示例：
                {
                  "status": "SUCCESS",
                  "summary": "近1小时未见 error 日志，仅有 info",
                  "keyFindings": ["未发现 ERROR", "P99 响应时间升高与数据库慢查询相关"],
                  "evidence": "...",
                  "nextHint": "建议转向高占用进程"
                }
                """;
    }

    /**
     * 构建 FinalReporter Agent 系统提示词
     */
    private String buildFinalReporterPrompt() {
        return """
                你是 FinalReporter Agent，只负责基于 Evidence Ledger 输出最终报告。
                - 不允许调用工具。
                - 不允许使用 Evidence Ledger 之外的数据。
                - 每个根因、处理建议和风险判断都必须能追溯到上下文中的 Evidence。
                - 如果证据不足，必须明确说明，不要补全虚构细节。

                输出纯 Markdown，必须从 "# 告警分析报告" 开始，遵循：
                # 告警分析报告
                ---
                ## 活跃告警清单
                ## 告警根因分析
                ## 处理方案执行
                ## 结论
                ## 证据索引

                """;
    }

    private String defaultText(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value;
    }
}

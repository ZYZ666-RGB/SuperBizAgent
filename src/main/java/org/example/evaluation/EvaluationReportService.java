package org.example.evaluation;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.example.context.ContextBuildResult;
import org.example.context.ContextEngineeringService;
import org.example.context.ContextSourceType;
import org.example.memory.LongTermMemoryService;
import org.example.memory.UserMemory;
import org.example.rag.index.AdvancedRagOfflineIndexService;
import org.example.rag.model.IndexResult;
import org.example.rag.online.AdvancedRagOnlineService;
import org.example.rag.online.model.Citation;
import org.example.rag.online.model.RagAnswer;
import org.example.rag.online.model.RagQueryRequest;
import org.example.rag.online.model.RagTrace;
import org.example.rag.online.model.RetrievalCandidate;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class EvaluationReportService {

    private static final String PASS = "PASS";
    private static final String WARN = "WARN";
    private static final String FAIL = "FAIL";
    private static final String SKIPPED = "SKIPPED";
    private static final Pattern SUREFIRE_SUMMARY = Pattern.compile(
            "Tests run: (\\d+), Failures: (\\d+), Errors: (\\d+), Skipped: (\\d+)");

    private final EvaluationProperties properties;
    private final AdvancedRagOfflineIndexService offlineIndexService;
    private final AdvancedRagOnlineService onlineService;
    private final LongTermMemoryService longTermMemoryService;
    private final ContextEngineeringService contextEngineeringService;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    @Value("${spring.ai.dashscope.api-key:}")
    private String dashScopeApiKey;

    public EvaluationReportService(
            EvaluationProperties properties,
            AdvancedRagOfflineIndexService offlineIndexService,
            AdvancedRagOnlineService onlineService,
            LongTermMemoryService longTermMemoryService,
            ContextEngineeringService contextEngineeringService,
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper) {
        this.properties = properties;
        this.offlineIndexService = offlineIndexService;
        this.onlineService = onlineService;
        this.longTermMemoryService = longTermMemoryService;
        this.contextEngineeringService = contextEngineeringService;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper.copy()
                .findAndRegisterModules()
                .enable(SerializationFeature.INDENT_OUTPUT);
    }

    public EvaluationReport currentReport(String namespace) {
        EvaluationReport report = readPersistedReport().orElseGet(this::snapshotReport);
        String resolvedNamespace = defaultText(namespace, currentNamespace(report));
        refreshRuntimeSnapshots(report, resolvedNamespace);
        return report;
    }

    public synchronized EvaluationReport refresh(EvaluationRunRequest request) {
        EvaluationReport report = readPersistedReport().orElseGet(this::snapshotReport);
        String namespace = defaultText(request == null ? null : request.getNamespace(), currentNamespace(report));
        refreshRuntimeSnapshots(report, namespace);
        persist(report);
        return report;
    }

    public synchronized EvaluationReport run(EvaluationRunRequest request) {
        String namespace = defaultText(request == null ? null : request.getNamespace(), properties.getNamespace());
        List<String> models = normalizeModels(request == null ? null : request.getModels());
        boolean seedRagDemo = request == null || request.getSeedRagDemo() == null
                ? properties.isSeedRagDemo()
                : request.getSeedRagDemo();

        EvaluationReport report = baseReport("live");
        report.getQualityGate().setReportPath(qualityGatePath().toString());
        report.setModelEvaluations(evaluateModels(models));
        report.setRagEvaluation(evaluateRag(namespace, seedRagDemo));
        report.setMemoryEvaluation(evaluateMemory());
        finalizeReport(report);
        persist(report);
        return report;
    }

    private EvaluationReport snapshotReport() {
        EvaluationReport report = baseReport("snapshot");
        report.setSummary("当前展示的是只读快照。点击“运行评测”后会真实调用模型、RAG 和记忆链路。");
        report.setOverallStatus(SKIPPED);
        report.setOverallScore(0.0);
        report.getRecommendations().add("点击页面右上角“运行评测”，生成包含模型对比、RAG 召回和记忆隔离的在线报告。");
        report.getRecommendations().add("如果 RAG 文档数量为 0，请先上传业务文档，或使用默认 eval_report 命名空间的演示评测。");
        return report;
    }

    private EvaluationReport baseReport(String mode) {
        EvaluationReport report = new EvaluationReport();
        report.setReportId(UUID.randomUUID().toString());
        report.setGeneratedAt(Instant.now());
        report.setRefreshedAt(report.getGeneratedAt());
        report.setMode(mode);
        report.setEnvironment(environmentSnapshot());
        report.setQualityGate(qualityGateSnapshot());
        return report;
    }

    private void refreshRuntimeSnapshots(EvaluationReport report, String namespace) {
        report.setRefreshedAt(Instant.now());
        report.setEnvironment(environmentSnapshot());
        report.setQualityGate(qualityGateSnapshot());
        refreshRagInventory(report, namespace);
        refreshMemoryInventory(report);
        if ("snapshot".equals(report.getMode())) {
            report.setSummary("当前展示的是刷新后的只读快照。点击“主动运行在线评测”后，会真实调用模型、RAG 和记忆链路。");
            report.setOverallStatus(SKIPPED);
            report.setOverallScore(0.0);
        } else {
            report.setSummary(summaryFor(report));
        }
        report.setRecommendations(recommendationsFor(report));
    }

    private void refreshRagInventory(EvaluationReport report, String namespace) {
        EvaluationReport.RagEvaluation rag = report.getRagEvaluation();
        if (rag == null) {
            rag = new EvaluationReport.RagEvaluation();
            report.setRagEvaluation(rag);
        }
        String previousNamespace = defaultText(rag.getNamespace(), namespace);
        boolean sameNamespace = previousNamespace.equals(namespace);
        rag.setNamespace(namespace);
        rag.setDocumentCount(countQuietly("SELECT COUNT(*) FROM rag_document WHERE namespace = ?", namespace));
        rag.setChunkCount(countQuietly("SELECT COUNT(*) FROM rag_chunk WHERE namespace = ?", namespace));
        if (!sameNamespace) {
            rag.setStatus(SKIPPED);
            rag.setScore(0.0);
            rag.setSeedStatus(SKIPPED);
            rag.setSeedDocumentId(null);
            rag.setAnswerableCase(new EvaluationReport.RagCaseResult());
            rag.setRefusalCase(new EvaluationReport.RagCaseResult());
            rag.getChecks().clear();
            rag.setMessage("已切换命名空间，本次只刷新文档和分片数量；点击“主动运行在线评测”生成该命名空间的 RAG 结果。");
            return;
        }
        if (rag.getStatus() == null || rag.getStatus().isBlank()) {
            rag.setStatus(SKIPPED);
            rag.setScore(0.0);
            rag.setMessage("当前只展示 RAG 文档和分片数量；点击“主动运行在线评测”验证召回、引用和拒答能力。");
        } else if (rag.getMessage() == null || rag.getMessage().isBlank() || rag.getMessage().startsWith("已刷新")) {
            rag.setMessage("已刷新当前命名空间的文档和分片数量；召回与回答结果来自最近一次主动在线评测。");
        }
    }

    private void refreshMemoryInventory(EvaluationReport report) {
        EvaluationReport.MemoryEvaluation memory = report.getMemoryEvaluation();
        if (memory == null) {
            memory = new EvaluationReport.MemoryEvaluation();
            report.setMemoryEvaluation(memory);
        }
        memory.setEnabledMemoryCount(countQuietly("SELECT COUNT(*) FROM user_memory WHERE enabled = 1"));
        if (memory.getStatus() == null || memory.getStatus().isBlank()) {
            memory.setStatus(SKIPPED);
            memory.setScore(0.0);
            memory.setMessage("当前只刷新长期记忆总量；点击“主动运行在线评测”验证写入、检索、隔离和是否进入最终上下文。");
        }
    }

    private EvaluationReport.EnvironmentSnapshot environmentSnapshot() {
        EvaluationReport.EnvironmentSnapshot snapshot = new EvaluationReport.EnvironmentSnapshot();
        snapshot.setDashScopeApiKeyConfigured(hasDashScopeKey());
        try {
            snapshot.setRagDocumentCount(count("SELECT COUNT(*) FROM rag_document"));
            snapshot.setRagChunkCount(count("SELECT COUNT(*) FROM rag_chunk"));
            snapshot.setEnabledMemoryCount(count("SELECT COUNT(*) FROM user_memory WHERE enabled = 1"));
            snapshot.setDatabaseReachable(true);
            snapshot.setMessage("MySQL 元数据表可访问。");
        } catch (Exception e) {
            snapshot.setDatabaseReachable(false);
            snapshot.setMessage("数据库检查失败：" + e.getMessage());
        }
        return snapshot;
    }

    private EvaluationReport.QualityGateSnapshot qualityGateSnapshot() {
        EvaluationReport.QualityGateSnapshot gate = new EvaluationReport.QualityGateSnapshot();
        Path path = qualityGatePath();
        gate.setReportPath(path.toString());
        if (!Files.exists(path)) {
            gate.setStatus(SKIPPED);
            gate.setMessage("未找到本地 Surefire 报告。请先运行 mvn -Dtest=RagEvalDatasetTest test。");
            return gate;
        }
        try {
            String text = Files.readString(path, StandardCharsets.UTF_8);
            Matcher matcher = SUREFIRE_SUMMARY.matcher(text);
            if (!matcher.find()) {
                gate.setStatus(WARN);
                gate.setMessage("已找到 Surefire 报告，但没有解析到测试汇总行。");
                return gate;
            }
            gate.setTestsRun(Integer.parseInt(matcher.group(1)));
            gate.setFailures(Integer.parseInt(matcher.group(2)));
            gate.setErrors(Integer.parseInt(matcher.group(3)));
            gate.setSkipped(Integer.parseInt(matcher.group(4)));
            boolean passed = gate.getFailures() == 0 && gate.getErrors() == 0 && gate.getTestsRun() > 0;
            gate.setStatus(passed ? PASS : FAIL);
            gate.setMessage(passed
                    ? "最近一次离线 RAG 评测门槛已通过。"
                    : "最近一次离线 RAG 评测存在失败或错误。");
        } catch (Exception e) {
            gate.setStatus(WARN);
            gate.setMessage("读取 Surefire 报告失败：" + e.getMessage());
        }
        return gate;
    }

    private List<EvaluationReport.ModelEvaluation> evaluateModels(List<String> models) {
        List<EvaluationReport.ModelEvaluation> evaluations = new ArrayList<>();
        if (!hasDashScopeKey()) {
            EvaluationReport.ModelEvaluation skipped = new EvaluationReport.ModelEvaluation();
            skipped.setStatus(SKIPPED);
            skipped.setModel("全部模型");
            skipped.setErrorMessage("未配置 DASHSCOPE_API_KEY。");
            skipped.setScore(0.0);
            evaluations.add(skipped);
            return evaluations;
        }
        for (String model : models) {
            evaluations.add(evaluateModel(model));
        }
        return evaluations;
    }

    private EvaluationReport.ModelEvaluation evaluateModel(String model) {
        EvaluationReport.ModelEvaluation evaluation = new EvaluationReport.ModelEvaluation();
        evaluation.setModel(model);
        long start = System.currentTimeMillis();
        try {
            DashScopeChatModel chatModel = DashScopeChatModel.builder()
                    .dashScopeApi(DashScopeApi.builder().apiKey(dashScopeApiKey).build())
                    .defaultOptions(DashScopeChatOptions.builder()
                            .withModel(model)
                            .withTemperature(properties.getLlm().getTemperature())
                            .withMaxToken(properties.getLlm().getMaxTokens())
                            .withTopP(properties.getLlm().getTopP())
                            .build())
                    .build();
            String prompt = """
                    你是企业运维助手。请用中文简洁回答。
                    问题：order-service 出现 Redis timeout 时，首先应该检查什么？只列 4 个要点。
                    """;
            ChatResponse response = chatModel.call(new Prompt(prompt));
            String text = response == null || response.getResult() == null || response.getResult().getOutput() == null
                    ? ""
                    : defaultText(response.getResult().getOutput().getText(), "");
            evaluation.setResponsePreview(preview(text, 260));
            evaluation.getChecks().add(requiredTermsCheck(
                    "运维关键点覆盖",
                    text,
                    List.of(
                            List.of("redis"),
                            List.of("order-service", "订单服务"),
                            List.of("连接池", "connection pool", "pool"),
                            List.of("超时", "timeout"),
                            List.of("重试", "retry", "retry budget"))));
            evaluation.getChecks().add(lengthCheck("回答克制性", text, 480));
            evaluation.getChecks().add(noHallucinationPostureCheck(text));
            evaluation.setLatencyMs(System.currentTimeMillis() - start);
            scoreChecks(evaluation);
        } catch (Exception e) {
            evaluation.setLatencyMs(System.currentTimeMillis() - start);
            evaluation.setStatus(FAIL);
            evaluation.setScore(0.0);
            evaluation.setErrorMessage(e.getMessage());
        }
        return evaluation;
    }

    private EvaluationReport.RagEvaluation evaluateRag(String namespace, boolean seedRagDemo) {
        EvaluationReport.RagEvaluation evaluation = new EvaluationReport.RagEvaluation();
        evaluation.setNamespace(namespace);
        evaluation.setDocumentCount(countQuietly("SELECT COUNT(*) FROM rag_document WHERE namespace = ?", namespace));
        evaluation.setChunkCount(countQuietly("SELECT COUNT(*) FROM rag_chunk WHERE namespace = ?", namespace));
        try {
            if (seedRagDemo) {
                IndexResult seed = seedRagDocument(namespace);
                evaluation.setSeedDocumentId(seed.getDocumentId());
                evaluation.setSeedStatus(seed.isSuccess() ? PASS : FAIL);
                if (!seed.isSuccess()) {
                    evaluation.setMessage("演示 RAG 文档入库失败：" + seed.getErrorMessage());
                }
            } else {
                evaluation.setSeedStatus(SKIPPED);
            }

            evaluation.setDocumentCount(countQuietly("SELECT COUNT(*) FROM rag_document WHERE namespace = ?", namespace));
            evaluation.setChunkCount(countQuietly("SELECT COUNT(*) FROM rag_chunk WHERE namespace = ?", namespace));
            evaluation.setAnswerableCase(evaluateAnswerableRagCase(namespace));
            evaluation.setRefusalCase(evaluateRefusalRagCase(namespace));
            evaluation.getChecks().add(booleanCheck(
                    "知识库文档入库",
                    evaluation.getDocumentCount() > 0 && evaluation.getChunkCount() > 0,
                    "namespace=" + namespace + ", documents=" + evaluation.getDocumentCount()
                            + ", chunks=" + evaluation.getChunkCount()));
            evaluation.getChecks().add(booleanCheck(
                    "可回答问题通过",
                    PASS.equals(evaluation.getAnswerableCase().getStatus()),
                    "score=" + evaluation.getAnswerableCase().getScore()));
            evaluation.getChecks().add(booleanCheck(
                    "无依据问题拒答",
                    PASS.equals(evaluation.getRefusalCase().getStatus()),
                    "score=" + evaluation.getRefusalCase().getScore()));
            double score = average(evaluation.getChecks().stream().mapToDouble(EvaluationReport.CheckResult::getScore).toArray());
            evaluation.setScore(score);
            evaluation.setStatus(statusFor(score));
        } catch (Exception e) {
            evaluation.setStatus(FAIL);
            evaluation.setScore(0.0);
            evaluation.setMessage(e.getMessage());
        }
        return evaluation;
    }

    private IndexResult seedRagDocument(String namespace) throws Exception {
        Path input = Path.of("rag-data", "eval-inputs", "superbiz-eval-runbook.md");
        Files.createDirectories(input.getParent());
        Files.writeString(input, demoRagDocument(), StandardCharsets.UTF_8);
        return offlineIndexService.indexFile(input, namespace);
    }

    private EvaluationReport.RagCaseResult evaluateAnswerableRagCase(String namespace) {
        String query = "order-service 出现 Redis timeout 时应该怎么处理？";
        RagAnswer answer = onlineService.answer(ragRequest(namespace, query));
        EvaluationReport.RagCaseResult result = toRagCase("rag-answerable", query, answer);
        String evidenceText = (defaultText(answer.getAnswer(), "") + "\n"
                + answer.getUsedChunks().stream()
                .map(RetrievalCandidate::getContent)
                .reduce("", (left, right) -> left + "\n" + defaultText(right, "")));
        result.getChecks().add(booleanCheck("召回证据", result.getUsedChunkCount() > 0, "usedChunks=" + result.getUsedChunkCount()));
        result.getChecks().add(booleanCheck("引用来源", result.getCitationCount() > 0, "citations=" + result.getCitationCount()));
        result.getChecks().add(booleanCheck("证据支撑", Boolean.TRUE.equals(result.getSupported())
                        && result.getConfidence() != null && result.getConfidence() >= 0.55,
                "supported=" + result.getSupported() + ", confidence=" + result.getConfidence()));
        result.getChecks().add(requiredTermsCheck(
                "关键点覆盖",
                evidenceText,
                List.of(
                        List.of("redis"),
                        List.of("order-service"),
                        List.of("connection pool", "连接池"),
                        List.of("timeout", "超时"),
                        List.of("retry budget", "重试"))));
        scoreRagCase(result);
        return result;
    }

    private EvaluationReport.RagCaseResult evaluateRefusalRagCase(String namespace) {
        String query = "月面遥测代理的恢复手册是什么？";
        RagAnswer answer = onlineService.answer(ragRequest(namespace, query));
        EvaluationReport.RagCaseResult result = toRagCase("rag-refusal", query, answer);
        result.getChecks().add(booleanCheck("未被证据支撑", !Boolean.TRUE.equals(result.getSupported()),
                "supported=" + result.getSupported()));
        result.getChecks().add(booleanCheck("低置信度", result.getConfidence() == null || result.getConfidence() < 0.55,
                "confidence=" + result.getConfidence()));
        result.getChecks().add(booleanCheck("避免强行引用", result.getCitationCount() == 0 || !Boolean.TRUE.equals(result.getSupported()),
                "citations=" + result.getCitationCount()));
        scoreRagCase(result);
        return result;
    }

    private EvaluationReport.RagCaseResult toRagCase(String caseId, String query, RagAnswer answer) {
        EvaluationReport.RagCaseResult result = new EvaluationReport.RagCaseResult();
        result.setCaseId(caseId);
        result.setQuery(query);
        result.setAnswerPreview(preview(answer.getAnswer(), 300));
        result.setSupported(answer.getSupported());
        result.setConfidence(answer.getConfidence());
        result.setCitationCount(answer.getCitations() == null ? 0 : answer.getCitations().size());
        result.setUsedChunkCount(answer.getUsedChunks() == null ? 0 : answer.getUsedChunks().size());
        if (answer.getUsedChunks() != null) {
            result.setUsedChunkIds(answer.getUsedChunks().stream()
                    .map(RetrievalCandidate::getChunkId)
                    .toList());
        }
        if (answer.getCitations() != null) {
            result.setCitationFiles(answer.getCitations().stream()
                    .map(Citation::getFileName)
                    .distinct()
                    .toList());
        }
        RagTrace trace = answer.getTrace();
        if (trace != null) {
            result.setDenseHitCount(nullToZero(trace.getDenseHitCount()));
            result.setSparseHitCount(nullToZero(trace.getSparseHitCount()));
            result.setRerankCount(nullToZero(trace.getRerankCount()));
            result.setTotalTimeMs(trace.getTotalTimeMs() == null ? 0L : trace.getTotalTimeMs());
        }
        return result;
    }

    private EvaluationReport.MemoryEvaluation evaluateMemory() {
        EvaluationReport.MemoryEvaluation evaluation = new EvaluationReport.MemoryEvaluation();
        String userId = "eval-report-user";
        String otherUserId = "eval-report-other-user";
        String marker = "eval-" + UUID.randomUUID();
        String content = "用户的名字是 EvalMemoryUser-" + marker + "，以后应使用这个名字来称呼用户。";
        evaluation.setUserId(userId);
        evaluation.setOtherUserId(otherUserId);
        evaluation.setMarker(marker);
        try {
            List<UserMemory> saved = longTermMemoryService.addManualMemory(
                    userId, content, "preference", "user", 0.90);
            evaluation.setMemoryId(saved.isEmpty() ? "" : saved.get(0).getMemoryId());
            List<UserMemory> sameUserResults = longTermMemoryService.searchMemories(userId, marker, null, 5);
            List<UserMemory> otherUserResults = longTermMemoryService.searchMemories(otherUserId, marker, null, 5);
            ContextBuildResult contextResult = contextEngineeringService.buildForChat(
                    userId,
                    "eval-memory-context",
                    "我是谁？");
            boolean savedOk = !saved.isEmpty();
            boolean searchOk = sameUserResults.stream().anyMatch(memory -> contains(memory.getContent(), marker));
            boolean isolated = otherUserResults.stream().noneMatch(memory -> contains(memory.getContent(), marker));
            boolean contextOk = contextResult.getSelectedPackets().stream()
                    .anyMatch(packet -> packet.getType() == ContextSourceType.MEMORY
                            && (contains(packet.getContent(), marker)
                            || contains(String.valueOf(packet.getMetadata().get("memoryId")), evaluation.getMemoryId())))
                    || contains(contextResult.getFinalContext(), marker);
            evaluation.getChecks().add(booleanCheck("长期记忆写入", savedOk, "memoryId=" + evaluation.getMemoryId()));
            evaluation.getChecks().add(booleanCheck("同用户可检索", searchOk, "queryMarker=" + marker));
            evaluation.getChecks().add(booleanCheck("跨用户隔离", isolated, "otherUserResults=" + otherUserResults.size()));
            evaluation.getChecks().add(booleanCheck("进入最终上下文", contextOk,
                    "selectedPackets=" + contextResult.getSelectedPackets().size()
                            + ", droppedPackets=" + contextResult.getDroppedPackets().size()));
            evaluation.setEnabledMemoryCount(countQuietly("SELECT COUNT(*) FROM user_memory WHERE enabled = 1"));
            double score = average(evaluation.getChecks().stream().mapToDouble(EvaluationReport.CheckResult::getScore).toArray());
            evaluation.setScore(score);
            evaluation.setStatus(statusFor(score));
            evaluation.setMessage("记忆评测使用独立的 eval-report 用户和唯一追踪标记。");
        } catch (Exception e) {
            evaluation.setStatus(FAIL);
            evaluation.setScore(0.0);
            evaluation.setMessage(e.getMessage());
        }
        return evaluation;
    }

    private RagQueryRequest ragRequest(String namespace, String query) {
        RagQueryRequest request = new RagQueryRequest();
        request.setNamespace(namespace);
        request.setQuery(query);
        request.setTopK(4);
        request.setDebug(true);
        request.setEnableRerank(false);
        request.setEnableVerify(true);
        return request;
    }

    private void scoreChecks(EvaluationReport.ModelEvaluation evaluation) {
        double score = average(evaluation.getChecks().stream().mapToDouble(EvaluationReport.CheckResult::getScore).toArray());
        evaluation.setScore(score);
        evaluation.setStatus(statusFor(score));
    }

    private void scoreRagCase(EvaluationReport.RagCaseResult result) {
        double score = average(result.getChecks().stream().mapToDouble(EvaluationReport.CheckResult::getScore).toArray());
        result.setScore(score);
        result.setStatus(statusFor(score));
    }

    private EvaluationReport.CheckResult requiredTermsCheck(
            String name,
            String text,
            List<List<String>> termGroups) {
        long matched = termGroups.stream()
                .filter(group -> group.stream().anyMatch(term -> contains(text, term)))
                .count();
        double score = termGroups.isEmpty() ? 1.0 : (double) matched / (double) termGroups.size();
        EvaluationReport.CheckResult check = new EvaluationReport.CheckResult();
        check.setName(name);
        check.setScore(score);
        check.setStatus(statusFor(score));
        check.setMessage("命中 " + matched + "/" + termGroups.size() + " 个预期概念组。");
        check.setEvidence(preview(text, 180));
        return check;
    }

    private EvaluationReport.CheckResult lengthCheck(String name, String text, int maxLength) {
        boolean passed = text != null && !text.isBlank() && text.length() <= maxLength;
        return booleanCheck(name, passed, "length=" + (text == null ? 0 : text.length()) + ", max=" + maxLength);
    }

    private EvaluationReport.CheckResult noHallucinationPostureCheck(String text) {
        boolean passed = text != null
                && !contains(text, "编造")
                && !contains(text, "随便")
                && !contains(text, "一定是");
        return booleanCheck("幻觉风险措辞", passed, "未发现明显过度自信或不安全措辞。");
    }

    private EvaluationReport.CheckResult booleanCheck(String name, boolean passed, String evidence) {
        EvaluationReport.CheckResult check = new EvaluationReport.CheckResult();
        check.setName(name);
        check.setStatus(passed ? PASS : FAIL);
        check.setScore(passed ? 1.0 : 0.0);
        check.setMessage(passed ? "通过" : "失败");
        check.setEvidence(evidence);
        return check;
    }

    private void finalizeReport(EvaluationReport report) {
        List<Double> scores = new ArrayList<>();
        report.getModelEvaluations().stream()
                .filter(model -> !SKIPPED.equals(model.getStatus()))
                .map(EvaluationReport.ModelEvaluation::getScore)
                .forEach(scores::add);
        if (!SKIPPED.equals(report.getRagEvaluation().getStatus())) {
            scores.add(report.getRagEvaluation().getScore());
        }
        if (!SKIPPED.equals(report.getMemoryEvaluation().getStatus())) {
            scores.add(report.getMemoryEvaluation().getScore());
        }
        double overall = scores.isEmpty() ? 0.0 : average(scores.stream().mapToDouble(Double::doubleValue).toArray());
        report.setOverallScore(overall);
        report.setOverallStatus(scores.isEmpty() ? SKIPPED : statusFor(overall));
        report.setSummary(summaryFor(report));
        report.setRecommendations(recommendationsFor(report));
    }

    private String summaryFor(EvaluationReport report) {
        return "模型评测 " + passCount(report.getModelEvaluations()) + "/" + report.getModelEvaluations().size()
                + " 通过，RAG 状态 " + statusLabel(report.getRagEvaluation().getStatus())
                + "，记忆状态 " + statusLabel(report.getMemoryEvaluation().getStatus())
                + "，总体分 " + Math.round(report.getOverallScore() * 100) + "%。";
    }

    private List<String> recommendationsFor(EvaluationReport report) {
        List<String> values = new ArrayList<>();
        if (!report.getEnvironment().isDashScopeApiKeyConfigured()) {
            values.add("配置 DASHSCOPE_API_KEY 后再运行在线模型和 RAG 生成评测。");
        }
        if (!PASS.equals(report.getRagEvaluation().getStatus())) {
            values.add("优先检查 RAG 文档是否成功入库、召回分片是否为空、引用来源是否缺失。");
        }
        if (!PASS.equals(report.getMemoryEvaluation().getStatus())) {
            values.add("优先检查 user_memory 表、MemoryVectorService 以及 userId 隔离逻辑。");
        }
        boolean hasModelFailure = report.getModelEvaluations().stream()
                .anyMatch(model -> FAIL.equals(model.getStatus()) || WARN.equals(model.getStatus()));
        if (hasModelFailure) {
            values.add("模型分数低时，查看关键点覆盖和回答克制性；如果是模型不可用，请调整 evaluation.llm.models。");
        }
        if (values.isEmpty()) {
            values.add("当前在线评测通过，可以继续扩大真实业务问题集和真实文档覆盖面。");
        }
        return values;
    }

    private int passCount(List<EvaluationReport.ModelEvaluation> evaluations) {
        return (int) evaluations.stream().filter(model -> PASS.equals(model.getStatus())).count();
    }

    private Optional<EvaluationReport> readPersistedReport() {
        Path path = reportPath();
        if (!Files.exists(path)) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(path.toFile(), EvaluationReport.class));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private void persist(EvaluationReport report) {
        try {
            Path path = reportPath();
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            objectMapper.writeValue(path.toFile(), report);
        } catch (Exception ignored) {
            // 即使本地报告写入失败，API 返回值仍然可用于页面展示。
        }
    }

    private String currentNamespace(EvaluationReport report) {
        if (report != null && report.getRagEvaluation() != null) {
            return defaultText(report.getRagEvaluation().getNamespace(), properties.getNamespace());
        }
        return properties.getNamespace();
    }

    private List<String> normalizeModels(List<String> requestedModels) {
        List<String> values = requestedModels == null || requestedModels.isEmpty()
                ? properties.getLlm().getModels()
                : requestedModels;
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
    }

    private String statusFor(double score) {
        if (score >= properties.getPassThreshold()) {
            return PASS;
        }
        if (score >= properties.getWarnThreshold()) {
            return WARN;
        }
        return FAIL;
    }

    private String statusLabel(String status) {
        return switch (defaultText(status, SKIPPED)) {
            case PASS -> "通过";
            case WARN -> "警告";
            case FAIL -> "失败";
            default -> "未运行";
        };
    }

    private double average(double[] values) {
        if (values == null || values.length == 0) {
            return 0.0;
        }
        double total = 0.0;
        for (double value : values) {
            total += value;
        }
        return total / values.length;
    }

    private long count(String sql) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class);
        return value == null ? 0L : value;
    }

    private long countQuietly(String sql) {
        try {
            return count(sql);
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private long countQuietly(String sql, Object argument) {
        try {
            Long value = jdbcTemplate.queryForObject(sql, Long.class, argument);
            return value == null ? 0L : value;
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private Path reportPath() {
        return Path.of(defaultText(properties.getReportPath(), "target/eval-report.json")).normalize();
    }

    private Path qualityGatePath() {
        return Path.of("target", "surefire-reports", "org.example.rag.eval.RagEvalDatasetTest.txt").normalize();
    }

    private boolean hasDashScopeKey() {
        return dashScopeApiKey != null && !dashScopeApiKey.isBlank();
    }

    private boolean contains(String text, String term) {
        if (text == null || term == null) {
            return false;
        }
        return text.toLowerCase(Locale.ROOT).contains(term.toLowerCase(Locale.ROOT));
    }

    private int nullToZero(Integer value) {
        return value == null ? 0 : value;
    }

    private String preview(String text, int maxLength) {
        String value = defaultText(text, "").replace("\r", " ").replace("\n", " ").trim();
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, Math.max(0, maxLength - 3)) + "...";
    }

    private String defaultText(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }

    private String demoRagDocument() {
        return """
                # SuperBizAgent 评测用运行手册

                ## Redis 超时

                当 order-service 出现 Redis timeout 时，首先应该检查 Redis 连接池是否耗尽、超时配置、
                Redis 节点健康状态、网络延迟以及重试预算。重启服务前需要保留错误日志。

                ## CPU 过高

                当 payment-service 出现 CPU 使用率过高时，应检查热点线程、近期发布、垃圾回收、
                容器 CPU 限制以及入口请求量。高风险重启前应优先评估扩容。

                ## 内存 OOM

                当 JVM 服务出现 OOM 或内存使用率过高时，应检查堆使用量、Full GC 频率、堆转储证据、
                内存泄漏模式以及近期流量变化。
                """;
    }
}

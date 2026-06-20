package org.example.rag.online.query;

import org.example.rag.online.model.QueryAnalysis;
import org.example.rag.online.model.QueryType;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class QueryAnalysisService {

    private static final Pattern SERVICE_NAME = Pattern.compile("\\b[a-z][a-z0-9-]*-service\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern ERROR_CODE = Pattern.compile("\\b(ERR_[A-Z0-9_]+|[A-Z]{2,10}_[A-Z0-9_]+|\\d{5,})\\b");
    private static final Pattern TRACE_ID = Pattern.compile("\\b(traceId|trace_id|trace-id)[:= ]+[A-Za-z0-9_-]{8,}\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern JAVA_IDENTIFIER = Pattern.compile("\\b[A-Za-z_$][A-Za-z0-9_$]*\\.(java|class|properties|yaml|yml)\\b");

    private static final List<String> COMPONENTS = List.of(
            "mysql", "redis", "kafka", "milvus", "minio", "nginx", "neo4j", "prometheus",
            "elasticsearch", "rocketmq", "rabbitmq", "gateway", "spring", "spring boot");

    private static final List<String> ALERT_TYPES = List.of(
            "CPU_HIGH", "MEMORY_HIGH", "DISK_FULL", "DB_TIMEOUT", "REDIS_TIMEOUT",
            "HTTP_5XX", "POD_RESTART", "LATENCY_HIGH");

    public QueryAnalysis analyze(String query) {
        QueryAnalysis analysis = new QueryAnalysis();
        String safeQuery = query == null ? "" : query.trim();
        if (safeQuery.isBlank()) {
            analysis.setNeedRag(false);
            analysis.setNeedRewrite(false);
            analysis.setPreferDense(false);
            return analysis;
        }

        List<String> keywords = extractKeywords(safeQuery);
        List<String> services = findAll(SERVICE_NAME, safeQuery);
        List<String> errorCodes = findAll(ERROR_CODE, safeQuery);
        List<String> components = extractComponents(safeQuery);
        List<String> alertTypes = extractAlertTypes(safeQuery);

        analysis.setKeywords(keywords);
        analysis.setServiceNames(services);
        analysis.setErrorCodes(errorCodes);
        analysis.setComponents(components);
        analysis.setAlertTypes(alertTypes);
        analysis.setQueryType(classify(safeQuery, errorCodes, components));
        applyStrategyFlags(analysis);
        putFirst(analysis, "serviceName", services);
        putFirst(analysis, "errorCode", errorCodes);
        putFirst(analysis, "component", components);
        putFirst(analysis, "alertType", alertTypes);
        return analysis;
    }

    private QueryType classify(String query, List<String> errorCodes, List<String> components) {
        String lower = query.toLowerCase(Locale.ROOT);
        if (!errorCodes.isEmpty()
                || TRACE_ID.matcher(query).find()
                || JAVA_IDENTIFIER.matcher(query).find()
                || containsAny(lower, "traceid", "配置项", "错误码", "接口", "api", "class", "method")) {
            return QueryType.EXACT_TERM;
        }
        if (containsAny(lower, "排查", "原因", "告警", "异常", "超时", "失败", "慢", "timeout", "error", "exception", "latency")) {
            return QueryType.TROUBLESHOOTING;
        }
        if (containsAny(lower, "步骤", "流程", "如何", "怎么配置", "怎么部署", "安装", "procedure", "deploy")) {
            return QueryType.PROCEDURE;
        }
        if (containsAny(lower, "对比", "区别", "差异", "compare", "difference")) {
            return QueryType.COMPARISON;
        }
        if (containsAny(lower, "总结", "概括", "归纳", "summary")) {
            return QueryType.SUMMARY;
        }
        if (containsAny(lower, "关系", "依赖", "影响", "链路", "调用关系", "multi-hop") || components.size() >= 2) {
            return QueryType.MULTI_HOP;
        }
        return QueryType.GENERAL_QA;
    }

    private void applyStrategyFlags(QueryAnalysis analysis) {
        QueryType type = analysis.getQueryType();
        analysis.setNeedRag(type != QueryType.OUT_OF_SCOPE);
        analysis.setNeedRewrite(type == QueryType.GENERAL_QA || type == QueryType.TROUBLESHOOTING
                || type == QueryType.PROCEDURE || type == QueryType.MULTI_HOP);
        analysis.setNeedHyde(type == QueryType.TROUBLESHOOTING || type == QueryType.MULTI_HOP);
        analysis.setPreferBm25(type == QueryType.EXACT_TERM);
        analysis.setPreferDense(type != QueryType.EXACT_TERM || analysis.getKeywords().size() > 2);
    }

    private List<String> extractKeywords(String query) {
        Set<String> keywords = new LinkedHashSet<>();
        for (String token : query.split("[\\s,，。；;：:()（）\\[\\]{}]+")) {
            String normalized = token.trim();
            if (normalized.length() >= 2 && !isStopWord(normalized)) {
                keywords.add(normalized);
            }
        }
        keywords.addAll(findAll(SERVICE_NAME, query));
        keywords.addAll(findAll(ERROR_CODE, query));
        keywords.addAll(extractComponents(query));
        return new ArrayList<>(keywords);
    }

    private List<String> extractComponents(String query) {
        String lower = query.toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();
        for (String component : COMPONENTS) {
            if (lower.contains(component)) {
                matches.add(canonicalComponent(component));
            }
        }
        return matches.stream().distinct().toList();
    }

    private String canonicalComponent(String component) {
        if ("spring boot".equals(component)) {
            return "Spring Boot";
        }
        return component.substring(0, 1).toUpperCase(Locale.ROOT) + component.substring(1);
    }

    private List<String> extractAlertTypes(String query) {
        String upper = query.toUpperCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();
        for (String alertType : ALERT_TYPES) {
            if (upper.contains(alertType)) {
                matches.add(alertType);
            }
        }
        if (query.contains("CPU") && containsAny(query, "高", "high", "告警")) {
            matches.add("CPU_HIGH");
        }
        if (query.contains("内存") && containsAny(query.toLowerCase(Locale.ROOT), "高", "memory", "告警")) {
            matches.add("MEMORY_HIGH");
        }
        return matches.stream().distinct().toList();
    }

    private List<String> findAll(Pattern pattern, String text) {
        List<String> result = new ArrayList<>();
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            result.add(matcher.group());
        }
        return result.stream().distinct().toList();
    }

    private boolean containsAny(String text, String... words) {
        for (String word : words) {
            if (text.contains(word)) {
                return true;
            }
        }
        return false;
    }

    private void putFirst(QueryAnalysis analysis, String key, List<String> values) {
        if (values != null && !values.isEmpty()) {
            analysis.getMetadataFilter().put(key, values.get(0));
        }
    }

    private boolean isStopWord(String token) {
        return Set.of("怎么", "如何", "什么", "一下", "一个", "这个", "那个", "the", "and", "for", "with")
                .contains(token.toLowerCase(Locale.ROOT));
    }
}

package org.example.rag.online.query;

import org.example.rag.online.model.QueryAnalysis;
import org.example.rag.online.model.QueryType;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class QueryRewriteService {

    public List<String> rewrite(String query, QueryAnalysis analysis, int n) {
        String safeQuery = query == null ? "" : query.trim();
        if (safeQuery.isBlank()) {
            return List.of();
        }
        Set<String> queries = new LinkedHashSet<>();
        queries.add(safeQuery);

        QueryType type = analysis == null ? QueryType.GENERAL_QA : analysis.getQueryType();
        if (type == QueryType.TROUBLESHOOTING) {
            addTroubleshootingQueries(queries, safeQuery, analysis);
        } else if (type == QueryType.PROCEDURE) {
            queries.add(safeQuery + " 步骤");
            queries.add(safeQuery + " 配置流程");
            queries.add(safeQuery + " 操作指南");
        } else if (type == QueryType.EXACT_TERM) {
            queries.add(safeQuery + " 错误码 文档");
            queries.add(safeQuery + " 配置项 日志");
        } else {
            queries.add(safeQuery + " 说明");
            queries.add(safeQuery + " 最佳实践");
            queries.add(safeQuery + " 系统设计");
        }

        int limit = Math.max(1, n);
        List<String> result = new ArrayList<>();
        for (String item : queries) {
            if (item != null && !item.isBlank() && item.length() <= 120) {
                result.add(item);
            }
            if (result.size() >= limit) {
                break;
            }
        }
        return result.isEmpty() ? List.of(safeQuery) : result;
    }

    private void addTroubleshootingQueries(Set<String> queries, String query, QueryAnalysis analysis) {
        String target = query;
        if (analysis != null && !analysis.getComponents().isEmpty()) {
            target = analysis.getComponents().get(0);
        } else if (analysis != null && !analysis.getServiceNames().isEmpty()) {
            target = analysis.getServiceNames().get(0);
        }
        queries.add(target + " 超时原因");
        queries.add(target + " 异常排查步骤");
        queries.add(target + " 故障处理 日志 告警");
    }
}

package org.example.rag.online.query;

import org.example.rag.online.model.QueryAnalysis;
import org.example.rag.online.model.QueryType;
import org.springframework.stereotype.Service;

@Service
public class HyDEGeneratorService {

    public String generateHypotheticalDocument(String query, QueryAnalysis analysis) {
        if (query == null || query.isBlank()) {
            return "";
        }
        QueryType type = analysis == null ? QueryType.GENERAL_QA : analysis.getQueryType();
        if (type == QueryType.TROUBLESHOOTING) {
            String target = analysis != null && !analysis.getComponents().isEmpty()
                    ? analysis.getComponents().get(0)
                    : "系统服务";
            return target + " 故障通常需要结合告警、日志、链路追踪、资源监控、依赖组件和配置变更排查。"
                    + " 常见原因包括连接超时、资源不足、线程池耗尽、数据库慢查询、缓存异常、下游服务阻塞或网络抖动。"
                    + " 排查步骤应先确认影响范围，再查看错误码、traceId、服务日志和监控指标。";
        }
        if (type == QueryType.MULTI_HOP) {
            return "这个问题可能涉及多个系统模块、依赖组件和调用关系，需要从项目结构、模块职责、技术组件和数据流之间的关系进行检索。";
        }
        return query;
    }
}

package org.example.rag.online.query;

import org.example.rag.online.model.QueryAnalysis;
import org.example.rag.online.model.QueryType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class QueryAnalysisServiceTest {

    private final QueryAnalysisService service = new QueryAnalysisService();

    @Test
    void classifiesTroubleshootingAndExtractsOpsMetadata() {
        QueryAnalysis analysis = service.analyze("order-service Redis 超时怎么排查？traceId=abc123456789");

        assertThat(analysis.getQueryType()).isEqualTo(QueryType.EXACT_TERM);
        assertThat(analysis.getServiceNames()).contains("order-service");
        assertThat(analysis.getComponents()).contains("Redis");
        assertThat(analysis.getMetadataFilter()).containsEntry("serviceName", "order-service");
        assertThat(analysis.getPreferBm25()).isTrue();
    }

    @Test
    void classifiesProcedureQuestions() {
        QueryAnalysis analysis = service.analyze("如何配置 Milvus 文档索引流程？");

        assertThat(analysis.getQueryType()).isEqualTo(QueryType.PROCEDURE);
        assertThat(analysis.getNeedRewrite()).isTrue();
        assertThat(analysis.getComponents()).contains("Milvus");
    }
}

package org.example.rag.online.query;

import org.example.rag.online.model.QueryAnalysis;
import org.example.rag.online.model.QueryType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HyDEGeneratorServiceTest {

    private final HyDEGeneratorService service = new HyDEGeneratorService();

    @Test
    void generatesHypotheticalTroubleshootingDocumentOnlyForRetrieval() {
        QueryAnalysis analysis = new QueryAnalysis();
        analysis.setQueryType(QueryType.TROUBLESHOOTING);
        analysis.getComponents().add("Redis");

        String hyde = service.generateHypotheticalDocument("Redis 超时怎么排查？", analysis);

        assertThat(hyde).contains("Redis", "日志", "监控");
    }
}

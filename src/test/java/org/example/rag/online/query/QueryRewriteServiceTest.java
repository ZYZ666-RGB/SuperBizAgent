package org.example.rag.online.query;

import org.example.rag.online.model.QueryAnalysis;
import org.example.rag.online.model.QueryType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class QueryRewriteServiceTest {

    private final QueryRewriteService service = new QueryRewriteService();

    @Test
    void keepsOriginalQueryAndAddsTroubleshootingVariants() {
        QueryAnalysis analysis = new QueryAnalysis();
        analysis.setQueryType(QueryType.TROUBLESHOOTING);
        analysis.getComponents().add("Redis");

        List<String> queries = service.rewrite("Redis 超时怎么排查？", analysis, 3);

        assertThat(queries).first().isEqualTo("Redis 超时怎么排查？");
        assertThat(queries).anyMatch(query -> query.contains("超时原因"));
        assertThat(queries).hasSize(3);
    }
}

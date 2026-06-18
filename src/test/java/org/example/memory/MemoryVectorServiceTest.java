package org.example.memory;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MemoryVectorServiceTest {

    @Test
    void searchExpressionAlwaysScopesByUserEnabledAndMemoryType() {
        MemoryVectorService service = new MemoryVectorService(null, null, new MemoryProperties());

        String expression = service.buildSearchExpression(
                "user-a",
                List.of("semantic", "project_context"));

        assertThat(expression).contains("user_id == \"user-a\"");
        assertThat(expression).contains("enabled == true");
        assertThat(expression).contains("memory_type in [\"semantic\", \"project_context\"]");
    }

    @Test
    void searchExpressionEscapesUserInput() {
        MemoryVectorService service = new MemoryVectorService(null, null, new MemoryProperties());

        String expression = service.buildSearchExpression("user\"a", List.of("semantic"));

        assertThat(expression).contains("user_id == \"user\\\"a\"");
    }
}

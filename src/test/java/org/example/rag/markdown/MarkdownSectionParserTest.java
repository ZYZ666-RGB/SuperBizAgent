package org.example.rag.markdown;

import org.example.rag.chunk.TokenEstimator;
import org.example.rag.model.MarkdownBlock;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MarkdownSectionParserTest {

    @Test
    void parsesBlocksWithHeadingPath() {
        MarkdownSectionParser parser = new MarkdownSectionParser(new TokenEstimator());
        String markdown = """
                # System
                ## Login
                Paragraph text.

                | A | B |
                |---|---|
                | 1 | 2 |

                ```java
                class A {}
                ```
                """;

        List<MarkdownBlock> blocks = parser.parse(markdown);

        assertThat(blocks).extracting(MarkdownBlock::getType)
                .contains("heading", "paragraph", "table", "code");
        assertThat(blocks.stream()
                .filter(block -> "paragraph".equals(block.getType()))
                .findFirst()
                .orElseThrow()
                .getHeadingPath()).isEqualTo("System > Login");
        assertThat(blocks.stream()
                .filter(block -> "table".equals(block.getType()))
                .findFirst()
                .orElseThrow()
                .getContent()).contains("| A | B |");
    }
}

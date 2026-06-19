package org.example.rag.markdown;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MarkdownNormalizeServiceTest {

    @Test
    void normalizesNoiseWithoutBreakingCodeOrTables() {
        MarkdownNormalizeService service = new MarkdownNormalizeService(new MarkdownCleaner());
        String markdown = """
                Watermark
                Watermark
                Watermark
                Watermark
                # Title


                intro line
                continues

                | A | B |
                |---|---|
                | 1 | 2 |

                ```
                line

                line2
                ```
                Chapter .......... 12
                """;

        String normalized = service.normalize(markdown);

        assertThat(normalized).contains("# Title");
        assertThat(normalized).contains("intro line continues");
        assertThat(normalized).contains("| A | B |");
        assertThat(normalized).contains("```\nline\n\nline2\n```");
        assertThat(normalized).doesNotContain("Watermark");
        assertThat(normalized).doesNotContain("..........");
    }
}

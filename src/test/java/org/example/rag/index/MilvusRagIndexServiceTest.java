package org.example.rag.index;

import org.example.constant.MilvusConstants;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class MilvusRagIndexServiceTest {

    @Test
    void truncateToUtf8BytesKeepsContentWithinMilvusLimit() {
        String content = "中文表格内容".repeat(900);

        String truncated = MilvusRagIndexService.truncateToUtf8Bytes(
                content,
                MilvusConstants.CONTENT_MAX_LENGTH);

        assertThat(truncated.getBytes(StandardCharsets.UTF_8).length)
                .isLessThanOrEqualTo(MilvusConstants.CONTENT_MAX_LENGTH);
        assertThat(content).startsWith(truncated);
        assertThat(truncated).doesNotContain("\uFFFD");
    }

    @Test
    void truncateToUtf8BytesLeavesShortContentUnchanged() {
        String content = "order-service Redis timeout runbook";

        String truncated = MilvusRagIndexService.truncateToUtf8Bytes(
                content,
                MilvusConstants.CONTENT_MAX_LENGTH);

        assertThat(truncated).isSameAs(content);
    }
}

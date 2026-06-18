package org.example.memory;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

@Repository
public class ConversationSummaryRepository {

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<ConversationSummary> rowMapper = (rs, rowNum) -> {
        ConversationSummary summary = new ConversationSummary();
        summary.setId(rs.getLong("id"));
        summary.setUserId(rs.getString("user_id"));
        summary.setSessionId(rs.getString("session_id"));
        summary.setSummary(rs.getString("summary"));
        summary.setSummarizedUntilMessageId(rs.getLong("summarized_until_message_id"));
        summary.setSummarizedMessageCount(rs.getInt("summarized_message_count"));
        Timestamp createdAt = rs.getTimestamp("created_at");
        if (createdAt != null) {
            summary.setCreatedAt(createdAt.toLocalDateTime());
        }
        Timestamp updatedAt = rs.getTimestamp("updated_at");
        if (updatedAt != null) {
            summary.setUpdatedAt(updatedAt.toLocalDateTime());
        }
        return summary;
    };

    public ConversationSummaryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<ConversationSummary> findBySession(String userId, String sessionId) {
        String sql = """
                SELECT id, user_id, session_id, summary, summarized_until_message_id,
                       summarized_message_count, created_at, updated_at
                FROM conversation_summary
                WHERE user_id = ? AND session_id = ?
                """;
        List<ConversationSummary> summaries = jdbcTemplate.query(sql, rowMapper, userId, sessionId);
        if (summaries.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(summaries.get(0));
    }

    public void upsert(
            String userId,
            String sessionId,
            String summary,
            long summarizedUntilMessageId,
            int summarizedMessageCount) {
        String sql = """
                INSERT INTO conversation_summary(
                    user_id, session_id, summary, summarized_until_message_id, summarized_message_count
                )
                VALUES (?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    summary = VALUES(summary),
                    summarized_until_message_id = VALUES(summarized_until_message_id),
                    summarized_message_count = VALUES(summarized_message_count),
                    updated_at = CURRENT_TIMESTAMP
                """;
        jdbcTemplate.update(sql, userId, sessionId, summary, summarizedUntilMessageId, summarizedMessageCount);
    }

    public void deleteBySession(String userId, String sessionId) {
        String sql = "DELETE FROM conversation_summary WHERE user_id = ? AND session_id = ?";
        jdbcTemplate.update(sql, userId, sessionId);
    }
}

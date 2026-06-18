package org.example.memory;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class ConversationMessageRepository {

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<ConversationMessage> rowMapper = (rs, rowNum) -> {
        ConversationMessage message = new ConversationMessage();
        message.setId(rs.getLong("id"));
        message.setUserId(rs.getString("user_id"));
        message.setSessionId(rs.getString("session_id"));
        message.setRole(rs.getString("role"));
        message.setContent(rs.getString("content"));
        message.setTokenCount(rs.getInt("token_count"));
        Timestamp createdAt = rs.getTimestamp("created_at");
        if (createdAt != null) {
            message.setCreatedAt(createdAt.toLocalDateTime());
        }
        return message;
    };

    public ConversationMessageRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public long insert(String userId, String sessionId, String role, String content, int tokenCount) {
        String sql = """
                INSERT INTO conversation_message(user_id, session_id, role, content, token_count)
                VALUES (?, ?, ?, ?, ?)
                """;
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, new String[]{"id"});
            ps.setString(1, userId);
            ps.setString(2, sessionId);
            ps.setString(3, role);
            ps.setString(4, content);
            ps.setInt(5, tokenCount);
            return ps;
        }, keyHolder);

        Number key = keyHolder.getKey();
        return key == null ? 0L : key.longValue();
    }

    public List<ConversationMessage> findRecent(String userId, String sessionId, int messageLimit) {
        String sql = """
                SELECT id, user_id, session_id, role, content, token_count, created_at
                FROM (
                    SELECT id, user_id, session_id, role, content, token_count, created_at
                    FROM conversation_message
                    WHERE user_id = ? AND session_id = ?
                    ORDER BY id DESC
                    LIMIT ?
                ) recent_messages
                ORDER BY id ASC
                """;
        return jdbcTemplate.query(sql, rowMapper, userId, sessionId, Math.max(1, messageLimit));
    }

    public long countBySession(String userId, String sessionId) {
        String sql = "SELECT COUNT(*) FROM conversation_message WHERE user_id = ? AND session_id = ?";
        Long count = jdbcTemplate.queryForObject(sql, Long.class, userId, sessionId);
        return count == null ? 0L : count;
    }

    public Optional<LocalDateTime> findSessionCreatedAt(String userId, String sessionId) {
        String sql = "SELECT MIN(created_at) FROM conversation_message WHERE user_id = ? AND session_id = ?";
        Timestamp timestamp = jdbcTemplate.queryForObject(sql, Timestamp.class, userId, sessionId);
        if (timestamp == null) {
            return Optional.empty();
        }
        return Optional.of(timestamp.toLocalDateTime());
    }

    public Optional<Long> findSummaryCutoffMessageId(String userId, String sessionId, int recentMessageLimit) {
        String sql = """
                SELECT id
                FROM conversation_message
                WHERE user_id = ? AND session_id = ?
                ORDER BY id DESC
                LIMIT 1 OFFSET ?
                """;
        List<Long> ids = jdbcTemplate.queryForList(sql, Long.class, userId, sessionId, Math.max(1, recentMessageLimit));
        if (ids.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(ids.get(0));
    }

    public List<ConversationMessage> findMessagesForSummary(
            String userId, String sessionId, long afterMessageId, long untilMessageId) {
        String sql = """
                SELECT id, user_id, session_id, role, content, token_count, created_at
                FROM conversation_message
                WHERE user_id = ? AND session_id = ? AND id > ? AND id <= ?
                ORDER BY id ASC
                """;
        return jdbcTemplate.query(sql, rowMapper, userId, sessionId, afterMessageId, untilMessageId);
    }

    public void deleteBySession(String userId, String sessionId) {
        String sql = "DELETE FROM conversation_message WHERE user_id = ? AND session_id = ?";
        jdbcTemplate.update(sql, userId, sessionId);
    }
}

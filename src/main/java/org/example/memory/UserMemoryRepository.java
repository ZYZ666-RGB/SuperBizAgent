package org.example.memory;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

@Repository
public class UserMemoryRepository {

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<UserMemory> rowMapper = (rs, rowNum) -> {
        UserMemory memory = new UserMemory();
        memory.setId(rs.getLong("id"));
        memory.setMemoryId(rs.getString("memory_id"));
        memory.setUserId(rs.getString("user_id"));
        memory.setSessionId(rs.getString("session_id"));
        memory.setTaskId(rs.getString("task_id"));
        memory.setAgentId(rs.getString("agent_id"));
        memory.setAppId(rs.getString("app_id"));
        memory.setMemoryType(rs.getString("memory_type"));
        memory.setScopeType(rs.getString("scope_type"));
        memory.setContent(rs.getString("content"));
        memory.setEvidence(rs.getString("evidence"));
        memory.setEntities(rs.getString("entities"));
        memory.setMetadata(rs.getString("metadata"));
        memory.setSource(rs.getString("source"));
        memory.setImportance(rs.getDouble("importance"));
        memory.setConfidence(rs.getDouble("confidence"));
        memory.setEvidenceScore(rs.getDouble("evidence_score"));
        memory.setStabilityScore(rs.getDouble("stability_score"));
        memory.setFutureUsefulnessScore(rs.getDouble("future_usefulness_score"));
        memory.setSafetyScore(rs.getInt("safety_score"));
        memory.setAccessCount(rs.getInt("access_count"));
        Timestamp lastAccessedAt = rs.getTimestamp("last_accessed_at");
        if (lastAccessedAt != null) {
            memory.setLastAccessedAt(lastAccessedAt.toLocalDateTime());
        }
        memory.setEnabled(rs.getInt("enabled") == 1);
        Timestamp createdAt = rs.getTimestamp("created_at");
        if (createdAt != null) {
            memory.setCreatedAt(createdAt.toLocalDateTime());
        }
        Timestamp updatedAt = rs.getTimestamp("updated_at");
        if (updatedAt != null) {
            memory.setUpdatedAt(updatedAt.toLocalDateTime());
        }
        return memory;
    };

    public UserMemoryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void insert(UserMemory memory) {
        String sql = """
                INSERT INTO user_memory(
                    memory_id, user_id, session_id, task_id, agent_id, app_id,
                    memory_type, scope_type, content, evidence, entities, metadata, source,
                    importance, confidence, evidence_score, stability_score,
                    future_usefulness_score, safety_score, enabled
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        jdbcTemplate.update(
                sql,
                memory.getMemoryId(),
                memory.getUserId(),
                memory.getSessionId(),
                memory.getTaskId(),
                memory.getAgentId(),
                memory.getAppId(),
                memory.getMemoryType(),
                memory.getScopeType(),
                memory.getContent(),
                memory.getEvidence(),
                memory.getEntities(),
                memory.getMetadata(),
                memory.getSource(),
                memory.getImportance(),
                memory.getConfidence(),
                memory.getEvidenceScore(),
                memory.getStabilityScore(),
                memory.getFutureUsefulnessScore(),
                memory.getSafetyScore(),
                memory.getEnabled() == null || memory.getEnabled() ? 1 : 0);
    }

    public List<UserMemory> findEnabledByUserAndType(String userId, String memoryType, int limit) {
        String sql = """
                SELECT *
                FROM user_memory
                WHERE user_id = ? AND memory_type = ? AND enabled = 1
                ORDER BY importance DESC, updated_at DESC, id DESC
                LIMIT ?
                """;
        return jdbcTemplate.query(sql, rowMapper, userId, memoryType, Math.max(1, limit));
    }

    public List<UserMemory> findEnabledForPrompt(String userId, int limit) {
        String sql = """
                SELECT *
                FROM user_memory
                WHERE user_id = ?
                  AND enabled = 1
                  AND memory_type IN (
                    'semantic', 'preference', 'project_context', 'career_goal',
                    'skill', 'environment', 'task'
                  )
                ORDER BY importance DESC, access_count DESC, updated_at DESC, id DESC
                LIMIT ?
                """;
        return jdbcTemplate.query(sql, rowMapper, userId, Math.max(1, limit));
    }

    public List<UserMemory> findRecentEpisodicForPrompt(String userId, int limit) {
        String sql = """
                SELECT *
                FROM user_memory
                WHERE user_id = ? AND enabled = 1 AND memory_type = 'episodic'
                ORDER BY importance DESC, updated_at DESC, id DESC
                LIMIT ?
                """;
        return jdbcTemplate.query(sql, rowMapper, userId, Math.max(1, limit));
    }

    public Optional<UserMemory> findEnabledExactContent(String userId, String memoryType, String content) {
        String sql = """
                SELECT *
                FROM user_memory
                WHERE user_id = ? AND memory_type = ? AND content = ? AND enabled = 1
                ORDER BY id DESC
                LIMIT 1
                """;
        List<UserMemory> memories = jdbcTemplate.query(sql, rowMapper, userId, memoryType, content);
        if (memories.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(memories.get(0));
    }

    public long countEnabledByUser(String userId) {
        String sql = "SELECT COUNT(*) FROM user_memory WHERE user_id = ? AND enabled = 1";
        Long count = jdbcTemplate.queryForObject(sql, Long.class, userId);
        return count == null ? 0L : count;
    }
}

package org.example.memory.task;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

@Repository
public class AgentTaskStateRepository {

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<AgentTaskState> rowMapper = (rs, rowNum) -> {
        AgentTaskState state = new AgentTaskState();
        state.setId(rs.getLong("id"));
        state.setUserId(rs.getString("user_id"));
        state.setSessionId(rs.getString("session_id"));
        state.setTaskId(rs.getString("task_id"));
        state.setAgentId(rs.getString("agent_id"));
        state.setStage(rs.getString("stage"));
        state.setPlannerPlan(rs.getString("planner_plan"));
        state.setExecutorFeedback(rs.getString("executor_feedback"));
        state.setToolResults(rs.getString("tool_results"));
        state.setFinalReport(rs.getString("final_report"));
        state.setStatus(rs.getString("status"));
        Timestamp createdAt = rs.getTimestamp("created_at");
        if (createdAt != null) {
            state.setCreatedAt(createdAt.toLocalDateTime());
        }
        Timestamp updatedAt = rs.getTimestamp("updated_at");
        if (updatedAt != null) {
            state.setUpdatedAt(updatedAt.toLocalDateTime());
        }
        return state;
    };

    public AgentTaskStateRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void upsert(AgentTaskState state) {
        Optional<AgentTaskState> existing = findByUserAndTask(state.getUserId(), state.getTaskId());
        if (existing.isPresent()) {
            update(state);
        } else {
            insert(state);
        }
    }

    public Optional<AgentTaskState> findByUserAndTask(String userId, String taskId) {
        String sql = """
                SELECT *
                FROM agent_task_state
                WHERE user_id = ? AND task_id = ?
                LIMIT 1
                """;
        List<AgentTaskState> states = jdbcTemplate.query(sql, rowMapper, userId, taskId);
        if (states.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(states.get(0));
    }

    public List<AgentTaskState> findRecentByUser(String userId, int limit) {
        String sql = """
                SELECT *
                FROM agent_task_state
                WHERE user_id = ?
                ORDER BY updated_at DESC, id DESC
                LIMIT ?
                """;
        return jdbcTemplate.query(sql, rowMapper, userId, Math.max(1, limit));
    }

    private void insert(AgentTaskState state) {
        String sql = """
                INSERT INTO agent_task_state(
                    user_id, session_id, task_id, agent_id, stage,
                    planner_plan, executor_feedback, tool_results, final_report, status
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        jdbcTemplate.update(
                sql,
                state.getUserId(),
                state.getSessionId(),
                state.getTaskId(),
                defaultText(state.getAgentId(), "aiops_agent"),
                state.getStage(),
                state.getPlannerPlan(),
                state.getExecutorFeedback(),
                state.getToolResults(),
                state.getFinalReport(),
                state.getStatus());
    }

    private void update(AgentTaskState state) {
        String sql = """
                UPDATE agent_task_state
                SET session_id = COALESCE(?, session_id),
                    agent_id = COALESCE(?, agent_id),
                    stage = COALESCE(?, stage),
                    planner_plan = COALESCE(?, planner_plan),
                    executor_feedback = COALESCE(?, executor_feedback),
                    tool_results = COALESCE(?, tool_results),
                    final_report = COALESCE(?, final_report),
                    status = COALESCE(?, status),
                    updated_at = CURRENT_TIMESTAMP
                WHERE user_id = ? AND task_id = ?
                """;
        jdbcTemplate.update(
                sql,
                state.getSessionId(),
                state.getAgentId(),
                state.getStage(),
                state.getPlannerPlan(),
                state.getExecutorFeedback(),
                state.getToolResults(),
                state.getFinalReport(),
                state.getStatus(),
                state.getUserId(),
                state.getTaskId());
    }

    private String defaultText(String value, String defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return value;
    }
}

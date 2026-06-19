package org.example.memory.task;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class AgentTaskStateService {

    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_FINISHED = "FINISHED";
    public static final String STATUS_FAILED = "FAILED";

    private final AgentTaskStateRepository repository;

    public AgentTaskStateService(AgentTaskStateRepository repository) {
        this.repository = repository;
    }

    public void startTask(String userId, String sessionId, String taskId, String agentId, String stage) {
        AgentTaskState state = baseState(userId, sessionId, taskId, agentId);
        state.setStage(defaultText(stage, "STARTED"));
        state.setStatus(STATUS_RUNNING);
        repository.upsert(state);
    }

    public void saveSnapshot(
            String userId,
            String sessionId,
            String taskId,
            String agentId,
            String stage,
            String plannerPlan,
            String executorFeedback,
            String toolResults) {
        AgentTaskState state = baseState(userId, sessionId, taskId, agentId);
        state.setStage(defaultText(stage, "SNAPSHOT"));
        state.setPlannerPlan(emptyToNull(plannerPlan));
        state.setExecutorFeedback(emptyToNull(executorFeedback));
        state.setToolResults(emptyToNull(toolResults));
        state.setStatus(STATUS_RUNNING);
        repository.upsert(state);
    }

    public void finishTask(
            String userId,
            String sessionId,
            String taskId,
            String agentId,
            String finalReport) {
        AgentTaskState state = baseState(userId, sessionId, taskId, agentId);
        state.setStage("FINISHED");
        state.setFinalReport(emptyToNull(finalReport));
        state.setStatus(STATUS_FINISHED);
        repository.upsert(state);
    }

    public void failTask(
            String userId,
            String sessionId,
            String taskId,
            String agentId,
            String errorMessage) {
        AgentTaskState state = baseState(userId, sessionId, taskId, agentId);
        state.setStage("FAILED");
        state.setToolResults(emptyToNull(errorMessage));
        state.setStatus(STATUS_FAILED);
        repository.upsert(state);
    }

    public Optional<AgentTaskState> findByUserAndTask(String userId, String taskId) {
        return repository.findByUserAndTask(userId, taskId);
    }

    public List<AgentTaskState> findRecentByUser(String userId, int limit) {
        return repository.findRecentByUser(userId, limit);
    }

    private AgentTaskState baseState(String userId, String sessionId, String taskId, String agentId) {
        AgentTaskState state = new AgentTaskState();
        state.setUserId(defaultText(userId, "default_user"));
        state.setSessionId(emptyToNull(sessionId));
        state.setTaskId(taskId);
        state.setAgentId(defaultText(agentId, "aiops_agent"));
        return state;
    }

    private String defaultText(String value, String defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return value;
    }

    private String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}

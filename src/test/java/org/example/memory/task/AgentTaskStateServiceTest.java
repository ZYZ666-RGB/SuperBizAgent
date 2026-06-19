package org.example.memory.task;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AgentTaskStateServiceTest {

    private EmbeddedDatabase database;
    private AgentTaskStateService service;

    @BeforeEach
    void setUp() {
        database = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .setName("agent-task-state-test-" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE")
                .build();
        new ResourceDatabasePopulator(new ClassPathResource("db/schema-memory-test.sql"))
                .execute(database);

        AgentTaskStateRepository repository = new AgentTaskStateRepository(new JdbcTemplate(database));
        service = new AgentTaskStateService(repository);
    }

    @AfterEach
    void tearDown() {
        database.shutdown();
    }

    @Test
    void taskStateRollsForwardFromStartToSnapshotToFinished() {
        service.startTask("user-a", "session-a", "task-1", "aiops_agent", "REQUEST_RECEIVED");
        service.saveSnapshot(
                "user-a",
                "session-a",
                "task-1",
                "aiops_agent",
                "SUPERVISOR_FINISHED",
                "planner plan",
                "executor feedback",
                "tool results");
        service.finishTask("user-a", "session-a", "task-1", "aiops_agent", "final report");

        AgentTaskState state = service.findByUserAndTask("user-a", "task-1").orElseThrow();

        assertThat(state.getSessionId()).isEqualTo("session-a");
        assertThat(state.getStage()).isEqualTo("FINISHED");
        assertThat(state.getPlannerPlan()).isEqualTo("planner plan");
        assertThat(state.getExecutorFeedback()).isEqualTo("executor feedback");
        assertThat(state.getToolResults()).isEqualTo("tool results");
        assertThat(state.getFinalReport()).isEqualTo("final report");
        assertThat(state.getStatus()).isEqualTo(AgentTaskStateService.STATUS_FINISHED);
    }

    @Test
    void taskStateIsScopedByUserAndTask() {
        service.startTask("user-a", "session-a", "task-1", "aiops_agent", "REQUEST_RECEIVED");
        service.startTask("user-b", "session-b", "task-1", "aiops_agent", "REQUEST_RECEIVED");
        service.failTask("user-a", "session-a", "task-1", "aiops_agent", "failed");

        assertThat(service.findByUserAndTask("user-a", "task-1").orElseThrow().getStatus())
                .isEqualTo(AgentTaskStateService.STATUS_FAILED);
        assertThat(service.findByUserAndTask("user-b", "task-1").orElseThrow().getStatus())
                .isEqualTo(AgentTaskStateService.STATUS_RUNNING);
        assertThat(service.findRecentByUser("user-a", 10)).hasSize(1);
    }
}

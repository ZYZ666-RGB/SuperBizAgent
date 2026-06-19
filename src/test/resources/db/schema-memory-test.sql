CREATE TABLE conversation_message (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id VARCHAR(64) NOT NULL,
    session_id VARCHAR(64) NOT NULL,
    role VARCHAR(20) NOT NULL,
    content CLOB NOT NULL,
    token_count INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_user_session_time ON conversation_message(user_id, session_id, created_at);
CREATE INDEX idx_user_session_id ON conversation_message(user_id, session_id, id);

CREATE TABLE conversation_summary (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id VARCHAR(64) NOT NULL,
    session_id VARCHAR(64) NOT NULL,
    summary CLOB NOT NULL,
    summarized_until_message_id BIGINT DEFAULT 0,
    summarized_message_count INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_user_session UNIQUE (user_id, session_id)
);

CREATE TABLE user_memory (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    memory_id VARCHAR(64) NOT NULL UNIQUE,
    user_id VARCHAR(64) NOT NULL,
    session_id VARCHAR(64),
    task_id VARCHAR(64),
    agent_id VARCHAR(64),
    app_id VARCHAR(64) DEFAULT 'super_biz_agent',
    memory_type VARCHAR(30) NOT NULL,
    scope_type VARCHAR(30) DEFAULT 'user',
    content CLOB NOT NULL,
    evidence CLOB,
    entities CLOB,
    metadata CLOB,
    source VARCHAR(50),
    importance DOUBLE DEFAULT 0.5,
    confidence DOUBLE DEFAULT 1.0,
    evidence_score DOUBLE DEFAULT 0.0,
    stability_score DOUBLE DEFAULT 0.0,
    future_usefulness_score DOUBLE DEFAULT 0.0,
    safety_score INT DEFAULT 1,
    access_count INT DEFAULT 0,
    last_accessed_at TIMESTAMP NULL,
    enabled TINYINT DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_user_type ON user_memory(user_id, memory_type);
CREATE INDEX idx_user_scope ON user_memory(user_id, scope_type);
CREATE INDEX idx_user_enabled ON user_memory(user_id, enabled);
CREATE INDEX idx_user_agent ON user_memory(user_id, agent_id);

CREATE TABLE agent_task_state (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id VARCHAR(64) NOT NULL,
    session_id VARCHAR(64),
    task_id VARCHAR(64) NOT NULL,
    agent_id VARCHAR(64) DEFAULT 'aiops_agent',
    stage VARCHAR(50),
    planner_plan CLOB,
    executor_feedback CLOB,
    tool_results CLOB,
    final_report CLOB,
    status VARCHAR(30),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_user_task UNIQUE (user_id, task_id)
);

CREATE INDEX idx_user_status ON agent_task_state(user_id, status);
CREATE INDEX idx_user_session_task ON agent_task_state(user_id, session_id, task_id);

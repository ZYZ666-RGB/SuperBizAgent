CREATE TABLE IF NOT EXISTS conversation_message (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id VARCHAR(64) NOT NULL,
    session_id VARCHAR(64) NOT NULL,
    role VARCHAR(20) NOT NULL COMMENT 'user/assistant/system/tool',
    content TEXT NOT NULL,
    token_count INT DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_session_time (user_id, session_id, created_at),
    INDEX idx_user_session_id (user_id, session_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS conversation_summary (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id VARCHAR(64) NOT NULL,
    session_id VARCHAR(64) NOT NULL,
    summary TEXT NOT NULL,
    summarized_until_message_id BIGINT DEFAULT 0,
    summarized_message_count INT DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_session (user_id, session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS user_memory (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    memory_id VARCHAR(64) NOT NULL UNIQUE,

    user_id VARCHAR(64) NOT NULL,
    session_id VARCHAR(64),
    task_id VARCHAR(64),
    agent_id VARCHAR(64),
    app_id VARCHAR(64) DEFAULT 'super_biz_agent',

    memory_type VARCHAR(30) NOT NULL COMMENT 'semantic/episodic/perceptual/preference/project_context/career_goal/skill/environment/task',
    scope_type VARCHAR(30) DEFAULT 'user' COMMENT 'user/session/project/task',

    content TEXT NOT NULL,
    evidence TEXT,
    entities JSON,
    metadata JSON,

    source VARCHAR(50) COMMENT 'user_explicit/auto_extracted/consolidated/imported',
    importance DOUBLE DEFAULT 0.5,
    confidence DOUBLE DEFAULT 1.0,

    evidence_score DOUBLE DEFAULT 0.0,
    stability_score DOUBLE DEFAULT 0.0,
    future_usefulness_score DOUBLE DEFAULT 0.0,
    safety_score INT DEFAULT 1,

    access_count INT DEFAULT 0,
    last_accessed_at DATETIME NULL,
    enabled TINYINT DEFAULT 1,

    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_user_type (user_id, memory_type),
    INDEX idx_user_scope (user_id, scope_type),
    INDEX idx_user_enabled (user_id, enabled),
    INDEX idx_user_agent (user_id, agent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS agent_task_state (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id VARCHAR(64) NOT NULL,
    session_id VARCHAR(64),
    task_id VARCHAR(64) NOT NULL,
    agent_id VARCHAR(64) DEFAULT 'aiops_agent',

    stage VARCHAR(50),
    planner_plan TEXT,
    executor_feedback TEXT,
    tool_results TEXT,
    final_report TEXT,
    status VARCHAR(30) COMMENT 'RUNNING/FINISHED/FAILED',

    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    UNIQUE KEY uk_user_task (user_id, task_id),
    INDEX idx_user_status (user_id, status),
    INDEX idx_user_session_task (user_id, session_id, task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

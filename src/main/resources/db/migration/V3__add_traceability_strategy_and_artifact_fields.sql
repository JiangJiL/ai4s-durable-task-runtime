-- 面向运行中心的可追溯信息：策略保留版本，产物作为独立一等对象登记。
CREATE TABLE step_strategy (
    id CHAR(36) NOT NULL,
    task_step_id CHAR(36) NOT NULL,
    version INT NOT NULL,
    strategy_summary VARCHAR(1000) NOT NULL,
    decision_rationale TEXT NULL,
    planned_actions_json JSON NULL,
    expected_artifacts_json JSON NULL,
    author_type VARCHAR(32) NOT NULL,
    author_id VARCHAR(128) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_step_strategy_step FOREIGN KEY (task_step_id) REFERENCES task_step (id),
    CONSTRAINT uk_step_strategy_version UNIQUE (task_step_id, version),
    INDEX idx_step_strategy_step_created_at (task_step_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='步骤核心策略的版本化审计记录';

-- artifact 原表已存在，以下字段让 UI 不必从 metadata_json 猜测产物含义。
ALTER TABLE artifact
    ADD COLUMN artifact_type VARCHAR(32) NOT NULL DEFAULT 'OTHER' AFTER task_step_id,
    ADD COLUMN display_name VARCHAR(255) NOT NULL DEFAULT '未命名产物' AFTER artifact_type,
    ADD COLUMN summary TEXT NULL AFTER display_name,
    ADD COLUMN produced_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) AFTER metadata_json,
    ADD INDEX idx_artifact_step_created_at (task_step_id, created_at);

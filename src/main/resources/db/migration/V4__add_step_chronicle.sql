-- 每条记录是面向审阅者的关键事实，不承担状态迁移或全量工具日志职责。
CREATE TABLE step_chronicle (
    id CHAR(36) NOT NULL,
    task_step_id CHAR(36) NOT NULL,
    entry_type VARCHAR(24) NOT NULL,
    title VARCHAR(255) NOT NULL,
    summary TEXT NOT NULL,
    details_json JSON NULL,
    actor_type VARCHAR(32) NOT NULL,
    actor_id VARCHAR(255) NOT NULL,
    trace_id VARCHAR(255) NOT NULL,
    occurred_at TIMESTAMP(6) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_step_chronicle_step FOREIGN KEY (task_step_id) REFERENCES task_step (id),
    INDEX idx_step_chronicle_step_occurred_at (task_step_id, occurred_at)
);

CREATE TABLE task (
    id CHAR(36) NOT NULL,
    goal TEXT NOT NULL,
    status VARCHAR(32) NOT NULL,
    current_step_id CHAR(36) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_task_status_updated_at (status, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE task_step (
    id CHAR(36) NOT NULL,
    task_id CHAR(36) NOT NULL,
    ordinal INT NOT NULL,
    step_type VARCHAR(32) NOT NULL,
    step_name VARCHAR(255) NOT NULL,
    status VARCHAR(32) NOT NULL,
    input_json JSON NULL,
    output_json JSON NULL,
    error_json JSON NULL,
    attempt INT NOT NULL DEFAULT 0,
    max_attempts INT NOT NULL DEFAULT 1,
    resume_mode VARCHAR(32) NOT NULL,
    checkpoint_uri VARCHAR(2048) NULL,
    next_retry_at DATETIME(6) NULL,
    started_at DATETIME(6) NULL,
    finished_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_task_step_task FOREIGN KEY (task_id) REFERENCES task (id),
    CONSTRAINT uk_task_step_ordinal UNIQUE (task_id, ordinal),
    INDEX idx_task_step_reconciliation (status, next_retry_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE external_job (
    id CHAR(36) NOT NULL,
    task_step_id CHAR(36) NOT NULL,
    provider VARCHAR(64) NOT NULL,
    external_job_id VARCHAR(255) NULL,
    idempotency_key VARCHAR(255) NOT NULL,
    status VARCHAR(32) NOT NULL,
    request_json JSON NOT NULL,
    result_json JSON NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_external_job_step FOREIGN KEY (task_step_id) REFERENCES task_step (id),
    CONSTRAINT uk_external_job_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT uk_external_job_provider_id UNIQUE (provider, external_job_id),
    INDEX idx_external_job_status_updated_at (status, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE task_event (
    id BIGINT NOT NULL AUTO_INCREMENT,
    task_id CHAR(36) NOT NULL,
    task_step_id CHAR(36) NULL,
    event_type VARCHAR(64) NOT NULL,
    payload_json JSON NOT NULL,
    trace_id VARCHAR(128) NOT NULL,
    occurred_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_task_event_task FOREIGN KEY (task_id) REFERENCES task (id),
    CONSTRAINT fk_task_event_step FOREIGN KEY (task_step_id) REFERENCES task_step (id),
    INDEX idx_task_event_task_id_id (task_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE artifact (
    id CHAR(36) NOT NULL,
    task_id CHAR(36) NOT NULL,
    task_step_id CHAR(36) NULL,
    uri VARCHAR(2048) NOT NULL,
    sha256 CHAR(64) NOT NULL,
    size_bytes BIGINT NOT NULL,
    metadata_json JSON NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_artifact_task FOREIGN KEY (task_id) REFERENCES task (id),
    CONSTRAINT fk_artifact_step FOREIGN KEY (task_step_id) REFERENCES task_step (id),
    INDEX idx_artifact_task_id (task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE checkpoint (
    id CHAR(36) NOT NULL,
    task_step_id CHAR(36) NOT NULL,
    checkpoint_kind VARCHAR(32) NOT NULL,
    uri VARCHAR(2048) NOT NULL,
    metadata_json JSON NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_checkpoint_step FOREIGN KEY (task_step_id) REFERENCES task_step (id),
    INDEX idx_checkpoint_step_id_created_at (task_step_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE outbox (
    id CHAR(36) NOT NULL,
    aggregate_type VARCHAR(32) NOT NULL,
    aggregate_id CHAR(36) NOT NULL,
    message_type VARCHAR(64) NOT NULL,
    payload_json JSON NOT NULL,
    idempotency_key VARCHAR(255) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    published_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_outbox_idempotency_key UNIQUE (idempotency_key),
    INDEX idx_outbox_status_created_at (status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Agent Step 的租约属于 Runtime State，而不是 OpenClaw Session 或 Conversation。
-- Worker 中断时不必清理；租约过期后由下一次 claim 原子接手。
ALTER TABLE task_step
    ADD COLUMN worker_id VARCHAR(128) NULL COMMENT '当前领取该步骤的 Runtime Worker 身份',
    ADD COLUMN lease_token CHAR(36) NULL COMMENT '用于校验 Agent Intent 的一次性租约令牌',
    ADD COLUMN lease_expires_at DATETIME(6) NULL COMMENT '租约过期时间，过期后允许其他 Session 接手',
    ADD COLUMN claimed_at DATETIME(6) NULL COMMENT '本次租约领取时间';

CREATE INDEX idx_task_step_claimable
    ON task_step (task_id, status, lease_expires_at);

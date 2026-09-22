package io.github.jiangjil.ai4s.runtime.application;

import io.github.jiangjil.ai4s.runtime.application.port.TaskStore;
import io.github.jiangjil.ai4s.runtime.domain.Task;

import java.util.List;

/**
 * 为普通 OpenClaw Agent 提供恢复入口。
 * Agent 重启后先读取未终态任务，再领取其当前 Step；不需要记住 taskId。
 */
public final class ListActiveTasksService {
    private final TaskStore taskStore;

    public ListActiveTasksService(TaskStore taskStore) {
        this.taskStore = taskStore;
    }

    public List<Task> list(int limit) {
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("limit must be between 1 and 100");
        }
        return taskStore.findActiveTasks(limit);
    }
}

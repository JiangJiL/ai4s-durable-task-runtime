package io.github.jiangjil.ai4s.runtime.application;

import io.github.jiangjil.ai4s.runtime.application.port.TaskStore;
import io.github.jiangjil.ai4s.runtime.domain.Task;
import io.github.jiangjil.ai4s.runtime.domain.TaskStatus;
import java.util.List;

/** 运行中心读取历史任务；与恢复入口的“仅活跃任务”语义刻意分离。 */
public final class ListTasksService {
    private final TaskStore taskStore;
    public ListTasksService(TaskStore taskStore) { this.taskStore = taskStore; }
    public List<Task> list(TaskStatus status, int limit) {
        if (limit < 1 || limit > 200) throw new IllegalArgumentException("limit must be between 1 and 200");
        return taskStore.findTasks(status, limit);
    }
}

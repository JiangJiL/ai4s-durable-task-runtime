package io.github.jiangjil.ai4s.runtime.application.port;

import io.github.jiangjil.ai4s.runtime.domain.TaskEvent;

public interface TaskEventStore {
    void append(TaskEvent event);
}

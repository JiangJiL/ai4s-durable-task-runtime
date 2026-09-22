package io.github.jiangjil.ai4s.runtime.application;

import io.github.jiangjil.ai4s.runtime.application.port.TaskStore;
import io.github.jiangjil.ai4s.runtime.domain.TaskStep;

import java.time.Clock;
import java.util.UUID;

/** 只向已经领取当前步骤的 Worker 提供带执行权限的确定性 Runtime Context。 */
public final class GetClaimedRuntimeContextService {
    private final GetTaskRuntimeStateService stateService;
    private final RuntimeContextBuilder contextBuilder;
    private final Clock clock;

    public GetClaimedRuntimeContextService(TaskStore taskStore, RuntimeContextBuilder contextBuilder, Clock clock) {
        this.stateService = new GetTaskRuntimeStateService(taskStore);
        this.contextBuilder = contextBuilder;
        this.clock = clock;
    }

    public RuntimeContextBuilder.RuntimeContext get(UUID taskId, UUID stepId, String leaseToken) {
        GetTaskRuntimeStateService.TaskRuntimeState state = stateService.get(taskId);
        TaskStep current = state.currentStep();
        if (current == null || !current.id().equals(stepId)) {
            throw new IllegalStateException("请求的步骤不是当前 Runtime Step");
        }
        current.requireActiveLease(leaseToken, clock.instant());
        return contextBuilder.build(state);
    }
}

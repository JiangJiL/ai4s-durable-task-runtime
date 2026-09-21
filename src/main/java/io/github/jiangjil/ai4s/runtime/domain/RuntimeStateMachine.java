package io.github.jiangjil.ai4s.runtime.domain;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 确定性的生命周期规则。持久化层与 Agent 适配器必须调用本类，
 * 不能直接赋值修改状态。
 */
public final class RuntimeStateMachine {

    private static final Map<TaskStatus, Set<TaskStatus>> TASK_TRANSITIONS = new EnumMap<>(TaskStatus.class);
    private static final Map<StepStatus, Set<StepStatus>> STEP_TRANSITIONS = new EnumMap<>(StepStatus.class);

    static {
        TASK_TRANSITIONS.put(TaskStatus.CREATED, EnumSet.of(TaskStatus.RUNNING, TaskStatus.CANCELLED));
        TASK_TRANSITIONS.put(TaskStatus.RUNNING, EnumSet.of(TaskStatus.WAITING, TaskStatus.PAUSED,
                TaskStatus.SUCCEEDED, TaskStatus.FAILED, TaskStatus.CANCELLED));
        TASK_TRANSITIONS.put(TaskStatus.WAITING, EnumSet.of(TaskStatus.RUNNING, TaskStatus.PAUSED,
                TaskStatus.FAILED, TaskStatus.CANCELLED));
        TASK_TRANSITIONS.put(TaskStatus.PAUSED, EnumSet.of(TaskStatus.RUNNING, TaskStatus.CANCELLED));
        TASK_TRANSITIONS.put(TaskStatus.SUCCEEDED, EnumSet.noneOf(TaskStatus.class));
        TASK_TRANSITIONS.put(TaskStatus.FAILED, EnumSet.noneOf(TaskStatus.class));
        TASK_TRANSITIONS.put(TaskStatus.CANCELLED, EnumSet.noneOf(TaskStatus.class));

        STEP_TRANSITIONS.put(StepStatus.PENDING, EnumSet.of(StepStatus.READY, StepStatus.SKIPPED, StepStatus.CANCELLED));
        STEP_TRANSITIONS.put(StepStatus.READY, EnumSet.of(StepStatus.DISPATCHING, StepStatus.CANCELLED));
        STEP_TRANSITIONS.put(StepStatus.DISPATCHING, EnumSet.of(StepStatus.RUNNING, StepStatus.WAITING_EXTERNAL,
                StepStatus.RETRY_WAIT, StepStatus.FAILED, StepStatus.CANCELLED));
        STEP_TRANSITIONS.put(StepStatus.RUNNING, EnumSet.of(StepStatus.WAITING_EXTERNAL, StepStatus.SUCCEEDED,
                StepStatus.RETRY_WAIT, StepStatus.FAILED, StepStatus.CANCELLED));
        STEP_TRANSITIONS.put(StepStatus.WAITING_EXTERNAL, EnumSet.of(StepStatus.SUCCEEDED, StepStatus.RETRY_WAIT,
                StepStatus.FAILED, StepStatus.CANCELLED));
        STEP_TRANSITIONS.put(StepStatus.RETRY_WAIT, EnumSet.of(StepStatus.READY, StepStatus.FAILED, StepStatus.CANCELLED));
        STEP_TRANSITIONS.put(StepStatus.SUCCEEDED, EnumSet.noneOf(StepStatus.class));
        STEP_TRANSITIONS.put(StepStatus.FAILED, EnumSet.noneOf(StepStatus.class));
        STEP_TRANSITIONS.put(StepStatus.CANCELLED, EnumSet.noneOf(StepStatus.class));
        STEP_TRANSITIONS.put(StepStatus.SKIPPED, EnumSet.noneOf(StepStatus.class));
    }

    private RuntimeStateMachine() {
    }

    public static void requireTaskTransition(TaskStatus from, TaskStatus to) {
        requireTransition("task", from, to, TASK_TRANSITIONS);
    }

    public static void requireStepTransition(StepStatus from, StepStatus to) {
        requireTransition("step", from, to, STEP_TRANSITIONS);
    }

    private static <S extends Enum<S>> void requireTransition(
            String aggregate, S from, S to, Map<S, Set<S>> transitions) {
        if (!transitions.get(from).contains(to)) {
            throw new InvalidStateTransitionException(aggregate, from, to);
        }
    }
}

package io.github.jiangjil.ai4s.runtime.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RuntimeStateMachineTest {

    @Test
    void allowsNormalTaskLifecycle() {
        assertDoesNotThrow(() -> RuntimeStateMachine.requireTaskTransition(TaskStatus.CREATED, TaskStatus.RUNNING));
        assertDoesNotThrow(() -> RuntimeStateMachine.requireTaskTransition(TaskStatus.RUNNING, TaskStatus.WAITING));
        assertDoesNotThrow(() -> RuntimeStateMachine.requireTaskTransition(TaskStatus.WAITING, TaskStatus.RUNNING));
        assertDoesNotThrow(() -> RuntimeStateMachine.requireTaskTransition(TaskStatus.RUNNING, TaskStatus.SUCCEEDED));
    }

    @Test
    void forbidsLeavingTerminalTaskState() {
        assertThrows(InvalidStateTransitionException.class,
                () -> RuntimeStateMachine.requireTaskTransition(TaskStatus.SUCCEEDED, TaskStatus.RUNNING));
    }

    @Test
    void allowsAsyncStepLifecycle() {
        assertDoesNotThrow(() -> RuntimeStateMachine.requireStepTransition(StepStatus.PENDING, StepStatus.READY));
        assertDoesNotThrow(() -> RuntimeStateMachine.requireStepTransition(StepStatus.READY, StepStatus.DISPATCHING));
        assertDoesNotThrow(() -> RuntimeStateMachine.requireStepTransition(StepStatus.DISPATCHING, StepStatus.WAITING_EXTERNAL));
        assertDoesNotThrow(() -> RuntimeStateMachine.requireStepTransition(StepStatus.WAITING_EXTERNAL, StepStatus.SUCCEEDED));
    }

    @Test
    void permitsRetryOnlyThroughRetryWait() {
        assertDoesNotThrow(() -> RuntimeStateMachine.requireStepTransition(StepStatus.RUNNING, StepStatus.RETRY_WAIT));
        assertDoesNotThrow(() -> RuntimeStateMachine.requireStepTransition(StepStatus.RETRY_WAIT, StepStatus.READY));
        assertThrows(InvalidStateTransitionException.class,
                () -> RuntimeStateMachine.requireStepTransition(StepStatus.FAILED, StepStatus.READY));
    }
}

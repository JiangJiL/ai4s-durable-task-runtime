package io.github.jiangjil.ai4s.runtime.infrastructure.web;

import io.github.jiangjil.ai4s.runtime.application.ConcurrentTaskUpdateException;
import io.github.jiangjil.ai4s.runtime.application.TaskNotFoundException;
import io.github.jiangjil.ai4s.runtime.domain.InvalidStateTransitionException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 将可预期的 Runtime 命令错误转换为明确 HTTP 响应，不能吞掉后续排障所需的异常。 */
@RestControllerAdvice
public class RuntimeApiExceptionHandler {

    @ExceptionHandler(TaskNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    ApiError taskNotFound(TaskNotFoundException exception) {
        return new ApiError("TASK_NOT_FOUND", exception.getMessage());
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class, InvalidStateTransitionException.class})
    @ResponseStatus(HttpStatus.CONFLICT)
    ApiError invalidCommand(RuntimeException exception) {
        return new ApiError("INVALID_RUNTIME_COMMAND", exception.getMessage());
    }

    @ExceptionHandler(ConcurrentTaskUpdateException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    ApiError concurrentUpdate(ConcurrentTaskUpdateException exception) {
        return new ApiError("CONCURRENT_TASK_UPDATE", exception.getMessage());
    }

    /** 返回稳定错误码，Agent 可按错误码调整下一步，而无需猜测自然语言错误信息。 */
    public record ApiError(String code, String message) {
    }
}

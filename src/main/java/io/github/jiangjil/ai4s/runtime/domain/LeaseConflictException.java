package io.github.jiangjil.ai4s.runtime.domain;

/** Agent 使用过期、错误或已被接手的租约令牌回写 Runtime 时抛出。 */
public final class LeaseConflictException extends IllegalStateException {
    public LeaseConflictException(String message) {
        super(message);
    }
}

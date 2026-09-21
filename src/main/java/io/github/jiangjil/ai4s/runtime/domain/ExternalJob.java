package io.github.jiangjil.ai4s.runtime.domain;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** 持久化的提交意图，以及后续绑定的外部执行引用。 */
public record ExternalJob(
        UUID id,
        UUID taskStepId,
        String provider,
        String externalJobId,
        String idempotencyKey,
        ExternalJobStatus status,
        Map<String, Object> request,
        Instant createdAt,
        Instant updatedAt) {

    public ExternalJob {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(taskStepId, "taskStepId is required");
        if (provider == null || provider.isBlank()) {
            throw new IllegalArgumentException("provider is required");
        }
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("idempotencyKey is required");
        }
        Objects.requireNonNull(status, "status is required");
        request = Map.copyOf(request == null ? Map.of() : request);
        Objects.requireNonNull(createdAt, "createdAt is required");
        Objects.requireNonNull(updatedAt, "updatedAt is required");
    }

    public ExternalJob markSubmitted(String submittedExternalJobId, Instant at) {
        if (status != ExternalJobStatus.SUBMITTING) {
            throw new IllegalStateException("Only a submitting job can be marked submitted: " + id);
        }
        if (submittedExternalJobId == null || submittedExternalJobId.isBlank()) {
            throw new IllegalArgumentException("externalJobId is required");
        }
        return new ExternalJob(id, taskStepId, provider, submittedExternalJobId, idempotencyKey,
                ExternalJobStatus.SUBMITTED, request, createdAt, at);
    }

    /** 应用执行器观测到的状态，但禁止任何不可能的状态回退。 */
    public ExternalJob reconcileTo(ExternalJobStatus observedStatus, Instant at) {
        Objects.requireNonNull(observedStatus, "observedStatus is required");
        if (status == observedStatus) {
            return this;
        }
        if (status == ExternalJobStatus.SUBMITTING || status == ExternalJobStatus.SUCCEEDED
                || status == ExternalJobStatus.FAILED || status == ExternalJobStatus.CANCELLED
                || status == ExternalJobStatus.LOST) {
            throw new IllegalStateException("Cannot reconcile " + status + " job: " + id);
        }
        if (observedStatus == ExternalJobStatus.SUBMITTING || observedStatus == ExternalJobStatus.SUBMITTED) {
            throw new IllegalStateException("Provider status cannot rewind to " + observedStatus);
        }
        return new ExternalJob(id, taskStepId, provider, externalJobId, idempotencyKey, observedStatus,
                request, createdAt, at);
    }
}

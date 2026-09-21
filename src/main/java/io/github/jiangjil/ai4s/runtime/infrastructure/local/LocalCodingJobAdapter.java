package io.github.jiangjil.ai4s.runtime.infrastructure.local;

import io.github.jiangjil.ai4s.runtime.application.port.ExternalJobAdapter;
import io.github.jiangjil.ai4s.runtime.domain.ExternalJob;
import io.github.jiangjil.ai4s.runtime.domain.ExternalJobObservation;
import io.github.jiangjil.ai4s.runtime.domain.ExternalJobStatus;
import io.github.jiangjil.ai4s.runtime.domain.FailureType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;

/**
 * Local process adapter for the Coding-task experiment. The file registry is
 * deliberately external to the Runtime process: after a Runtime crash, the
 * same idempotency key resolves to the same logical Job rather than spawning a
 * second shell command.
 */
public final class LocalCodingJobAdapter implements ExternalJobAdapter {
    private final Path registryRoot;

    public LocalCodingJobAdapter(Path registryRoot) {
        this.registryRoot = registryRoot.toAbsolutePath().normalize();
    }

    @Override
    public String submit(ExternalJob job) {
        String externalId = "local-" + sha256(job.idempotencyKey());
        Path marker = marker(externalId);
        try {
            Files.createDirectories(registryRoot);
            try {
                Files.writeString(marker, "STARTING\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
            } catch (java.nio.file.FileAlreadyExistsException ignored) {
                return externalId;
            }

            String command = requiredCommand(job.request());
            Path output = output(externalId);
            Path completion = completion(externalId);
            String wrapped = "(" + command + "); code=$?; printf '%s' \"$code\" > "
                    + shellQuote(completion.toString() + ".tmp") + "; mv "
                    + shellQuote(completion.toString() + ".tmp") + " " + shellQuote(completion.toString()) + "; exit $code";
            Process process = new ProcessBuilder("/bin/sh", "-c", wrapped)
                    .redirectErrorStream(true)
                    .redirectOutput(output.toFile())
                    .start();
            Files.writeString(marker, "RUNNING\n" + process.pid() + "\n", StandardCharsets.UTF_8,
                    StandardOpenOption.TRUNCATE_EXISTING);
            return externalId;
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot submit local coding Job " + externalId, exception);
        }
    }

    @Override
    public ExternalJobObservation getObservation(ExternalJob job) {
        if (job.externalJobId() == null || job.externalJobId().isBlank()) {
            return new ExternalJobObservation(ExternalJobStatus.LOST, FailureType.PROCESS_LOST);
        }
        try {
            Path completion = completion(job.externalJobId());
            if (Files.exists(completion)) {
                int exitCode = Integer.parseInt(Files.readString(completion, StandardCharsets.UTF_8).trim());
                return exitCode == 0
                        ? new ExternalJobObservation(ExternalJobStatus.SUCCEEDED, null)
                        : new ExternalJobObservation(ExternalJobStatus.FAILED, FailureType.APPLICATION_ERROR);
            }
            Path marker = marker(job.externalJobId());
            if (!Files.exists(marker)) {
                return new ExternalJobObservation(ExternalJobStatus.LOST, FailureType.PROCESS_LOST);
            }
            String[] lines = Files.readString(marker, StandardCharsets.UTF_8).split("\\R");
            if (lines.length < 2 || !"RUNNING".equals(lines[0])) {
                return new ExternalJobObservation(ExternalJobStatus.LOST, FailureType.PROCESS_LOST);
            }
            long pid = Long.parseLong(lines[1]);
            return ProcessHandle.of(pid).filter(ProcessHandle::isAlive).isPresent()
                    ? new ExternalJobObservation(ExternalJobStatus.RUNNING, null)
                    : new ExternalJobObservation(ExternalJobStatus.LOST, FailureType.PROCESS_LOST);
        } catch (IOException | NumberFormatException exception) {
            return new ExternalJobObservation(ExternalJobStatus.LOST, FailureType.PROCESS_LOST);
        }
    }

    private Path marker(String externalId) { return registryRoot.resolve(externalId + ".job"); }
    private Path completion(String externalId) { return registryRoot.resolve(externalId + ".exit"); }
    private Path output(String externalId) { return registryRoot.resolve(externalId + ".log"); }

    private static String requiredCommand(Map<String, Object> request) {
        Object command = request.get("command");
        if (!(command instanceof String value) || value.isBlank()) {
            throw new IllegalArgumentException("Local Coding Job request requires a nonblank command");
        }
        return value;
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static String shellQuote(String value) {
        return "'" + value.replace("'", "'\\\"'\\\"'") + "'";
    }
}

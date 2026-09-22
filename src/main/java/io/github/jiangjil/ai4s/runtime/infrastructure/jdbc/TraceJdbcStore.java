package io.github.jiangjil.ai4s.runtime.infrastructure.jdbc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.jiangjil.ai4s.runtime.application.port.TraceStore;
import io.github.jiangjil.ai4s.runtime.domain.ArtifactType;
import io.github.jiangjil.ai4s.runtime.domain.ChronicleEntryType;
import io.github.jiangjil.ai4s.runtime.domain.StepChronicleEntry;
import io.github.jiangjil.ai4s.runtime.domain.StepStrategy;
import io.github.jiangjil.ai4s.runtime.domain.TaskArtifact;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** MySQL 的策略/产物适配器。查询只返回索引和元数据，不读取 URI 指向的大文件。 */
@Repository
public class TraceJdbcStore implements TraceStore {
    private final JdbcTemplate jdbc; private final ObjectMapper json;
    public TraceJdbcStore(JdbcTemplate jdbc, ObjectMapper json) { this.jdbc = jdbc; this.json = json; }

    @Override public void appendStrategy(StepStrategy s) {
        jdbc.update("""
                INSERT INTO step_strategy (id, task_step_id, version, strategy_summary, decision_rationale,
                planned_actions_json, expected_artifacts_json, author_type, author_id, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, s.id().toString(), s.stepId().toString(), s.version(), s.summary(), s.rationale(),
                write(s.plannedActions()), write(s.expectedArtifacts()), s.authorType(), s.authorId(), Timestamp.from(s.createdAt()));
    }

    @Override public void insertArtifact(TaskArtifact a) {
        jdbc.update("""
                INSERT INTO artifact (id, task_id, task_step_id, artifact_type, display_name, summary, uri, sha256,
                size_bytes, metadata_json, produced_at, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, a.id().toString(), a.taskId().toString(), a.stepId() == null ? null : a.stepId().toString(),
                a.type().name(), a.displayName(), a.summary(), a.uri(), a.sha256(), a.sizeBytes(), write(a.metadata()),
                Timestamp.from(a.producedAt()), Timestamp.from(a.createdAt()));
    }

    @Override public void appendChronicle(StepChronicleEntry e) {
        jdbc.update("""
                INSERT INTO step_chronicle (id, task_step_id, entry_type, title, summary, details_json,
                actor_type, actor_id, trace_id, occurred_at, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, e.id().toString(), e.stepId().toString(), e.type().name(), e.title(), e.summary(), write(e.details()),
                e.actorType(), e.actorId(), e.traceId(), Timestamp.from(e.occurredAt()), Timestamp.from(e.createdAt()));
    }

    @Override public List<StepStrategy> findStrategies(UUID stepId) {
        return jdbc.query("""
                SELECT id, task_step_id, version, strategy_summary, decision_rationale, planned_actions_json,
                       expected_artifacts_json, author_type, author_id, created_at
                FROM step_strategy WHERE task_step_id = ? ORDER BY version
                """, (rs, n) -> new StepStrategy(UUID.fromString(rs.getString("id")), UUID.fromString(rs.getString("task_step_id")),
                rs.getInt("version"), rs.getString("strategy_summary"), rs.getString("decision_rationale"),
                list(rs.getString("planned_actions_json")), list(rs.getString("expected_artifacts_json")),
                rs.getString("author_type"), rs.getString("author_id"), rs.getTimestamp("created_at").toInstant()), stepId.toString());
    }

    @Override public List<TaskArtifact> findArtifacts(UUID taskId) {
        return jdbc.query("""
                SELECT id, task_id, task_step_id, artifact_type, display_name, summary, uri, sha256, size_bytes,
                       metadata_json, produced_at, created_at FROM artifact WHERE task_id = ? ORDER BY produced_at DESC
                """, (rs, n) -> new TaskArtifact(UUID.fromString(rs.getString("id")), UUID.fromString(rs.getString("task_id")),
                rs.getString("task_step_id") == null ? null : UUID.fromString(rs.getString("task_step_id")),
                ArtifactType.valueOf(rs.getString("artifact_type")), rs.getString("display_name"), rs.getString("summary"),
                rs.getString("uri"), rs.getString("sha256"), rs.getLong("size_bytes"), map(rs.getString("metadata_json")),
                rs.getTimestamp("produced_at").toInstant(), rs.getTimestamp("created_at").toInstant()), taskId.toString());
    }

    @Override public List<StepChronicleEntry> findChronicle(UUID stepId) {
        return jdbc.query("""
                SELECT id, task_step_id, entry_type, title, summary, details_json, actor_type, actor_id,
                       trace_id, occurred_at, created_at
                FROM step_chronicle WHERE task_step_id = ? ORDER BY occurred_at, created_at
                """, (rs, n) -> new StepChronicleEntry(UUID.fromString(rs.getString("id")),
                UUID.fromString(rs.getString("task_step_id")), ChronicleEntryType.valueOf(rs.getString("entry_type")),
                rs.getString("title"), rs.getString("summary"), map(rs.getString("details_json")),
                rs.getString("actor_type"), rs.getString("actor_id"), rs.getString("trace_id"),
                rs.getTimestamp("occurred_at").toInstant(), rs.getTimestamp("created_at").toInstant()), stepId.toString());
    }

    private String write(Object value) { try { return json.writeValueAsString(value); } catch (JsonProcessingException e) { throw new IllegalArgumentException("Trace JSON 无法序列化", e); } }
    private List<Map<String,Object>> list(String value) { if (value == null) return List.of(); try { return json.readValue(value, new TypeReference<>() {}); } catch (JsonProcessingException e) { throw new IllegalStateException("策略 JSON 无法解析", e); } }
    @SuppressWarnings("unchecked") private Map<String,Object> map(String value) { if (value == null) return Map.of(); try { return json.readValue(value, Map.class); } catch (JsonProcessingException e) { throw new IllegalStateException("产物 JSON 无法解析", e); } }
}

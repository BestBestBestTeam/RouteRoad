package com.routeroad.shared.infrastructure;

import com.routeroad.shared.domain.ApiCommandStatus;
import jakarta.persistence.Access;
import jakarta.persistence.AccessType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "api_command")
@Access(AccessType.FIELD)
public class ApiCommandEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "scenario_id", nullable = false)
    private UUID scenarioId;

    @Column(name = "actor_id", nullable = true)
    private UUID actorId;

    @Column(name = "idempotency_key", nullable = false)
    private UUID idempotencyKey;

    @Column(name = "type", nullable = false, columnDefinition = "text")
    private String type;

    @Column(name = "request_hash", nullable = false, columnDefinition = "text")
    private String requestHash;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "request", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> request;

    @Convert(converter = ApiCommandStatusConverter.class)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", nullable = false, length = 16)
    private ApiCommandStatus status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "result", nullable = true, columnDefinition = "jsonb")
    private JsonNode result;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "error", nullable = true, columnDefinition = "jsonb")
    private JsonNode error;

    @JdbcTypeCode(SqlTypes.TIMESTAMP_WITH_TIMEZONE)
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @JdbcTypeCode(SqlTypes.TIMESTAMP_WITH_TIMEZONE)
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ApiCommandEntity() {
    }

    public ApiCommandEntity(
            UUID id,
            UUID scenarioId,
            UUID actorId,
            UUID idempotencyKey,
            String type,
            String requestHash,
            Map<String, Object> request,
            ApiCommandStatus status,
            JsonNode result,
            JsonNode error,
            Instant createdAt,
            Instant updatedAt) {
        this.id = id;
        this.scenarioId = scenarioId;
        this.actorId = actorId;
        this.idempotencyKey = idempotencyKey;
        this.type = type;
        this.requestHash = requestHash;
        this.request = request;
        this.status = status;
        this.result = result;
        this.error = error;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getScenarioId() {
        return scenarioId;
    }

    public UUID getActorId() {
        return actorId;
    }

    public UUID getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getType() {
        return type;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public Map<String, Object> getRequest() {
        return request;
    }

    public ApiCommandStatus getStatus() {
        return status;
    }

    public JsonNode getResult() {
        return result;
    }

    public JsonNode getError() {
        return error;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}

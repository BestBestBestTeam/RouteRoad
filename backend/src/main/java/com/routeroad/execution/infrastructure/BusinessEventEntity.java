package com.routeroad.execution.infrastructure;

import com.routeroad.execution.domain.BusinessEventType;
import jakarta.persistence.Access;
import jakarta.persistence.AccessType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "business_event")
@Access(AccessType.FIELD)
@Immutable
public class BusinessEventEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "type", nullable = false, length = 32, updatable = false)
    private BusinessEventType type;

    @Column(name = "service_date", nullable = false, updatable = false)
    private LocalDate serviceDate;

    @Column(name = "order_id", nullable = true, updatable = false)
    private UUID orderId;

    @Column(name = "courier_id", nullable = true, updatable = false)
    private UUID courierId;

    @Column(name = "actor_id", nullable = true, updatable = false)
    private UUID actorId;

    @JdbcTypeCode(SqlTypes.TIMESTAMP_WITH_TIMEZONE)
    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @JdbcTypeCode(SqlTypes.TIMESTAMP_WITH_TIMEZONE)
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "reason", nullable = false, columnDefinition = "text", updatable = false)
    private String reason;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb", updatable = false)
    private Map<String, Object> payload;

    protected BusinessEventEntity() {
    }

    public BusinessEventEntity(
            UUID id,
            BusinessEventType type,
            LocalDate serviceDate,
            UUID orderId,
            UUID courierId,
            UUID actorId,
            Instant occurredAt,
            Instant createdAt,
            String reason,
            Map<String, Object> payload) {
        this.id = id;
        this.type = type;
        this.serviceDate = serviceDate;
        this.orderId = orderId;
        this.courierId = courierId;
        this.actorId = actorId;
        this.occurredAt = occurredAt;
        this.createdAt = createdAt;
        this.reason = reason;
        this.payload = payload;
    }

    public UUID getId() {
        return id;
    }

    public BusinessEventType getType() {
        return type;
    }

    public LocalDate getServiceDate() {
        return serviceDate;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public UUID getCourierId() {
        return courierId;
    }

    public UUID getActorId() {
        return actorId;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getReason() {
        return reason;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }
}

package com.routeroad.execution.infrastructure;

import com.routeroad.execution.domain.AlertOrderStatus;
import jakarta.persistence.Access;
import jakarta.persistence.AccessType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "alert_order")
@Access(AccessType.FIELD)
public class AlertOrderEntity {

    @EmbeddedId
    private AlertOrderId id;

    @Convert(converter = AlertOrderStatusConverter.class)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", nullable = false, length = 16)
    private AlertOrderStatus status;

    @JdbcTypeCode(SqlTypes.TIMESTAMP_WITH_TIMEZONE)
    @Column(name = "resolved_at", nullable = true)
    private Instant resolvedAt;

    @Column(name = "resolved_by", nullable = true)
    private UUID resolvedBy;

    @Column(name = "resolution_reason", nullable = true, columnDefinition = "text")
    private String resolutionReason;

    protected AlertOrderEntity() {
    }

    public AlertOrderEntity(
            AlertOrderId id,
            AlertOrderStatus status,
            Instant resolvedAt,
            UUID resolvedBy,
            String resolutionReason) {
        this.id = id;
        this.status = status;
        this.resolvedAt = resolvedAt;
        this.resolvedBy = resolvedBy;
        this.resolutionReason = resolutionReason;
    }

    public AlertOrderId getId() {
        return id;
    }

    public AlertOrderStatus getStatus() {
        return status;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public UUID getResolvedBy() {
        return resolvedBy;
    }

    public String getResolutionReason() {
        return resolutionReason;
    }
}

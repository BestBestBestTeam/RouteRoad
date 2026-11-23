package com.routeroad.execution.infrastructure;

import com.routeroad.execution.domain.AlertKind;
import com.routeroad.execution.domain.AlertScope;
import com.routeroad.execution.domain.AlertSeverity;
import com.routeroad.execution.domain.AlertStatus;
import jakarta.persistence.Access;
import jakarta.persistence.AccessType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "alert")
@Access(AccessType.FIELD)
public class AlertEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "source_event_id", nullable = false)
    private UUID sourceEventId;

    @Column(name = "service_date", nullable = false)
    private LocalDate serviceDate;

    @Column(name = "courier_id", nullable = true)
    private UUID courierId;

    @Convert(converter = AlertKindConverter.class)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "kind", nullable = false, length = 16)
    private AlertKind kind;

    @Convert(converter = AlertScopeConverter.class)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "scope", nullable = false, length = 16)
    private AlertScope scope;

    @Convert(converter = AlertSeverityConverter.class)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "severity", nullable = false, length = 16)
    private AlertSeverity severity;

    @Convert(converter = AlertStatusConverter.class)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", nullable = false, length = 16)
    private AlertStatus status;

    @Column(name = "reason_code", nullable = false, columnDefinition = "text")
    private String reasonCode;

    @Column(name = "reason_text", nullable = false, columnDefinition = "text")
    private String reasonText;

    @JdbcTypeCode(SqlTypes.TIMESTAMP_WITH_TIMEZONE)
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @JdbcTypeCode(SqlTypes.TIMESTAMP_WITH_TIMEZONE)
    @Column(name = "deadline_at", nullable = false)
    private Instant deadlineAt;

    @JdbcTypeCode(SqlTypes.TIMESTAMP_WITH_TIMEZONE)
    @Column(name = "resolved_at", nullable = true)
    private Instant resolvedAt;

    @Column(name = "resolved_by", nullable = true)
    private UUID resolvedBy;

    @Column(name = "resolution_reason", nullable = true, columnDefinition = "text")
    private String resolutionReason;

    protected AlertEntity() {
    }

    public AlertEntity(
            UUID id,
            UUID sourceEventId,
            LocalDate serviceDate,
            UUID courierId,
            AlertKind kind,
            AlertScope scope,
            AlertSeverity severity,
            AlertStatus status,
            String reasonCode,
            String reasonText,
            Instant createdAt,
            Instant deadlineAt,
            Instant resolvedAt,
            UUID resolvedBy,
            String resolutionReason) {
        this.id = id;
        this.sourceEventId = sourceEventId;
        this.serviceDate = serviceDate;
        this.courierId = courierId;
        this.kind = kind;
        this.scope = scope;
        this.severity = severity;
        this.status = status;
        this.reasonCode = reasonCode;
        this.reasonText = reasonText;
        this.createdAt = createdAt;
        this.deadlineAt = deadlineAt;
        this.resolvedAt = resolvedAt;
        this.resolvedBy = resolvedBy;
        this.resolutionReason = resolutionReason;
    }

    public UUID getId() {
        return id;
    }

    public UUID getSourceEventId() {
        return sourceEventId;
    }

    public LocalDate getServiceDate() {
        return serviceDate;
    }

    public UUID getCourierId() {
        return courierId;
    }

    public AlertKind getKind() {
        return kind;
    }

    public AlertScope getScope() {
        return scope;
    }

    public AlertSeverity getSeverity() {
        return severity;
    }

    public AlertStatus getStatus() {
        return status;
    }

    public String getReasonCode() {
        return reasonCode;
    }

    public String getReasonText() {
        return reasonText;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getDeadlineAt() {
        return deadlineAt;
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

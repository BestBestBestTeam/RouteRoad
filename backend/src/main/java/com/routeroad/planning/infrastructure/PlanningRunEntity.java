package com.routeroad.planning.infrastructure;

import com.routeroad.planning.domain.PlanningAlgorithm;
import com.routeroad.planning.domain.PlanningEventType;
import com.routeroad.planning.domain.PlanningRunType;
import jakarta.persistence.Access;
import jakarta.persistence.AccessType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
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
import java.util.UUID;

@Entity
@Table(name = "planning_run")
@Access(AccessType.FIELD)
@Immutable
public class PlanningRunEntity {

    @Id
    @Column(name = "plan_version", nullable = false, updatable = false)
    private Long planVersion;

    @Column(name = "request_id", nullable = false, unique = true, updatable = false)
    private UUID requestId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "run_type", nullable = false, length = 16, updatable = false)
    private PlanningRunType runType;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "event_type", nullable = true, length = 32, updatable = false)
    private PlanningEventType eventType;

    @Convert(converter = PlanningAlgorithmConverter.class)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "algorithm", nullable = false, length = 16, updatable = false)
    private PlanningAlgorithm algorithm;

    @Column(name = "plan_date", nullable = false, updatable = false)
    private LocalDate planDate;

    @JdbcTypeCode(SqlTypes.TIMESTAMP_WITH_TIMEZONE)
    @Column(name = "model_now", nullable = false, updatable = false)
    private Instant modelNow;

    @JdbcTypeCode(SqlTypes.TIMESTAMP_WITH_TIMEZONE)
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metrics", nullable = false, columnDefinition = "jsonb", updatable = false)
    private PlanningMetrics metrics;

    protected PlanningRunEntity() {
    }

    public Long getPlanVersion() {
        return planVersion;
    }

    public UUID getRequestId() {
        return requestId;
    }

    public PlanningRunType getRunType() {
        return runType;
    }

    public PlanningEventType getEventType() {
        return eventType;
    }

    public PlanningAlgorithm getAlgorithm() {
        return algorithm;
    }

    public LocalDate getPlanDate() {
        return planDate;
    }

    public Instant getModelNow() {
        return modelNow;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public PlanningMetrics getMetrics() {
        return metrics;
    }
}

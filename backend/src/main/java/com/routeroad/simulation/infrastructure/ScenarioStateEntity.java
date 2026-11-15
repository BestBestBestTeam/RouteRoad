package com.routeroad.simulation.infrastructure;

import jakarta.persistence.Access;
import jakarta.persistence.AccessType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "scenario_state")
@Access(AccessType.FIELD)
public class ScenarioStateEntity {

    @Id
    @Column(name = "scenario_id", nullable = false, updatable = false)
    private UUID scenarioId;

    @Column(name = "seed", nullable = true)
    private Integer seed;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "config", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> config;

    @Column(name = "service_date", nullable = false)
    private LocalDate serviceDate;

    @JdbcTypeCode(SqlTypes.TIMESTAMP_WITH_TIMEZONE)
    @Column(name = "model_anchor_at", nullable = false)
    private Instant modelAnchorAt;

    @JdbcTypeCode(SqlTypes.TIMESTAMP_WITH_TIMEZONE)
    @Column(name = "real_anchor_at", nullable = false)
    private Instant realAnchorAt;

    @Column(name = "speed", nullable = false)
    private Double speed;

    @Column(name = "revision", nullable = false)
    private Long revision;

    protected ScenarioStateEntity() {
    }

    public ScenarioStateEntity(
            UUID scenarioId,
            Integer seed,
            Map<String, Object> config,
            LocalDate serviceDate,
            Instant modelAnchorAt,
            Instant realAnchorAt,
            Double speed,
            Long revision) {
        this.scenarioId = scenarioId;
        this.seed = seed;
        this.config = config;
        this.serviceDate = serviceDate;
        this.modelAnchorAt = modelAnchorAt;
        this.realAnchorAt = realAnchorAt;
        this.speed = speed;
        this.revision = revision;
    }

    public UUID getScenarioId() {
        return scenarioId;
    }

    public Integer getSeed() {
        return seed;
    }

    public Map<String, Object> getConfig() {
        return config;
    }

    public LocalDate getServiceDate() {
        return serviceDate;
    }

    public Instant getModelAnchorAt() {
        return modelAnchorAt;
    }

    public Instant getRealAnchorAt() {
        return realAnchorAt;
    }

    public Double getSpeed() {
        return speed;
    }

    public Long getRevision() {
        return revision;
    }
}

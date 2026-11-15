package com.routeroad.planning.infrastructure;

import com.routeroad.planning.domain.PlanningAlgorithm;
import jakarta.persistence.Access;
import jakarta.persistence.AccessType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "route")
@Access(AccessType.FIELD)
public class RouteEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "courier_id", nullable = false, updatable = false)
    private UUID courierId;

    @Column(name = "plan_date", nullable = false, updatable = false)
    private LocalDate planDate;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_version", referencedColumnName = "plan_version",
            nullable = false, updatable = false)
    private PlanningRunEntity planningRun;

    @Column(name = "is_active", nullable = false)
    private Boolean active;

    @Convert(converter = PlanningAlgorithmConverter.class)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "algorithm", nullable = false, length = 16, updatable = false)
    private PlanningAlgorithm algorithm;

    @Column(name = "total_distance_m", nullable = false, updatable = false)
    private Integer totalDistanceM;

    @Column(name = "violated_windows", nullable = false, updatable = false)
    private Integer violatedWindows;

    @JdbcTypeCode(SqlTypes.TIMESTAMP_WITH_TIMEZONE)
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected RouteEntity() {
    }

    public UUID getId() {
        return id;
    }

    public UUID getCourierId() {
        return courierId;
    }

    public LocalDate getPlanDate() {
        return planDate;
    }

    public PlanningRunEntity getPlanningRun() {
        return planningRun;
    }

    public Boolean getActive() {
        return active;
    }

    void setActive(boolean active) {
        this.active = active;
    }

    public PlanningAlgorithm getAlgorithm() {
        return algorithm;
    }

    public Integer getTotalDistanceM() {
        return totalDistanceM;
    }

    public Integer getViolatedWindows() {
        return violatedWindows;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

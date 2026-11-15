package com.routeroad.planning.infrastructure;

import com.routeroad.planning.domain.AssignmentDecidedBy;
import com.routeroad.planning.domain.AssignmentReasonCode;
import jakarta.persistence.Access;
import jakarta.persistence.AccessType;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "assignment")
@Access(AccessType.FIELD)
@Immutable
public class AssignmentEntity {

    @EmbeddedId
    private AssignmentId id;

    @Column(name = "courier_id", nullable = true, updatable = false)
    private UUID courierId;

    @Column(name = "route_id", nullable = true, updatable = false)
    private UUID routeId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("planVersion")
    @JoinColumn(name = "plan_version", referencedColumnName = "plan_version",
            nullable = false, updatable = false)
    private PlanningRunEntity planningRun;

    @Column(name = "source_order_version", nullable = false, updatable = false)
    private Long sourceOrderVersion;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "reason_code", nullable = false, length = 32, updatable = false)
    private AssignmentReasonCode reasonCode;

    @Column(name = "reason_text", nullable = false, columnDefinition = "text", updatable = false)
    private String reasonText;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "decided_by", nullable = false, length = 16, updatable = false)
    private AssignmentDecidedBy decidedBy;

    @JdbcTypeCode(SqlTypes.TIMESTAMP_WITH_TIMEZONE)
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AssignmentEntity() {
    }

    public AssignmentId getId() {
        return id;
    }

    public UUID getOrderId() {
        return id == null ? null : id.getOrderId();
    }

    public UUID getCourierId() {
        return courierId;
    }

    public UUID getRouteId() {
        return routeId;
    }

    public PlanningRunEntity getPlanningRun() {
        return planningRun;
    }

    public Long getSourceOrderVersion() {
        return sourceOrderVersion;
    }

    public AssignmentReasonCode getReasonCode() {
        return reasonCode;
    }

    public String getReasonText() {
        return reasonText;
    }

    public AssignmentDecidedBy getDecidedBy() {
        return decidedBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

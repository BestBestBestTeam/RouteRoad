package com.routeroad.planning.infrastructure;

import jakarta.persistence.Access;
import jakarta.persistence.AccessType;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
@Access(AccessType.FIELD)
public class AssignmentId implements Serializable {

    @Column(name = "order_id", nullable = false, updatable = false)
    private UUID orderId;

    @Column(name = "plan_version", nullable = false, updatable = false)
    private Long planVersion;

    protected AssignmentId() {
    }

    public AssignmentId(UUID orderId, Long planVersion) {
        this.orderId = Objects.requireNonNull(orderId, "orderId");
        this.planVersion = Objects.requireNonNull(planVersion, "planVersion");
    }

    public UUID getOrderId() {
        return orderId;
    }

    public Long getPlanVersion() {
        return planVersion;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof AssignmentId that)) {
            return false;
        }
        return Objects.equals(orderId, that.orderId)
                && Objects.equals(planVersion, that.planVersion);
    }

    @Override
    public int hashCode() {
        return Objects.hash(orderId, planVersion);
    }
}

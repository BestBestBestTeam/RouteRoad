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
public class RouteStopId implements Serializable {

    @Column(name = "route_id", nullable = false, updatable = false)
    private UUID routeId;

    @Column(name = "sequence", nullable = false, updatable = false)
    private Integer sequence;

    protected RouteStopId() {
    }

    public RouteStopId(UUID routeId, Integer sequence) {
        this.routeId = Objects.requireNonNull(routeId, "routeId");
        this.sequence = Objects.requireNonNull(sequence, "sequence");
    }

    public UUID getRouteId() {
        return routeId;
    }

    public Integer getSequence() {
        return sequence;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof RouteStopId that)) {
            return false;
        }
        return Objects.equals(routeId, that.routeId)
                && Objects.equals(sequence, that.sequence);
    }

    @Override
    public int hashCode() {
        return Objects.hash(routeId, sequence);
    }
}

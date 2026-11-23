package com.routeroad.planning.infrastructure;

import com.routeroad.planning.domain.RouteStopType;
import jakarta.persistence.Access;
import jakarta.persistence.AccessType;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "route_stop")
@Access(AccessType.FIELD)
@Immutable
public class RouteStopEntity {

    @EmbeddedId
    private RouteStopId id;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "type", nullable = false, length = 16, updatable = false)
    private RouteStopType type;

    @Column(name = "order_id", nullable = true, updatable = false)
    private UUID orderId;

    @Column(name = "office_id", nullable = true, updatable = false)
    private UUID officeId;

    @JdbcTypeCode(SqlTypes.TIMESTAMP_WITH_TIMEZONE)
    @Column(name = "arrival_eta", nullable = false, updatable = false)
    private Instant arrivalEta;

    @JdbcTypeCode(SqlTypes.TIMESTAMP_WITH_TIMEZONE)
    @Column(name = "departure_eta", nullable = false, updatable = false)
    private Instant departureEta;

    @Column(name = "distance_from_prev_m", nullable = false, updatable = false)
    private Integer distanceFromPrevM;

    @Column(name = "window_violation_s", nullable = false, updatable = false)
    private Integer windowViolationS;

    protected RouteStopEntity() {
    }

    public RouteStopId getId() {
        return id;
    }

    public RouteStopType getType() {
        return type;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public UUID getOfficeId() {
        return officeId;
    }

    public Instant getArrivalEta() {
        return arrivalEta;
    }

    public Instant getDepartureEta() {
        return departureEta;
    }

    public Integer getDistanceFromPrevM() {
        return distanceFromPrevM;
    }

    public Integer getWindowViolationS() {
        return windowViolationS;
    }
}

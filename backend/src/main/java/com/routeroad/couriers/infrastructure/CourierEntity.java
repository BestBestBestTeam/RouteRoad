package com.routeroad.couriers.infrastructure;

import jakarta.persistence.Access;
import jakarta.persistence.AccessType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "courier")
@Access(AccessType.FIELD)
public class CourierEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "name", nullable = false, columnDefinition = "text")
    private String name;

    @Column(name = "office_id", nullable = false)
    private UUID officeId;

    @JdbcTypeCode(SqlTypes.TIMESTAMP_WITH_TIMEZONE)
    @Column(name = "shift_start", nullable = false)
    private Instant shiftStart;

    @JdbcTypeCode(SqlTypes.TIMESTAMP_WITH_TIMEZONE)
    @Column(name = "shift_end", nullable = false)
    private Instant shiftEnd;

    @Column(name = "position_lat", nullable = true)
    private Double positionLat;

    @Column(name = "position_lon", nullable = true)
    private Double positionLon;

    @JdbcTypeCode(SqlTypes.TIMESTAMP_WITH_TIMEZONE)
    @Column(name = "position_at", nullable = true)
    private Instant positionAt;

    // Whether new assignments may be accepted; shift boundaries are separate.
    @Column(name = "is_available", nullable = false)
    private Boolean available;

    protected CourierEntity() {
    }

    public CourierEntity(UUID id, String name, UUID officeId, Instant shiftStart,
                         Instant shiftEnd, Double positionLat, Double positionLon,
                         Instant positionAt, Boolean available) {
        this.id = id;
        this.name = name;
        this.officeId = officeId;
        this.shiftStart = shiftStart;
        this.shiftEnd = shiftEnd;
        this.positionLat = positionLat;
        this.positionLon = positionLon;
        this.positionAt = positionAt;
        this.available = available;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public UUID getOfficeId() {
        return officeId;
    }

    public Instant getShiftStart() {
        return shiftStart;
    }

    public Instant getShiftEnd() {
        return shiftEnd;
    }

    public Double getPositionLat() {
        return positionLat;
    }

    public Double getPositionLon() {
        return positionLon;
    }

    public Instant getPositionAt() {
        return positionAt;
    }

    public Boolean getAvailable() {
        return available;
    }
}

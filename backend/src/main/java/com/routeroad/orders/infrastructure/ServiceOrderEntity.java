package com.routeroad.orders.infrastructure;

import com.routeroad.orders.domain.ServiceOrderStatus;
import jakarta.persistence.Access;
import jakarta.persistence.AccessType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "service_order")
@Access(AccessType.FIELD)
public class ServiceOrderEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "number", nullable = false, unique = true, columnDefinition = "text")
    private String number;

    @Column(name = "address", nullable = false, columnDefinition = "text")
    private String address;

    @Column(name = "district", nullable = true, columnDefinition = "text")
    private String district;

    @Column(name = "product", nullable = false, columnDefinition = "text")
    private String product;

    @Column(name = "client_name", nullable = false, columnDefinition = "text")
    private String clientName;

    @Column(name = "lat", nullable = false)
    private Double lat;

    @Column(name = "lon", nullable = false)
    private Double lon;

    @JdbcTypeCode(SqlTypes.TIMESTAMP_WITH_TIMEZONE)
    @Column(name = "window_from", nullable = false)
    private Instant windowFrom;

    @JdbcTypeCode(SqlTypes.TIMESTAMP_WITH_TIMEZONE)
    @Column(name = "window_to", nullable = false)
    private Instant windowTo;

    @Column(name = "service_duration_s", nullable = false)
    private Integer serviceDurationS;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", nullable = false, length = 32)
    private ServiceOrderStatus status;

    @Column(name = "service_date", nullable = false)
    private LocalDate serviceDate;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected ServiceOrderEntity() {
    }

    public ServiceOrderEntity(UUID id, String number, String clientName, String address,
                              String district, String product, Double lat, Double lon,
                              Instant windowFrom, Instant windowTo, Integer serviceDurationS,
                              ServiceOrderStatus status, LocalDate serviceDate) {
        this.id = id;
        this.number = number;
        this.address = address;
        this.district = district;
        this.product = product;
        this.clientName = clientName;
        this.lat = lat;
        this.lon = lon;
        this.windowFrom = windowFrom;
        this.windowTo = windowTo;
        this.serviceDurationS = serviceDurationS;
        this.status = status;
        this.serviceDate = serviceDate;
    }

    public UUID getId() {
        return id;
    }

    public String getNumber() {
        return number;
    }

    public String getAddress() {
        return address;
    }

    public String getDistrict() {
        return district;
    }

    public String getProduct() {
        return product;
    }

    public String getClientName() {
        return clientName;
    }

    public Double getLat() {
        return lat;
    }

    public Double getLon() {
        return lon;
    }

    public Instant getWindowFrom() {
        return windowFrom;
    }

    public Instant getWindowTo() {
        return windowTo;
    }

    public Integer getServiceDurationS() {
        return serviceDurationS;
    }

    public ServiceOrderStatus getStatus() {
        return status;
    }

    public LocalDate getServiceDate() {
        return serviceDate;
    }

    public Long getVersion() {
        return version;
    }
}

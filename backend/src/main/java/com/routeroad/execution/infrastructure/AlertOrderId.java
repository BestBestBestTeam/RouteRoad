package com.routeroad.execution.infrastructure;

import jakarta.persistence.Access;
import jakarta.persistence.AccessType;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
@Access(AccessType.FIELD)
public class AlertOrderId implements Serializable {

    @Column(name = "alert_id", nullable = false, updatable = false)
    private UUID alertId;

    @Column(name = "order_id", nullable = false, updatable = false)
    private UUID orderId;

    protected AlertOrderId() {
    }

    public AlertOrderId(UUID alertId, UUID orderId) {
        this.alertId = Objects.requireNonNull(alertId, "alertId");
        this.orderId = Objects.requireNonNull(orderId, "orderId");
    }

    public UUID getAlertId() {
        return alertId;
    }

    public UUID getOrderId() {
        return orderId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof AlertOrderId that)) {
            return false;
        }
        return Objects.equals(alertId, that.alertId)
                && Objects.equals(orderId, that.orderId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(alertId, orderId);
    }
}

package com.routeroad.couriers.infrastructure;

import java.time.Instant;
import java.util.UUID;

public interface CourierListRow {
    UUID getId();
    String getName();
    UUID getOfficeId();
    Instant getShiftStart();
    Instant getShiftEnd();
    Double getPositionLat();
    Double getPositionLon();
    Instant getPositionAt();
    boolean getAvailable();
    long getTotalOrders();
    long getCompletedOrders();
    long getOpenAlertCount();
}

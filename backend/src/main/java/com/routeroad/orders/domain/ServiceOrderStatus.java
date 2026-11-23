package com.routeroad.orders.domain;

public enum ServiceOrderStatus {
    NEW,
    ASSIGNED,
    UNASSIGNED,
    EN_ROUTE,
    ARRIVED,
    IN_PROGRESS,
    COMPLETED,
    RESCHEDULED,
    CANCELLED
}

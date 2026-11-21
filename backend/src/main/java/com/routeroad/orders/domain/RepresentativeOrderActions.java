package com.routeroad.orders.domain;

import java.util.List;

public final class RepresentativeOrderActions {
    private RepresentativeOrderActions() {}

    public static List<String> forOrder(ServiceOrderStatus status, boolean currentDay,
                                         boolean orderRefused, boolean dayRefused) {
        if (!currentDay || orderRefused) return List.of();
        if (dayRefused && status != ServiceOrderStatus.IN_PROGRESS) return List.of();
        return switch (status) {
            case ASSIGNED -> List.of("EN_ROUTE", "REPORT_DELAY", "REFUSE_ORDER");
            case EN_ROUTE -> List.of("ARRIVED", "REPORT_DELAY", "REFUSE_ORDER");
            case ARRIVED -> List.of("IN_PROGRESS", "REPORT_DELAY", "REFUSE_ORDER");
            case IN_PROGRESS -> List.of("COMPLETED", "REPORT_DELAY");
            default -> List.of();
        };
    }
}

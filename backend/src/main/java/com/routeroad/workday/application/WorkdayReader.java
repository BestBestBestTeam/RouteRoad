package com.routeroad.workday.application;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface WorkdayReader {
    Counts count(LocalDate date);
    List<Office> findOffices();

    record Counts(long ordersTotal, long ordersAssigned, long ordersCompleted,
                  long couriersTotal, long openAlerts, boolean hasRoutes) {}
    record Office(UUID id, String name, String address, double lat, double lon) {}
}

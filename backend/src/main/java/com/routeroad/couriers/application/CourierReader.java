package com.routeroad.couriers.application;

import com.routeroad.couriers.api.CourierView;
import java.time.LocalDate;
import java.util.List;

public interface CourierReader {
    record Filter(LocalDate date, String search, Boolean available, Boolean hasOpenAlerts, Boolean hasWorkload) {}
    record Result(List<CourierView> items, long total) {}
    Result find(Filter filter, int pageSize, long offset);
}

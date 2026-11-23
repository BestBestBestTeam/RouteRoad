package com.routeroad.workday.application;

import com.routeroad.offices.api.OfficeView;
import com.routeroad.workday.api.MapView.*;
import java.time.LocalDate;
import java.util.List;

public interface WorkdayMapReader {
    record Result(List<MapOrder> orders, List<MapCourier> couriers, List<OfficeView> offices,
                  List<MapRoute> routes, List<MapAlert> alerts) {}
    Result read(LocalDate date);
}

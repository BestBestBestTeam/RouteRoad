package com.routeroad.couriers.api;

import com.routeroad.offices.api.OfficeView;
import com.routeroad.orders.api.OrderView;
import com.routeroad.planning.api.RouteView;
import com.routeroad.shared.api.Clock;
import java.time.LocalDate;
import java.util.List;

public record ScheduleView(LocalDate date, CourierView courier, RouteView route,
                           List<OrderView> orders, List<OfficeView> offices, Clock clock) {}

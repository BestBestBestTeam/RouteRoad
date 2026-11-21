package com.routeroad.couriers.application;

import com.routeroad.offices.api.OfficeView;
import com.routeroad.orders.api.OrderView;
import com.routeroad.planning.api.RouteView;
import com.routeroad.shared.api.Position;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CourierScheduleReader {
    record Courier(UUID id, String name, UUID officeId, Instant shiftStart, Instant shiftEnd,
                   Position position, boolean available) {}
    record Details(RouteView route, List<OfficeView> offices, int openAlertCount, boolean dayRefused) {}
    Optional<Courier> findCourier(UUID id);
    Details details(Courier courier, LocalDate date, List<OrderView> orders);
}

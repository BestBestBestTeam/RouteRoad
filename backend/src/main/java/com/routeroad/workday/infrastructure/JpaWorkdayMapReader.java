package com.routeroad.workday.infrastructure;

import com.routeroad.couriers.application.InvalidScheduleException;
import com.routeroad.couriers.infrastructure.CourierRepository;
import com.routeroad.execution.infrastructure.AlertRepository;
import com.routeroad.execution.infrastructure.OpenAlertOrderRow;
import com.routeroad.orders.api.OrderView;
import com.routeroad.orders.application.OrderReader;
import com.routeroad.planning.infrastructure.RouteRepository;
import com.routeroad.planning.infrastructure.RouteStopEntity;
import com.routeroad.planning.infrastructure.RouteStopRepository;
import com.routeroad.planning.infrastructure.RouteViewAssembler;
import com.routeroad.shared.api.Position;
import com.routeroad.workday.api.MapView.*;
import com.routeroad.workday.application.WorkdayMapReader;
import com.routeroad.workday.application.WorkdayReader;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class JpaWorkdayMapReader implements WorkdayMapReader {
    private final OrderReader orders;
    private final CourierRepository couriers;
    private final WorkdayReader workday;
    private final RouteRepository routes;
    private final RouteStopRepository stops;
    private final AlertRepository alerts;
    private final RouteViewAssembler assembler;

    public JpaWorkdayMapReader(OrderReader orders, CourierRepository couriers, WorkdayReader workday,
                               RouteRepository routes, RouteStopRepository stops, AlertRepository alerts,
                               RouteViewAssembler assembler) {
        this.orders = orders;
        this.couriers = couriers;
        this.workday = workday;
        this.routes = routes;
        this.stops = stops;
        this.alerts = alerts;
        this.assembler = assembler;
    }

    @Override public Result read(LocalDate date) {
        var dayOrders = orders.findDay(date);
        var mapOrders = dayOrders.stream().map(o -> new MapOrder(o.id(), o.number(), o.address(), o.clientName(),
                o.lat(), o.lon(), o.dayStatus())).toList();
        var mapCouriers = couriers.findAll(Sort.by("name", "id")).stream().map(c -> new MapCourier(c.getId(), c.getName(),
                Position.fromNullable(c.getPositionLat(), c.getPositionLon(), c.getPositionAt()))).toList();
        var offices = workday.findOffices().stream().map(WorkdayReader.Office::view).toList();
        var officeById = offices.stream().collect(Collectors.toMap(o -> o.id(), Function.identity()));
        var courierIds = mapCouriers.stream().map(MapCourier::id).collect(Collectors.toSet());
        var activeRoutes = routes.findAllConfirmedActive(date);
        if (activeRoutes.stream().anyMatch(r -> !courierIds.contains(r.getCourierId()))) throw new InvalidScheduleException();
        var routeStops = activeRoutes.isEmpty() ? List.<RouteStopEntity>of()
                : stops.findById_RouteIdInOrderById_RouteIdAscId_SequenceAsc(activeRoutes.stream().map(r -> r.getId()).toList());
        var stopsByRoute = routeStops.stream().collect(Collectors.groupingBy(s -> s.getId().getRouteId()));
        // Historical-day orders remain map markers, but cannot become current route visits.
        var ordersByCourier = dayOrders.stream().filter(o -> o.serviceDate().equals(date)
                && o.assignment() != null && o.assignment().courierId() != null)
                .collect(Collectors.groupingBy(o -> o.assignment().courierId()));
        var mapRoutes = activeRoutes.stream().map(r -> assembler.assemble(r,
                        stopsByRoute.getOrDefault(r.getId(), List.of()),
                        ordersByCourier.getOrDefault(r.getCourierId(), List.<OrderView>of()), officeById))
                .map(r -> new MapRoute(r.id(), r.courierId(), r.planVersion(), r.stops(), r.geometry(), r.geometryKind())).toList();
        var linksByAlert = alerts.findOpenOrderLinks(date).stream().collect(Collectors.groupingBy(OpenAlertOrderRow::getId,
                LinkedHashMap::new, Collectors.toList()));
        var mapAlerts = linksByAlert.values().stream().map(links -> new MapAlert(links.getFirst().getId(),
                links.stream().map(OpenAlertOrderRow::getOrderId).filter(Objects::nonNull).toList(),
                links.getFirst().getCourierId())).toList();
        return new Result(mapOrders, mapCouriers, offices, mapRoutes, mapAlerts);
    }
}

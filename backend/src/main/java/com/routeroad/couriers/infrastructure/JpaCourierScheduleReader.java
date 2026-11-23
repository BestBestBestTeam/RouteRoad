package com.routeroad.couriers.infrastructure;

import com.routeroad.couriers.application.CourierScheduleReader;
import com.routeroad.couriers.application.InvalidScheduleException;
import com.routeroad.execution.domain.AlertStatus;
import com.routeroad.execution.domain.BusinessEventType;
import com.routeroad.execution.infrastructure.AlertRepository;
import com.routeroad.execution.infrastructure.BusinessEventRepository;
import com.routeroad.offices.api.OfficeView;
import com.routeroad.offices.infrastructure.OfficeRepository;
import com.routeroad.orders.api.OrderView;
import com.routeroad.planning.infrastructure.RouteRepository;
import com.routeroad.planning.infrastructure.RouteViewAssembler;
import com.routeroad.planning.infrastructure.RouteStopRepository;
import com.routeroad.shared.api.Position;
import org.springframework.stereotype.Component;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class JpaCourierScheduleReader implements CourierScheduleReader {
    private final CourierRepository couriers;
    private final RouteRepository routes;
    private final RouteStopRepository stops;
    private final OfficeRepository offices;
    private final AlertRepository alerts;
    private final BusinessEventRepository events;
    private final RouteViewAssembler assembler;

    public JpaCourierScheduleReader(CourierRepository couriers, RouteRepository routes, RouteStopRepository stops,
                                    OfficeRepository offices, AlertRepository alerts, BusinessEventRepository events,
                                    RouteViewAssembler assembler) {
        this.couriers = couriers;
        this.routes = routes;
        this.stops = stops;
        this.offices = offices;
        this.alerts = alerts;
        this.events = events;
        this.assembler = assembler;
    }

    @Override public Optional<Courier> findCourier(UUID id) {
        return couriers.findById(id).map(c -> new Courier(c.getId(), c.getName(), c.getOfficeId(),
                c.getShiftStart(), c.getShiftEnd(), Position.fromNullable(c.getPositionLat(), c.getPositionLon(), c.getPositionAt()), c.getAvailable()));
    }

    @Override public Details details(Courier courier, LocalDate date, List<OrderView> orders) {
        var route = routes.findConfirmedActive(courier.id(), date);
        var routeStops = route.map(r -> stops.findById_RouteIdOrderById_SequenceAsc(r.getId()))
                .orElseGet(List::of);
        var officeIds = new HashSet<UUID>();
        officeIds.add(courier.officeId());
        routeStops.stream().map(s -> s.getOfficeId()).filter(Objects::nonNull).forEach(officeIds::add);
        var officeViews = offices.findAllById(officeIds).stream()
                .map(o -> new OfficeView(o.getId(), o.getName(), o.getAddress(), o.getLat(), o.getLon()))
                .sorted(Comparator.comparing(OfficeView::name).thenComparing(OfficeView::id)).toList();
        var officeById = officeViews.stream().collect(Collectors.toMap(OfficeView::id, Function.identity()));
        if (!officeById.keySet().containsAll(officeIds)) throw new InvalidScheduleException();
        var view = route.map(r -> assembler.assemble(r, routeStops, orders, officeById)).orElse(null);
        return new Details(view, officeViews,
                Math.toIntExact(alerts.countByCourierIdAndServiceDateAndStatus(courier.id(), date, AlertStatus.OPEN)),
                events.existsByTypeAndCourierIdAndServiceDate(BusinessEventType.COURIER_REFUSED, courier.id(), date));
    }
}

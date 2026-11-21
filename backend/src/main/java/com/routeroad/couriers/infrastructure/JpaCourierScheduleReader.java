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
import com.routeroad.orders.domain.ServiceOrderStatus;
import com.routeroad.planning.api.RouteStop;
import com.routeroad.planning.api.RouteView;
import com.routeroad.planning.domain.RouteStopType;
import com.routeroad.planning.infrastructure.RouteRepository;
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

    public JpaCourierScheduleReader(CourierRepository couriers, RouteRepository routes, RouteStopRepository stops,
                                    OfficeRepository offices, AlertRepository alerts, BusinessEventRepository events) {
        this.couriers = couriers;
        this.routes = routes;
        this.stops = stops;
        this.offices = offices;
        this.alerts = alerts;
        this.events = events;
    }

    @Override public Optional<Courier> findCourier(UUID id) {
        return couriers.findById(id).map(c -> new Courier(c.getId(), c.getName(), c.getOfficeId(),
                c.getShiftStart(), c.getShiftEnd(), c.getPositionAt() == null ? null
                    : new Position(c.getPositionLat(), c.getPositionLon(), c.getPositionAt()), c.getAvailable()));
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
        var orderById = orders.stream().collect(Collectors.toMap(OrderView::id, Function.identity()));
        var stopOrderIds = new HashSet<UUID>();
        var coordinates = new ArrayList<List<Double>>();
        for (var stop : routeStops) {
            if (stop.getType() == RouteStopType.OFFICE) {
                var office = officeById.get(stop.getOfficeId());
                coordinates.add(List.of(office.lon(), office.lat()));
            } else {
                // Never load a foreign order to fill a stale stop or its geometry.
                var order = orderById.get(stop.getOrderId());
                if (order == null || !stopOrderIds.add(order.id())) throw new InvalidScheduleException();
                coordinates.add(List.of(order.lon(), order.lat()));
            }
        }
        if (route.isPresent()) {
            if (!routeStops.isEmpty() && (routeStops.getFirst().getType() != RouteStopType.OFFICE
                    || routeStops.getFirst().getId().getSequence() != 1
                    || routeStops.getFirst().getDistanceFromPrevM() != 0)) {
                throw new InvalidScheduleException();
            }
            // A rebuild can omit completed/cancelled visits; live assignments must still have stops.
            boolean missingLiveStop = orders.stream().anyMatch(o -> o.status() != ServiceOrderStatus.COMPLETED
                    && o.status() != ServiceOrderStatus.CANCELLED && !stopOrderIds.contains(o.id()));
            if (missingLiveStop) throw new InvalidScheduleException();
        }
        var view = route.map(r -> new RouteView(r.getId(), r.getCourierId(), r.getPlanDate(),
                r.getPlanningRun().getPlanVersion(), r.getActive(), r.getAlgorithm().name().toLowerCase(Locale.ROOT),
                r.getTotalDistanceM(), r.getViolatedWindows(), r.getCreatedAt(),
                routeStops.stream().map(s -> new RouteStop(s.getId().getRouteId(), s.getId().getSequence(), s.getType(),
                        s.getOrderId(), s.getOfficeId(), s.getArrivalEta(), s.getDepartureEta(),
                        s.getDistanceFromPrevM(), s.getWindowViolationS())).toList(),
                new RouteView.Geometry("LineString", List.copyOf(coordinates)), "straight")).orElse(null);
        return new Details(view, officeViews,
                Math.toIntExact(alerts.countByCourierIdAndServiceDateAndStatus(courier.id(), date, AlertStatus.OPEN)),
                events.existsByTypeAndCourierIdAndServiceDate(BusinessEventType.COURIER_REFUSED, courier.id(), date));
    }
}

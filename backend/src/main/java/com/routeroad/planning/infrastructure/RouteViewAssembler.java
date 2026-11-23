package com.routeroad.planning.infrastructure;

import com.routeroad.couriers.application.InvalidScheduleException;
import com.routeroad.offices.api.OfficeView;
import com.routeroad.orders.api.OrderView;
import com.routeroad.orders.domain.ServiceOrderStatus;
import com.routeroad.planning.api.RouteStop;
import com.routeroad.planning.api.RouteView;
import com.routeroad.planning.domain.RouteStopType;
import org.springframework.stereotype.Component;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/** One route validation and geometry implementation shared by schedules and map projections. */
@Component
public class RouteViewAssembler {
    public RouteView assemble(RouteEntity route, List<RouteStopEntity> stops, List<OrderView> orders,
                              Map<UUID, OfficeView> offices) {
        var orderById = orders.stream().collect(Collectors.toMap(OrderView::id, Function.identity()));
        var stopOrderIds = new HashSet<UUID>();
        var coordinates = new ArrayList<List<Double>>();
        for (var stop : stops) {
            if (stop.getType() == RouteStopType.OFFICE) {
                var office = offices.get(stop.getOfficeId());
                if (office == null) throw new InvalidScheduleException();
                addCoordinate(coordinates, office.lon(), office.lat());
            } else {
                var order = orderById.get(stop.getOrderId());
                if (order == null || !stopOrderIds.add(order.id())) throw new InvalidScheduleException();
                addCoordinate(coordinates, order.lon(), order.lat());
            }
        }
        if (!stops.isEmpty() && (stops.getFirst().getType() != RouteStopType.OFFICE
                || stops.getFirst().getId().getSequence() != 1 || stops.getFirst().getDistanceFromPrevM() != 0)) {
            throw new InvalidScheduleException();
        }
        boolean missingLiveStop = orders.stream().anyMatch(o -> o.status() != ServiceOrderStatus.COMPLETED
                && o.status() != ServiceOrderStatus.CANCELLED && !stopOrderIds.contains(o.id()));
        if (missingLiveStop) throw new InvalidScheduleException();
        return new RouteView(route.getId(), route.getCourierId(), route.getPlanDate(),
                route.getPlanningRun().getPlanVersion(), route.getActive(), route.getAlgorithm().name().toLowerCase(Locale.ROOT),
                route.getTotalDistanceM(), route.getViolatedWindows(), route.getCreatedAt(),
                stops.stream().map(s -> new RouteStop(s.getId().getRouteId(), s.getId().getSequence(), s.getType(),
                        s.getOrderId(), s.getOfficeId(), s.getArrivalEta(), s.getDepartureEta(),
                        s.getDistanceFromPrevM(), s.getWindowViolationS())).toList(),
                coordinates.size() < 2 ? null : new RouteView.Geometry("LineString", List.copyOf(coordinates)), "straight");
    }
    private static void addCoordinate(List<List<Double>> coordinates, Double lon, Double lat) {
        if (lon != null && lat != null && Double.isFinite(lon) && Double.isFinite(lat)
                && lon >= -180 && lon <= 180 && lat >= -90 && lat <= 90) {
            coordinates.add(List.of(lon, lat));
        }
    }
}

package com.routeroad.workday.api;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.routeroad.offices.api.OfficeView;
import com.routeroad.orders.domain.ServiceOrderStatus;
import com.routeroad.planning.api.RouteStop;
import com.routeroad.planning.api.RouteView.Geometry;
import com.routeroad.shared.api.Clock;
import com.routeroad.shared.api.Position;
import org.jspecify.annotations.Nullable;
import java.util.List;
import java.util.UUID;

public record MapView(List<MapOrder> orders, List<MapCourier> couriers, List<OfficeView> offices,
                      List<MapRoute> routes, List<MapAlert> alerts, Clock clock) {
    public record MapOrder(UUID id, String number, String address, @JsonProperty("client_name") String clientName,
                           double lat, double lon, @JsonProperty("day_status") ServiceOrderStatus dayStatus) {}
    public record MapCourier(UUID id, String name, Position position) {}
    public record MapRoute(UUID id, @JsonProperty("courier_id") UUID courierId,
                           @JsonProperty("plan_version") @JsonFormat(shape = JsonFormat.Shape.STRING) Long planVersion,
                           List<RouteStop> stops, @Nullable Geometry geometry,
                           @JsonProperty("geometry_kind") String geometryKind) {}
    public record MapAlert(UUID id, @JsonProperty("order_ids") List<UUID> orderIds,
                           @JsonProperty("courier_id") UUID courierId) {}
}

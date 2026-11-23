package com.routeroad.planning.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.routeroad.planning.domain.RouteStopType;
import java.time.Instant;
import java.util.UUID;

public record RouteStop(@JsonProperty("route_id") UUID routeId, int sequence, RouteStopType type,
        @JsonProperty("order_id") UUID orderId, @JsonProperty("office_id") UUID officeId,
        @JsonProperty("arrival_eta") Instant arrivalEta, @JsonProperty("departure_eta") Instant departureEta,
        @JsonProperty("distance_from_prev_m") int distanceFromPrevM,
        @JsonProperty("window_violation_s") int windowViolationS) {}

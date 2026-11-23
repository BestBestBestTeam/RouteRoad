package com.routeroad.planning.api;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record RouteView(UUID id, @JsonProperty("courier_id") UUID courierId,
        @JsonProperty("plan_date") LocalDate planDate,
        @JsonProperty("plan_version") @JsonFormat(shape = JsonFormat.Shape.STRING) Long planVersion,
        @JsonProperty("is_active") boolean active, String algorithm,
        @JsonProperty("total_distance_m") int totalDistanceM,
        @JsonProperty("violated_windows") int violatedWindows,
        @JsonProperty("created_at") Instant createdAt, List<RouteStop> stops,
        @Nullable Geometry geometry, @JsonProperty("geometry_kind") String geometryKind) {
    public record Geometry(String type, List<List<Double>> coordinates) {}
}

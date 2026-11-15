package com.routeroad.planning.infrastructure;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PlanningMetrics(
        @JsonProperty("total_distance_m") int totalDistanceM,
        @JsonProperty("violated_windows") int violatedWindows,
        @JsonProperty("unassigned_count") int unassignedCount,
        @JsonProperty("solve_time_ms") int solveTimeMs) {
}

package com.routeroad.couriers.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.routeroad.shared.api.Position;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CourierView(UUID id, String name, @JsonProperty("office_id") UUID officeId,
        @JsonProperty("shift_start") Instant shiftStart, @JsonProperty("shift_end") Instant shiftEnd,
        Position position, @JsonProperty("is_available") boolean available,
        @JsonProperty("total_orders") int totalOrders, @JsonProperty("completed_orders") int completedOrders,
        @JsonProperty("open_alert_count") int openAlertCount,
        @JsonProperty("available_actions") List<String> availableActions) {}

package com.routeroad.execution.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.routeroad.shared.api.Resolution;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record AlertView(UUID id, @JsonProperty("source_event_id") UUID sourceEventId,
        @JsonProperty("service_date") LocalDate serviceDate, @JsonProperty("courier_id") UUID courierId,
        String kind, String scope, String severity, String status,
        @JsonProperty("reason_code") String reasonCode, @JsonProperty("reason_text") String reasonText,
        @JsonProperty("created_at") Instant createdAt, @JsonProperty("deadline_at") Instant deadlineAt,
        @JsonProperty("resolved_at") Instant resolvedAt, @JsonProperty("resolved_by") UUID resolvedBy,
        @JsonProperty("resolution_reason") String resolutionReason, @JsonProperty("order_ids") List<UUID> orderIds,
        List<OrderEntry> orders, String title, List<String> impact, Resolution resolution,
        @JsonProperty("available_actions") List<String> availableActions) {
    public record OrderEntry(@JsonProperty("order_id") UUID orderId, String status, Resolution resolution) {}
}

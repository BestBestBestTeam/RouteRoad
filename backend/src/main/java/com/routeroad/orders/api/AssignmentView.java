package com.routeroad.orders.api;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.routeroad.planning.domain.AssignmentDecidedBy;
import com.routeroad.planning.domain.AssignmentReasonCode;
import com.routeroad.shared.api.Actor;
import java.time.Instant;
import java.util.UUID;

public record AssignmentView(@JsonProperty("order_id") UUID orderId,
        @JsonProperty("courier_id") UUID courierId, @JsonProperty("route_id") UUID routeId,
        @JsonProperty("plan_version") @JsonFormat(shape = JsonFormat.Shape.STRING) Long planVersion,
        @JsonProperty("source_order_version") @JsonFormat(shape = JsonFormat.Shape.STRING) Long sourceOrderVersion,
        @JsonProperty("reason_code") AssignmentReasonCode reasonCode, @JsonProperty("reason_text") String reasonText,
        @JsonProperty("decided_by") AssignmentDecidedBy decidedBy, @JsonProperty("created_at") Instant createdAt,
        @JsonProperty("courier_name") String courierName, Actor actor) {}

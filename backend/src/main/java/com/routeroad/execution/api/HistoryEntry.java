package com.routeroad.execution.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.routeroad.shared.api.Actor;
import com.routeroad.shared.api.Change;
import org.jspecify.annotations.Nullable;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record HistoryEntry(UUID id, String type, @JsonProperty("created_at") Instant createdAt, Actor actor,
        @JsonProperty("order_id") @Nullable UUID orderId, @JsonProperty("alert_id") @Nullable UUID alertId,
        String title, String description, String reason, List<Change> changes) {}

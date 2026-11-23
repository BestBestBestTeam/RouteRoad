package com.routeroad.execution.application;

import java.time.Instant;
import com.routeroad.execution.api.AlertView;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface OrderAlerts {
    /** Null courier means dispatcher; otherwise only single-order alerts for that courier are visible. */
    List<AlertView> find(UUID orderId, LocalDate date, UUID courierId, boolean allowDispatcherActions, Instant modelNow);
}

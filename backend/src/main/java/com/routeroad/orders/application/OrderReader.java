package com.routeroad.orders.application;

import com.routeroad.orders.domain.ServiceOrderStatus;
import com.routeroad.orders.api.OrderView;
import java.time.LocalDate;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.Optional;

public interface OrderReader {
    record Filter(LocalDate date, String search, ServiceOrderStatus status, UUID courierId, Boolean assigned) {}
    record Result(List<OrderView> items, long total) {}
    Result find(Filter filter, int pageSize, long offset, boolean allowActions, Instant modelNow);
    List<OrderView> findDay(LocalDate date);
    Optional<LocalDate> currentDate(UUID id);
    Optional<OrderView> findOne(UUID id, LocalDate date, boolean allowDispatcherActions, Instant modelNow);
    record ScheduledOrder(OrderView order, ActionContext context) {}
    List<ScheduledOrder> findSchedule(UUID courierId, LocalDate date, boolean allowDispatcherActions, Instant modelNow);
    record ActionContext(boolean orderRefused, boolean dayRefused, boolean awaitingDecision) {}
    ActionContext actionContext(UUID orderId, UUID courierId, LocalDate date);
}

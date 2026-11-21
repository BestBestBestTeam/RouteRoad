package com.routeroad.orders.application;

import com.routeroad.orders.domain.ServiceOrderStatus;
import com.routeroad.orders.api.OrderView;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.Optional;

public interface OrderReader {
    record Filter(LocalDate date, String search, ServiceOrderStatus status, UUID courierId, Boolean assigned) {}
    record Result(List<OrderView> items, long total) {}
    Result find(Filter filter, int pageSize, long offset, boolean allowActions);
    Optional<LocalDate> currentDate(UUID id);
    Optional<OrderView> findOne(UUID id, LocalDate date, boolean allowDispatcherActions);
    record ScheduledOrder(OrderView order, Refusals refusals) {}
    List<ScheduledOrder> findSchedule(UUID courierId, LocalDate date);
    record Refusals(boolean orderRefused, boolean dayRefused) {}
    Refusals refusals(UUID orderId, UUID courierId, LocalDate date);
}

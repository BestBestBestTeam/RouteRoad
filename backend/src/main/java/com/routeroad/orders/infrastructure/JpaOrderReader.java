package com.routeroad.orders.infrastructure;

import com.routeroad.orders.api.AssignmentView;
import com.routeroad.orders.api.OrderView;
import com.routeroad.orders.application.OrderReader;
import com.routeroad.orders.domain.ServiceOrderStatus;
import com.routeroad.planning.domain.AssignmentDecidedBy;
import com.routeroad.planning.domain.AssignmentReasonCode;
import com.routeroad.shared.api.Actor;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.time.LocalDate;

@Component
public class JpaOrderReader implements OrderReader {
    private final ServiceOrderRepository orders;
    public JpaOrderReader(ServiceOrderRepository orders) { this.orders = orders; }
    @Override public Result find(Filter filter, int pageSize, long offset, boolean allowActions) {
        String status = filter.status() == null ? null : filter.status().name();
        long total = orders.countOrderList(filter.date(), filter.search(), status, filter.courierId(), filter.assigned());
        var rows = orders.findOrderList(filter.date(), filter.search(), status, filter.courierId(), filter.assigned(), pageSize, offset);
        return new Result(rows.stream().map(row -> view(row, allowActions)).toList(), total);
    }

    @Override public Optional<LocalDate> currentDate(UUID id) { return orders.findCurrentServiceDate(id); }

    @Override public Optional<OrderView> findOne(UUID id, LocalDate date, boolean allowDispatcherActions) {
        return orders.findOrderDetail(id, date).map(row -> view(row, allowDispatcherActions));
    }

    @Override public Refusals refusals(UUID orderId, UUID courierId, LocalDate date) {
        var state = orders.findRefusals(orderId, courierId, date);
        return new Refusals(state.getOrderRefused(), state.getDayRefused());
    }

    @Override public List<ScheduledOrder> findSchedule(UUID courierId, LocalDate date) {
        return orders.findSchedule(courierId, date).stream()
                .map(row -> new ScheduledOrder(view(row, false),
                        new Refusals(row.getOrderRefused(), row.getDayRefused()))).toList();
    }

    private OrderView view(OrderListRow row, boolean allowActions) {
        AssignmentView assignment = null;
        if (row.getPlanVersion() != null) {
            var actor = row.getActorId() == null ? new Actor(null, "Система", Actor.Role.SYSTEM)
                    : new Actor(row.getActorId(), row.getActorName(), Actor.Role.valueOf(row.getActorRole().toUpperCase(Locale.ROOT)));
            assignment = new AssignmentView(row.getId(), row.getCourierId(), row.getRouteId(), row.getPlanVersion(),
                    row.getSourceOrderVersion(), AssignmentReasonCode.valueOf(row.getReasonCode()), row.getReasonText(),
                    AssignmentDecidedBy.valueOf(row.getDecidedBy()), row.getDecisionCreatedAt(), row.getCourierName(), actor);
        }
        
        return new OrderView(row.getId(), row.getNumber(), row.getClientName(), row.getAddress(), row.getDistrict(),
                row.getProduct(), row.getLat(), row.getLon(), row.getWindowFrom(), row.getWindowTo(), row.getServiceDurationS(),
                ServiceOrderStatus.valueOf(row.getStatus()), row.getServiceDate(), row.getVersion(),
                ServiceOrderStatus.valueOf(row.getDayStatus()), assignment,
                allowActions && row.getCanReassign() ? List.of("REASSIGN") : List.of());
    }
}

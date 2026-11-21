package com.routeroad.orders.application;

import com.routeroad.orders.api.OrderView;
import com.routeroad.orders.api.OrderDetailView;
import com.routeroad.auth.application.AccountService;
import com.routeroad.execution.application.OrderAlerts;
import com.routeroad.orders.domain.RepresentativeOrderActions;
import com.routeroad.shared.domain.AppUserRole;
import org.springframework.security.access.AccessDeniedException;
import com.routeroad.orders.domain.ServiceOrderStatus;
import com.routeroad.shared.api.Page;
import com.routeroad.simulation.application.ScenarioClockQuery;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.UUID;

@Service
public class OrderQueryService {
    private final OrderReader reader;
    private final ScenarioClockQuery scenario;
    private final AccountService accounts;
    private final OrderAlerts alerts;
    public OrderQueryService(OrderReader reader, ScenarioClockQuery scenario, AccountService accounts, OrderAlerts alerts) {
        this.reader = reader;
        this.scenario = scenario;
        this.accounts = accounts;
        this.alerts = alerts;
    }
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Page<OrderView> get(LocalDate date, String search, ServiceOrderStatus status, UUID courierId,
                               Boolean assigned, int page, int pageSize) {
        if (page < 1 || pageSize < 1 || pageSize > 100) throw new IllegalArgumentException("Invalid pagination");
        var context = scenario.get(date).orElseThrow(com.routeroad.simulation.application.ScenarioNotInitializedException::new);
        String pattern = com.routeroad.shared.application.TextSearch.contains(search);
        var result = reader.find(new OrderReader.Filter(context.date(), pattern, status, courierId, assigned),
                pageSize, ((long) page - 1) * pageSize, context.date().equals(context.scenarioDate()));
        return new Page<>(result.items(), Math.toIntExact(result.total()), page, pageSize, context.clock());
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public OrderDetailView detail(UUID id, LocalDate date, String sessionLogin) {
        var viewer = accounts.current(sessionLogin);
        var context = scenario.get(date).orElseThrow(com.routeroad.simulation.application.ScenarioNotInitializedException::new);
        var currentDate = reader.currentDate(id).orElseThrow(OrderNotFoundException::new);
        boolean dispatcher = viewer.role() == AppUserRole.DISPATCHER;
        OrderView order;
        if (dispatcher) {
            order = reader.findOne(id, context.date(), context.date().equals(context.scenarioDate()))
                    .orElseThrow(OrderNotFoundException::new);
        } else {
            
            order = reader.findOne(id, currentDate, false).orElseThrow(OrderNotFoundException::new);
            if (viewer.courierId() == null || order.assignment() == null
                    || !viewer.courierId().equals(order.assignment().courierId())
                    || !context.date().equals(currentDate)) {
                throw new AccessDeniedException("Access denied");
            }
            var refusals = reader.refusals(id, viewer.courierId(), currentDate);
            order = order.withActions(RepresentativeOrderActions.forOrder(order.status(),
                    currentDate.equals(context.scenarioDate()), refusals.orderRefused(), refusals.dayRefused()));
        }
        
        return new OrderDetailView(order, alerts.find(id, context.date(), dispatcher ? null : viewer.courierId(),
                dispatcher && context.date().equals(context.scenarioDate())), context.clock());
    }
}

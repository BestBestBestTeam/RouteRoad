package com.routeroad.couriers.application;

import com.routeroad.auth.application.AccountService;
import com.routeroad.couriers.api.CourierView;
import com.routeroad.couriers.api.ScheduleView;
import com.routeroad.orders.application.OrderReader;
import com.routeroad.orders.domain.RepresentativeOrderActions;
import com.routeroad.orders.domain.ServiceOrderStatus;
import com.routeroad.shared.domain.AppUserRole;
import com.routeroad.simulation.application.ScenarioClockQuery;
import com.routeroad.simulation.application.ScenarioNotInitializedException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class CourierScheduleService {
    private final CourierScheduleReader reader;
    private final OrderReader orders;
    private final ScenarioClockQuery scenario;
    private final AccountService accounts;

    public CourierScheduleService(CourierScheduleReader reader, OrderReader orders,
                                  ScenarioClockQuery scenario, AccountService accounts) {
        this.reader = reader;
        this.orders = orders;
        this.scenario = scenario;
        this.accounts = accounts;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ScheduleView get(UUID id, LocalDate date, String sessionLogin) {
        var viewer = accounts.current(sessionLogin);
        boolean representative = viewer.role() == AppUserRole.REPRESENTATIVE;
        if (representative && (viewer.courierId() == null || !viewer.courierId().equals(id))) {
            throw new AccessDeniedException("Access denied");
        }
        var courier = reader.findCourier(id).orElseThrow(CourierNotFoundException::new);
        var context = scenario.get(date).orElseThrow(ScenarioNotInitializedException::new);
        var scheduled = orders.findSchedule(id, context.date());
        boolean currentDay = context.date().equals(context.scenarioDate());
        var views = scheduled.stream().map(item -> item.order().withActions(representative
                ? RepresentativeOrderActions.forOrder(item.order().status(), currentDay,
                        item.refusals().orderRefused(), item.refusals().dayRefused())
                : List.of())).toList();
        var details = reader.details(courier, context.date(), views);
        var courierView = new CourierView(courier.id(), courier.name(), courier.officeId(),
                courier.shiftStart(), courier.shiftEnd(), courier.position(), courier.available(), views.size(),
                (int) views.stream().filter(o -> o.dayStatus() == ServiceOrderStatus.COMPLETED).count(),
                details.openAlertCount(), representative && currentDay && courier.available() && !details.dayRefused()
                        ? List.of("REFUSE_WORKDAY") : List.of());
        return new ScheduleView(context.date(), courierView, details.route(), views, details.offices(), context.clock());
    }
}

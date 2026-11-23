package com.routeroad.execution.infrastructure;

import java.time.Instant;
import com.routeroad.execution.api.AlertView;
import com.routeroad.execution.application.OrderAlerts;
import org.springframework.stereotype.Component;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Component
public class JpaOrderAlerts implements OrderAlerts {
    private final AlertRepository alerts;
    private final AlertViewAssembler assembler;
    public JpaOrderAlerts(AlertRepository alerts, AlertViewAssembler assembler) {
        this.alerts = alerts;
        this.assembler = assembler;
    }
    @Override public List<AlertView> find(UUID orderId, LocalDate date, UUID courierId, boolean allowDispatcherActions, Instant modelNow) {
        return assembler.assemble(alerts.findForOrder(orderId,date,courierId),courierId == null && allowDispatcherActions,modelNow);
    }
}

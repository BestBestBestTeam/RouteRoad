package com.routeroad.execution.application;

import com.routeroad.execution.api.AlertView;
import com.routeroad.execution.domain.AlertSeverity;
import com.routeroad.execution.domain.AlertStatus;
import com.routeroad.shared.api.Page;
import com.routeroad.shared.application.TextSearch;
import com.routeroad.simulation.application.ScenarioClockQuery;
import com.routeroad.simulation.application.ScenarioNotInitializedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;

@Service
public class AlertQueryService {
    private final AlertReader reader;
    private final ScenarioClockQuery scenario;
    public AlertQueryService(AlertReader reader, ScenarioClockQuery scenario) {
        this.reader = reader;
        this.scenario = scenario;
    }
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Page<AlertView> get(LocalDate date, String search, AlertStatus status, AlertSeverity severity,
                               int page, int pageSize) {
        if (page < 1 || pageSize < 1 || pageSize > 100) throw new IllegalArgumentException("Invalid pagination");
        var context = scenario.get(date).orElseThrow(ScenarioNotInitializedException::new);
        var data = reader.find(new AlertReader.Filter(context.date(), TextSearch.contains(search), status, severity),
                pageSize, ((long)page - 1) * pageSize, context.date().equals(context.scenarioDate()),context.clock().now());
        return new Page<>(data.items(), Math.toIntExact(data.total()), page, pageSize, context.clock());
    }
}

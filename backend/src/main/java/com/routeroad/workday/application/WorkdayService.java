package com.routeroad.workday.application;

import com.routeroad.simulation.application.ScenarioNotInitializedException;
import com.routeroad.workday.api.WorkdayCounts;
import com.routeroad.workday.api.WorkdayView;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.Map;

@Service
public class WorkdayService {
    private final WorkdayReader reader;
    private final com.routeroad.simulation.application.ScenarioClockQuery scenario;

    public WorkdayService(WorkdayReader reader, com.routeroad.simulation.application.ScenarioClockQuery scenario) {
        this.reader = reader;
        this.scenario = scenario;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public WorkdayView get(@Nullable LocalDate requestedDate) {
        var context = scenario.get(requestedDate).orElseThrow(ScenarioNotInitializedException::new);
        var date = context.date();
        var clock = context.clock();
        var algorithm = algorithm(context.config());
        var counts = reader.count(date);
        var offices = reader.findOffices().stream().map(WorkdayReader.Office::view).toList();
        return new WorkdayView(date, new WorkdayCounts(Math.toIntExact(counts.ordersTotal()),
                Math.toIntExact(counts.ordersAssigned()), Math.toIntExact(counts.ordersCompleted()),
                Math.toIntExact(counts.couriersTotal()), Math.toIntExact(counts.openAlerts())),
                counts.hasRoutes(), algorithm, offices, clock);
    }

    private static String algorithm(Map<String, Object> config) {
        Object value = config.getOrDefault("algorithm", "greedy");
        if ("greedy".equals(value) || "ortools".equals(value)) return (String) value;
        // Persisted configuration is a server error, not an invalid client query.
        throw new IllegalStateException("Invalid scenario algorithm configuration");
    }
}

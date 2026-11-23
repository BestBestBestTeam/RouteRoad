package com.routeroad.workday.application;

import com.routeroad.simulation.application.ScenarioClockQuery;
import com.routeroad.simulation.application.ScenarioNotInitializedException;
import com.routeroad.workday.api.MapView;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;

@Service
public class WorkdayMapService {
    private final WorkdayMapReader reader;
    private final ScenarioClockQuery scenario;
    public WorkdayMapService(WorkdayMapReader reader, ScenarioClockQuery scenario) {
        this.reader = reader;
        this.scenario = scenario;
    }
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public MapView get(@Nullable LocalDate date) {
        var context = scenario.get(date).orElseThrow(ScenarioNotInitializedException::new);
        var data = reader.read(context.date());
        return new MapView(data.orders(), data.couriers(), data.offices(), data.routes(), data.alerts(), context.clock());
    }
}

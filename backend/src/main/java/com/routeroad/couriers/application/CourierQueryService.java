package com.routeroad.couriers.application;

import com.routeroad.couriers.api.CourierView;
import com.routeroad.shared.api.Page;
import com.routeroad.shared.application.TextSearch;
import com.routeroad.simulation.application.ScenarioClockQuery;
import com.routeroad.simulation.application.ScenarioNotInitializedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;

@Service
public class CourierQueryService {
    private final CourierReader reader;
    private final ScenarioClockQuery scenario;
    public CourierQueryService(CourierReader reader, ScenarioClockQuery scenario) {
        this.reader = reader;
        this.scenario = scenario;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Page<CourierView> get(LocalDate date, String search, Boolean available, Boolean hasOpenAlerts,
                                 Boolean hasWorkload, int page, int pageSize) {
        if (page < 1 || pageSize < 1 || pageSize > 100) throw new IllegalArgumentException("Invalid pagination");

        var context = scenario.get(date).orElseThrow(ScenarioNotInitializedException::new);
        var result = reader.find(new CourierReader.Filter(context.date(), TextSearch.contains(search),
                available, hasOpenAlerts, hasWorkload), pageSize, ((long) page - 1) * pageSize);
                
        return new Page<>(result.items(), Math.toIntExact(result.total()), page, pageSize, context.clock());
    }
}

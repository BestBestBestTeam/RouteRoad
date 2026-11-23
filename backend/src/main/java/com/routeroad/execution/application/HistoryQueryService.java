package com.routeroad.execution.application;

import com.routeroad.execution.api.HistoryEntry;
import com.routeroad.shared.api.Page;
import com.routeroad.shared.application.TextSearch;
import com.routeroad.simulation.application.ScenarioClockQuery;
import com.routeroad.simulation.application.ScenarioNotInitializedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

@Service
public class HistoryQueryService {
    private final HistoryReader reader;
    private final ScenarioClockQuery scenario;
    public HistoryQueryService(HistoryReader reader, ScenarioClockQuery scenario) {
        this.reader = reader;
        this.scenario = scenario;
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Page<HistoryEntry> get(LocalDate date, String search, String type, String actorRole, UUID orderId,
                                  int page, int pageSize) {
        if (page < 1 || pageSize < 1 || pageSize > 100) throw new IllegalArgumentException("Invalid pagination");
        if (type != null && !HistoryPresentation.categories().contains(type)) throw new IllegalArgumentException("Unknown history type");
        if (actorRole != null && !Set.of("dispatcher","representative","system").contains(actorRole))
            throw new IllegalArgumentException("Unknown actor role");
        var context = scenario.get(date).orElseThrow(ScenarioNotInitializedException::new);
        var result = reader.find(new HistoryReader.Filter(context.date(),TextSearch.contains(search),type,actorRole,orderId),
                pageSize,((long)page-1)*pageSize);
        return new Page<>(result.items(),Math.toIntExact(result.total()),page,pageSize,context.clock());
    }
}

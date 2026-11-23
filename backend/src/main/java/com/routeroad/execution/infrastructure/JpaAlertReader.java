package com.routeroad.execution.infrastructure;

import java.time.Instant;
import com.routeroad.execution.api.AlertView;
import com.routeroad.execution.application.AlertReader;
import org.springframework.stereotype.Component;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class JpaAlertReader implements AlertReader {
    private final AlertRepository alerts;
    private final AlertViewAssembler assembler;
    public JpaAlertReader(AlertRepository alerts, AlertViewAssembler assembler) {
        this.alerts = alerts;
        this.assembler = assembler;
    }
    @Override public Result find(Filter filter, int limit, long offset, boolean allowDispatcherActions, Instant modelNow) {
        String status = filter.status() == null ? null : filter.status().name().toLowerCase(Locale.ROOT);
        String severity = filter.severity() == null ? null : filter.severity().name().toLowerCase(Locale.ROOT);
        long total = alerts.countAlertList(filter.date(),filter.search(),status,severity);
        if (total == 0) return new Result(List.of(),0);
        var ids = alerts.findAlertPageIds(filter.date(),filter.search(),status,severity,limit,offset);
        if (ids.isEmpty()) return new Result(List.of(),total);
        var views = assembler.assemble(alerts.findViewsByIds(ids,filter.date()),allowDispatcherActions,modelNow).stream()
                .collect(Collectors.toMap(AlertView::id, Function.identity()));
        // Child-query order is independent of the agreed header-page order.
        return new Result(ids.stream().map(id -> Objects.requireNonNull(views.get(id))).toList(),total);
    }
}

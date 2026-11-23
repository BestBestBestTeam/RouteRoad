package com.routeroad.execution.application;

import java.time.Instant;
import com.routeroad.execution.api.AlertView;
import com.routeroad.execution.domain.AlertSeverity;
import com.routeroad.execution.domain.AlertStatus;
import java.time.LocalDate;
import java.util.List;

public interface AlertReader {
    record Filter(LocalDate date, String search, AlertStatus status, AlertSeverity severity) {}
    record Result(List<AlertView> items, long total) {}
    Result find(Filter filter, int limit, long offset, boolean allowDispatcherActions, Instant modelNow);
}

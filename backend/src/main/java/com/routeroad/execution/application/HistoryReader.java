package com.routeroad.execution.application;

import com.routeroad.execution.api.HistoryEntry;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface HistoryReader {
    record Filter(LocalDate date, String search, String type, String actorRole, UUID orderId) {}
    record Result(List<HistoryEntry> items, long total) {}
    Result find(Filter filter, int limit, long offset);
}

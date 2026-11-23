package com.routeroad.execution.infrastructure;

import com.routeroad.execution.api.AlertView;
import com.routeroad.execution.domain.ReassignmentActions;
import com.routeroad.shared.api.Actor;
import com.routeroad.shared.api.Resolution;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.*;

/** Shared public alert projection for order details and dispatcher lists. */
@Component
public class AlertViewAssembler {
    public List<AlertView> assemble(List<OrderAlertRow> rows, boolean allowActions, Instant modelNow) {
        var groups = new LinkedHashMap<UUID,List<OrderAlertRow>>();
        for (var row : rows) groups.computeIfAbsent(row.getId(), ignored -> new ArrayList<>()).add(row);
        return groups.values().stream().map(group -> view(group,allowActions,modelNow)).toList();
    }

    private AlertView view(List<OrderAlertRow> rows, boolean allowActions, Instant modelNow) {
        var header = rows.getFirst();
        var entries = rows.stream().filter(row -> row.getOrderId() != null).map(row -> new AlertView.OrderEntry(row.getOrderId(), row.getItemStatus(),
                resolution(row.getItemResolvedAt(), row.getItemResolvedBy(), row.getItemResolverName(),
                        row.getItemResolverRole(), row.getItemResolutionReason()))).toList();
        long pending = entries.stream().filter(entry -> entry.status().equals("pending")).count();
        boolean hasEligiblePending = allowActions && header.getStatus().equals("open") && rows.stream()
                .anyMatch(row -> row.getOrderId() != null && "pending".equals(row.getItemStatus()) && row.getItemCanReassign());
        String title = switch (header.getKind()) {
            case "delay" -> header.getScope().equals("order") ? "Задержка встречи" : "Задержка представителя";
            case "refusal" -> header.getScope().equals("order") ? "Отказ от заявки" : "Отказ от заявок на день";
            case "conflict" -> "Конфликт назначения";
            default -> throw new IllegalStateException("Unknown alert kind");
        };
        return new AlertView(header.getId(), header.getSourceEventId(), header.getServiceDate(), header.getCourierId(),
                header.getKind(), header.getScope(), header.getSeverity(), header.getStatus(), header.getReasonCode(),
                header.getReasonText(), header.getCreatedAt(), header.getDeadlineAt(), header.getResolvedAt(),
                header.getResolvedBy(), header.getResolutionReason(), entries.stream().map(AlertView.OrderEntry::orderId).toList(),
                entries, title, List.of("Затронуто заявок: " + entries.size(), "Ожидают решения: " + pending),
                resolution(header.getResolvedAt(), header.getResolvedBy(), header.getResolverName(),
                        header.getResolverRole(), header.getResolutionReason()),
                ReassignmentActions.forAlert(hasEligiblePending,modelNow,header.getDeadlineAt()));
    }

    private Resolution resolution(Instant at, UUID by, String name, String role, String reason) {
        if (at == null) return null;
        Actor actor = by == null ? new Actor(null, "Система", Actor.Role.SYSTEM)
                : new Actor(by, name, Actor.Role.valueOf(role.toUpperCase(Locale.ROOT)));
        // The source reason is nullable; an empty display reason adds no invented explanation.
        return new Resolution(at, actor, reason == null ? "" : reason);
    }
}

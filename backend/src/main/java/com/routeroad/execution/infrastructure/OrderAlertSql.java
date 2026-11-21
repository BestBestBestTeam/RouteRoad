package com.routeroad.execution.infrastructure;

public final class OrderAlertSql {
    private OrderAlertSql() {}
    public static final String RELATED = """
            SELECT al.id, al.source_event_id AS "sourceEventId", al.service_date AS "serviceDate",
                al.courier_id AS "courierId", al.kind, al.scope, al.severity, al.status,
                al.reason_code AS "reasonCode", al.reason_text AS "reasonText", al.created_at AS "createdAt",
                al.deadline_at AS "deadlineAt", al.resolved_at AS "resolvedAt", al.resolved_by AS "resolvedBy",
                al.resolution_reason AS "resolutionReason", resolver.name AS "resolverName", resolver.role AS "resolverRole",
                ao.order_id AS "orderId", ao.status AS "itemStatus", ao.resolved_at AS "itemResolvedAt",
                ao.resolved_by AS "itemResolvedBy", ao.resolution_reason AS "itemResolutionReason",
                item_resolver.name AS "itemResolverName", item_resolver.role AS "itemResolverRole",
                (o.service_date = :date AND o.status IN ('NEW','ASSIGNED','UNASSIGNED','EN_ROUTE','ARRIVED')) AS "itemCanReassign"
            FROM alert al JOIN alert_order ao ON ao.alert_id = al.id
            JOIN service_order o ON o.id = ao.order_id
            LEFT JOIN app_user resolver ON resolver.id = al.resolved_by
            LEFT JOIN app_user item_resolver ON item_resolver.id = ao.resolved_by
            WHERE al.service_date = :date
                AND EXISTS (SELECT 1 FROM alert_order target WHERE target.alert_id = al.id AND target.order_id = :orderId)
                AND (CAST(:courierId AS uuid) IS NULL OR (
                    al.scope = 'order' AND al.courier_id = :courierId
                    AND NOT EXISTS (SELECT 1 FROM alert_order other
                        WHERE other.alert_id = al.id AND other.order_id <> :orderId)))
            ORDER BY al.created_at DESC, al.id, ao.order_id
            """;
}

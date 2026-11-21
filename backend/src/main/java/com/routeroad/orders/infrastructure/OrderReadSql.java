package com.routeroad.orders.infrastructure;

public final class OrderReadSql {
    private OrderReadSql() {}
    public static final String SNAPSHOT = com.routeroad.planning.infrastructure.ConfirmedOrderSnapshotSql.SNAPSHOT;
    public static final String DETAIL_SNAPSHOT = com.routeroad.planning.infrastructure.ConfirmedOrderSnapshotSql.DETAIL_SNAPSHOT;
    public static final String FILTER = """
            FROM order_snapshot s
            WHERE (CAST(:search AS text) IS NULL OR lower(s.number) LIKE :search ESCAPE '!'
                OR lower(s.client_name) LIKE :search ESCAPE '!' OR lower(s.address) LIKE :search ESCAPE '!'
                OR lower(s.district) LIKE :search ESCAPE '!' OR lower(s.product) LIKE :search ESCAPE '!')
            AND (CAST(:status AS text) IS NULL OR s.day_status = :status)
            AND (CAST(:courierId AS uuid) IS NULL OR (s.is_assigned AND s.decision_courier_id = :courierId))
            AND (CAST(:assigned AS boolean) IS NULL OR s.is_assigned = :assigned)
            """;
    public static final String COUNTS = SNAPSHOT + """
            SELECT count(*) AS "ordersTotal", count(*) FILTER (WHERE is_assigned) AS "ordersAssigned",
                count(*) FILTER (WHERE service_date = :date AND status = 'COMPLETED') AS "ordersCompleted"
            FROM order_snapshot
            """;
    private static final String COLUMNS = """
            SELECT s.id, s.number, s.client_name AS "clientName", s.address, s.district, s.product, s.lat, s.lon,
                s.window_from AS "windowFrom", s.window_to AS "windowTo", s.service_duration_s AS "serviceDurationS",
                s.status, s.service_date AS "serviceDate", s.version, s.day_status AS "dayStatus",
                s.decision_plan_version AS "planVersion", s.decision_courier_id AS "courierId",
                s.decision_route_id AS "routeId", s.source_order_version AS "sourceOrderVersion",
                s.reason_code AS "reasonCode", s.reason_text AS "reasonText", s.decided_by AS "decidedBy",
                s.decision_created_at AS "decisionCreatedAt", s.courier_name AS "courierName",
                s.actor_id AS "actorId", s.actor_name AS "actorName", s.actor_role AS "actorRole",
                s.can_reassign AS "canReassign"
            """;
    public static final String ROWS = SNAPSHOT + COLUMNS + FILTER + " ORDER BY s.number, s.id LIMIT :limit OFFSET :offset";
    public static final String DETAIL = DETAIL_SNAPSHOT + COLUMNS + " FROM order_snapshot s";
    public static final String SCHEDULE = com.routeroad.planning.infrastructure.ConfirmedOrderSnapshotSql.COURIER_SNAPSHOT
            + COLUMNS + """
            , EXISTS (SELECT 1 FROM business_event e WHERE e.type = 'ORDER_REFUSED'
                AND e.order_id = s.id AND e.courier_id = :courierId) AS "orderRefused",
              EXISTS (SELECT 1 FROM business_event e WHERE e.type = 'COURIER_REFUSED'
                AND e.courier_id = :courierId AND e.service_date = :date) AS "dayRefused"
            FROM order_snapshot s WHERE s.is_assigned AND s.decision_courier_id = :courierId
            ORDER BY s.number, s.id
            """;
    public static final String TOTAL = SNAPSHOT + "SELECT count(*) " + FILTER;
}

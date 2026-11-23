package com.routeroad.couriers.infrastructure;

import com.routeroad.planning.infrastructure.ConfirmedOrderSnapshotSql;

public final class CourierReadSql {
    private CourierReadSql() {}
    public static final String SNAPSHOT = ConfirmedOrderSnapshotSql.SNAPSHOT + """
            , order_stats AS (
                SELECT decision_courier_id AS courier_id, count(*) AS total_orders,
                    count(*) FILTER (WHERE day_status = 'COMPLETED') AS completed_orders,
                    bool_or(day_status IN ('ASSIGNED','EN_ROUTE','ARRIVED','IN_PROGRESS')) AS has_workload
                FROM order_snapshot WHERE is_assigned
                GROUP BY decision_courier_id
            ), alert_stats AS (
                SELECT courier_id, count(*) AS open_alert_count
                FROM alert WHERE service_date = :date AND status = 'open' AND courier_id IS NOT NULL
                GROUP BY courier_id
            ), courier_snapshot AS (
                SELECT c.*, COALESCE(o.total_orders, 0) AS total_orders,
                    COALESCE(o.completed_orders, 0) AS completed_orders,
                    COALESCE(o.has_workload, false) AS has_workload,
                    COALESCE(a.open_alert_count, 0) AS open_alert_count
                FROM courier c
                LEFT JOIN order_stats o ON o.courier_id = c.id
                LEFT JOIN alert_stats a ON a.courier_id = c.id
            )
            """;
    public static final String FILTER = """
            FROM courier_snapshot c
            WHERE (CAST(:search AS text) IS NULL OR lower(c.name) LIKE :search ESCAPE '!')
                AND (CAST(:available AS boolean) IS NULL OR c.is_available = :available)
                AND (CAST(:hasOpenAlerts AS boolean) IS NULL OR (c.open_alert_count > 0) = :hasOpenAlerts)
                AND (CAST(:hasWorkload AS boolean) IS NULL OR c.has_workload = :hasWorkload)
            """;
    public static final String ROWS = SNAPSHOT + """
            SELECT c.id, c.name, c.office_id AS "officeId", c.shift_start AS "shiftStart", c.shift_end AS "shiftEnd",
                c.position_lat AS "positionLat", c.position_lon AS "positionLon", c.position_at AS "positionAt",
                c.is_available AS "available", c.total_orders AS "totalOrders",
                c.completed_orders AS "completedOrders", c.open_alert_count AS "openAlertCount"
            """ + FILTER + " ORDER BY c.name, c.id LIMIT :limit OFFSET :offset";
    public static final String TOTAL = SNAPSHOT + "SELECT count(*) " + FILTER;
}

package com.routeroad.planning.infrastructure;

public final class ConfirmedOrderSnapshotSql {
    private ConfirmedOrderSnapshotSql() {}
    private static final String DAY_SELECT = """
            WITH day_orders AS (
                SELECT o.*, CASE WHEN o.service_date = :date THEN o.status ELSE 'RESCHEDULED' END AS day_status
                FROM service_order o
            """;

    private static final String DAY_MEMBERSHIP = """
            o.service_date = :date OR EXISTS (
                    SELECT 1 FROM business_event e WHERE e.type = 'ORDER_RESCHEDULED' AND e.order_id = o.id
                        AND e.payload ->> 'from_date' = to_char(CAST(:date AS date), 'YYYY-MM-DD')
                )
            """;

    private static final String SNAPSHOT_BODY = """
            ), order_snapshot AS (
                SELECT o.*, d.plan_version AS decision_plan_version, d.courier_id AS decision_courier_id,
                    d.route_id AS decision_route_id, d.source_order_version, d.reason_code, d.reason_text,
                    d.decided_by, d.created_at AS decision_created_at, d.actor_id,
                    c.name AS courier_name, u.name AS actor_name, u.role AS actor_role,
                    (o.service_date = :date AND d.courier_id IS NOT NULL) AS is_assigned,
                    (o.service_date = :date AND o.day_status IN ('NEW','ASSIGNED','UNASSIGNED','EN_ROUTE','ARRIVED')
                        AND EXISTS (SELECT 1 FROM alert_order ao JOIN alert al ON al.id = ao.alert_id
                            WHERE ao.order_id = o.id AND ao.status = 'pending'
                                AND al.status = 'open' AND al.service_date = :date)) AS can_reassign
                FROM day_orders o
                LEFT JOIN LATERAL (
                    SELECT a.*, e.actor_id
                    FROM assignment a JOIN planning_run p ON p.plan_version = a.plan_version
                    JOIN business_event e ON e.type = 'PLAN_ACTIVATED' AND e.service_date = :date
                        AND e.payload ->> 'plan_version' = CAST(a.plan_version AS text)
                        AND e.payload -> 'order_ids' @> jsonb_build_array(CAST(a.order_id AS text))
                    WHERE a.order_id = o.id AND p.plan_date = :date
                    -- UUID only makes equal-time results repeatable; it does not establish chronology.
                    ORDER BY e.created_at DESC, e.id DESC
                    LIMIT 1
                ) d ON true
                LEFT JOIN courier c ON c.id = d.courier_id
                LEFT JOIN app_user u ON u.id = d.actor_id
            )
            """;

    public static final String SNAPSHOT = DAY_SELECT + " WHERE (" + DAY_MEMBERSHIP + ")" + SNAPSHOT_BODY;
    
    /** Restrict candidates before resolving the same latest confirmed decision as every other read. */
    public static final String COURIER_SNAPSHOT = DAY_SELECT + """
             WHERE o.service_date = :date AND EXISTS (
                 SELECT 1 FROM assignment candidate
                 JOIN planning_run run ON run.plan_version = candidate.plan_version
                 WHERE candidate.order_id = o.id AND candidate.courier_id = :courierId
                     AND run.plan_date = :date
             )
            """ + SNAPSHOT_BODY;

    public static final String DETAIL_SNAPSHOT = DAY_SELECT + " WHERE o.id = :id AND (" + DAY_MEMBERSHIP + ")" + SNAPSHOT_BODY;
}

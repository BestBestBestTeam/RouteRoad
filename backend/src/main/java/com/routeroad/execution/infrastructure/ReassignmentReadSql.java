package com.routeroad.execution.infrastructure;

/** Shared order eligibility and concrete pending-item scope for all action projections. */
public final class ReassignmentReadSql {
    private ReassignmentReadSql() {}
    public static final String ELIGIBLE_ORDER = "o.service_date = :date AND o.status IN ('NEW','ASSIGNED','UNASSIGNED','EN_ROUTE','ARRIVED')";
    public static final String OPEN_PENDING = "ao.status = 'pending' AND al.status = 'open' AND al.service_date = :date";
    // An order can use any eligible pending alert whose individual manual deadline has not expired.
    public static final String PENDING_DEADLINE = """
            SELECT max(al.deadline_at) AS deadline
            FROM alert_order ao JOIN alert al ON al.id = ao.alert_id
            WHERE ao.order_id = o.id AND
            """ + OPEN_PENDING;
    public static final String AWAITING_DECISION = """
            EXISTS (SELECT 1 FROM alert_order ao JOIN alert al ON al.id = ao.alert_id
                WHERE ao.order_id = :orderId AND
            """ + OPEN_PENDING + ")";
}

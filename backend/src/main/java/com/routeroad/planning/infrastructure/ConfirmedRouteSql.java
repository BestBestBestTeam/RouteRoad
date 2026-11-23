package com.routeroad.planning.infrastructure;

/** Shared activation predicate for the summary, individual schedules and the workday map. */
public final class ConfirmedRouteSql {
    private ConfirmedRouteSql() {}
    private static final String PREDICATE = """
            r.plan_date = :date AND r.is_active AND EXISTS (
                SELECT 1 FROM business_event e
                WHERE e.type = 'PLAN_ACTIVATED' AND e.service_date = :date
                    AND e.payload ->> 'plan_version' = CAST(r.plan_version AS text)
            )
            """;
    public static final String EXISTS = "SELECT EXISTS (SELECT 1 FROM route r WHERE " + PREDICATE + ")";
    public static final String ONE = "SELECT r.* FROM route r WHERE r.courier_id = :courierId AND " + PREDICATE;
    public static final String ALL = "SELECT r.* FROM route r WHERE " + PREDICATE + " ORDER BY r.courier_id, r.id";
}

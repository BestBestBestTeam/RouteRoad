package com.routeroad.execution.infrastructure;

/** Filter unique headers first; matching one child never trims the returned group. */
public final class AlertReadSql {
    private AlertReadSql() {}
    private static final String FILTER = """
            FROM alert al LEFT JOIN courier c ON c.id = al.courier_id
            WHERE al.service_date = :date
                AND (CAST(:status AS text) IS NULL OR al.status = :status)
                AND (CAST(:severity AS text) IS NULL OR al.severity = :severity)
                AND (CAST(:search AS text) IS NULL
                    OR lower(al.reason_code) LIKE :search ESCAPE '!'
                    OR lower(al.reason_text) LIKE :search ESCAPE '!'
                    OR lower(c.name) LIKE :search ESCAPE '!'
                    OR EXISTS (SELECT 1 FROM alert_order ao JOIN service_order o ON o.id = ao.order_id
                        WHERE ao.alert_id = al.id AND (
                            lower(o.number) LIKE :search ESCAPE '!'
                            OR lower(o.client_name) LIKE :search ESCAPE '!'
                            OR lower(o.address) LIKE :search ESCAPE '!')))
            """;
    public static final String TOTAL = "SELECT count(*) " + FILTER;
    public static final String IDS = "SELECT al.id " + FILTER
            + " ORDER BY al.created_at DESC, al.id ASC LIMIT :limit OFFSET :offset";
}

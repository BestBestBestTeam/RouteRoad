package com.routeroad.execution.infrastructure;

import com.routeroad.execution.api.HistoryEntry;
import com.routeroad.execution.application.HistoryPresentation;
import com.routeroad.execution.application.HistoryReader;
import com.routeroad.execution.domain.BusinessEventType;
import com.routeroad.shared.api.Actor;
import java.sql.Types;
import java.util.Locale;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JpaHistoryReader implements HistoryReader {
    private final NamedParameterJdbcTemplate jdbc;
    private final HistoryChanges changes;
    public JpaHistoryReader(NamedParameterJdbcTemplate jdbc, HistoryChanges changes) {
        this.jdbc = jdbc;
        this.changes = changes;
    }
    @Override public Result find(Filter filter, int limit, long offset) {
        var parameters = new MapSqlParameterSource().addValue("date", filter.date(), Types.DATE)
                .addValue("search", filter.search(), Types.VARCHAR).addValue("type", filter.type(), Types.VARCHAR)
                .addValue("actor_role", filter.actorRole(), Types.VARCHAR)
                .addValue("order_id", filter.orderId() == null ? null : filter.orderId().toString(), Types.VARCHAR)
                .addValue("limit", limit).addValue("offset", offset);
        var total = jdbc.queryForObject(HistoryReadSql.COUNT, parameters, Long.class);
        var items = jdbc.query(HistoryReadSql.ROWS, parameters, (row, index) -> {
            var definition = HistoryPresentation.definition(BusinessEventType.valueOf(row.getString("internal_type")));
            return new HistoryEntry(row.getObject("id", UUID.class), definition.category(), row.getTimestamp("created_at").toInstant(),
                    new Actor(row.getObject("actor_id", UUID.class), row.getString("actor_name"),
                            Actor.Role.valueOf(row.getString("actor_role").toUpperCase(Locale.ROOT))),
                    row.getObject("order_id", UUID.class), row.getObject("alert_id", UUID.class), definition.title(),
                    row.getString("description"), row.getString("reason"), changes.read(row.getString("changes_json")));
        });
        return new Result(items, total);
    }
}

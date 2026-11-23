package com.routeroad.execution.infrastructure;

import com.routeroad.execution.application.HistoryPresentation;
import com.routeroad.execution.domain.BusinessEventType;
import java.util.function.Function;

/** Search and returned descriptions share this projection; titles share the Java dictionary. */
public final class HistoryReadSql {
    private HistoryReadSql() {}
    private static final String UUID_PATTERN = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$";
    private static String literal(String value) { return "'" + value.replace("'", "''") + "'"; }
    public static String dictionaryCase(Function<HistoryPresentation.Definition,String> value) {
        var sql = new StringBuilder("case e.type ");
        for (var type : BusinessEventType.values())
            sql.append("when ").append(literal(type.name())).append(" then ")
                    .append(literal(value.apply(HistoryPresentation.definition(type)))).append(' ');
        return sql.append("end").toString();
    }
    private static String array(String expression) {
        return "(case when jsonb_typeof(" + expression + ")='array' then " + expression + " else '[]'::jsonb end)";
    }
    private static String validUuid(String expression) {
        return "(jsonb_typeof(" + expression + ")='string' and (" + expression + " #>> '{}') ~ " + literal(UUID_PATTERN) + ")";
    }
    private static String validUuidArray(String expression) {
        return "(jsonb_typeof(" + expression + ")='array' and not exists (select 1 from jsonb_array_elements("
                + array(expression) + ") v where not " + validUuid("v.value") + "))";
    }
    private static String references(String key, String label) {
        var expression = "e.payload->'" + key + "'";
        return "case when " + validUuidArray(expression) + " and jsonb_array_length(" + array(expression) + ")>0 then "
                + literal(label) + " || (select string_agg(v.value #>> '{}', ', ' order by v.n) from jsonb_array_elements("
                + array(expression) + ") with ordinality v(value,n)) end";
    }
    private static String snapshot() {
        return """
                (case when jsonb_typeof(e.payload->'actor')='object'
                    and jsonb_typeof(e.payload#>'{actor,id}')='string'
                    and (e.payload#>>'{actor,id}') ~ %s
                    then (e.payload#>>'{actor,id}')::uuid=e.actor_id else false end
                 and jsonb_typeof(e.payload#>'{actor,name}')='string'
                 and btrim(e.payload#>>'{actor,name}')<>''
                 and e.payload#>>'{actor,role}' in ('dispatcher','representative'))
                """.formatted(literal(UUID_PATTERN));
    }
    private static final String DESCRIPTION = """
            concat_ws('; ',
                case when jsonb_typeof(e.payload->'description')='string' then e.payload->>'description' else e.reason end,
                case when e.type='OFFLINE_ACTION_CONFLICT' then concat_ws('; ',
                    case when jsonb_typeof(e.payload->'reason_code')='string' and btrim(e.payload->>'reason_code')<>''
                         then 'Код причины: ' || (e.payload->>'reason_code')
                         when jsonb_typeof(e.payload->'reason')='string' and btrim(e.payload->>'reason')<>''
                         then 'Код причины: ' || (e.payload->>'reason') end,
                    %s, %s) end)
            """.formatted(references("client_action_ids", "Действия: "), references("command_ids", "Команды: "));
    private static final String PROJECTION = """
            select e.id, e.type as internal_type, e.created_at, e.order_id, e.actor_id, e.payload,
                %s as type, %s as title, %s as description, e.reason,
                case when e.actor_id is null then 'Система'
                     when %s then e.payload#>>'{actor,name}' else u.name end as actor_name,
                case when e.actor_id is null then 'system'
                     when %s then e.payload#>>'{actor,role}' else u.role end as actor_role,
                case when %s then (e.payload->>'alert_id')::uuid end as alert_id,
                (e.payload->'changes')::text as changes_json
            from business_event e left join app_user u on u.id=e.actor_id
            where e.service_date=:date
            """.formatted(dictionaryCase(HistoryPresentation.Definition::category),
                    dictionaryCase(HistoryPresentation.Definition::title), DESCRIPTION, snapshot(), snapshot(), validUuid("e.payload->'alert_id'"));
    private static final String FILTER = """
            where (:type is null or h.type=:type) and (:actor_role is null or h.actor_role=:actor_role)
                and (:search is null or lower(h.title) like :search escape '!'
                     or lower(h.description) like :search escape '!' or lower(h.reason) like :search escape '!')
                and (cast(:order_id as uuid) is null or h.order_id=cast(:order_id as uuid)
                     or (%s and exists (select 1 from jsonb_array_elements(%s) v
                         where lower(v.value #>> '{}')=cast(:order_id as text))))
            """.formatted(validUuidArray("h.payload->'order_ids'"), array("h.payload->'order_ids'"));
    public static final String COUNT = "with history as (" + PROJECTION + ") select count(*) from history h " + FILTER;
    public static final String ROWS = "with history as (" + PROJECTION + ") select h.id,h.internal_type,h.created_at,h.order_id,h.actor_id,h.actor_name,h.actor_role,h.alert_id,h.description,h.reason,h.changes_json from history h " + FILTER
            + " order by h.created_at desc, h.id asc limit :limit offset :offset";
}

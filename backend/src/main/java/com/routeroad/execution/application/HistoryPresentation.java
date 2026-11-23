package com.routeroad.execution.application;

import com.routeroad.execution.domain.BusinessEventType;
import java.util.*;
import java.util.stream.Collectors;

/** One classification/title dictionary, also used to generate the SQL search projection. */
public final class HistoryPresentation {
    public record Definition(String category, String title) {}
    private static final Map<BusinessEventType,Definition> DEFINITIONS;
    static {
        var definitions = new EnumMap<BusinessEventType,Definition>(BusinessEventType.class);
        definitions.put(BusinessEventType.DATA_LOADED,new Definition("data_loaded","Данные загружены"));
        definitions.put(BusinessEventType.PLAN_ACTIVATED,new Definition("routes_built","План активирован"));
        definitions.put(BusinessEventType.STATUS_CHANGED,new Definition("status_changed","Статус заявки изменён"));
        definitions.put(BusinessEventType.ASSIGNMENT_CHANGED,new Definition("assignment_changed","Назначение заявки изменено"));
        definitions.put(BusinessEventType.ORDER_RESCHEDULED,new Definition("status_changed","Заявка перенесена"));
        definitions.put(BusinessEventType.COURIER_DELAYED,new Definition("status_changed","Представитель сообщил о задержке"));
        definitions.put(BusinessEventType.ORDER_REFUSED,new Definition("alert_created","Представитель отказался от заявки"));
        definitions.put(BusinessEventType.COURIER_REFUSED,new Definition("alert_created","Представитель отказался от работы на день"));
        definitions.put(BusinessEventType.ALERT_CREATED,new Definition("alert_created","Создан алерт"));
        definitions.put(BusinessEventType.ALERT_ORDER_RESOLVED,new Definition("alert_resolved","Решена одна из заявок алерта"));
        definitions.put(BusinessEventType.ALERT_RESOLVED,new Definition("alert_resolved","Алерт закрыт"));
        definitions.put(BusinessEventType.OFFLINE_ACTION_CONFLICT,new Definition("sync_conflict","Офлайн-действие отклонено"));
        definitions.put(BusinessEventType.SCENARIO_RESET,new Definition("data_loaded","Сценарий сброшен"));
        if (definitions.size() != BusinessEventType.values().length) throw new IllegalStateException("Missing history presentation");
        DEFINITIONS = Map.copyOf(definitions);
    }
    private HistoryPresentation() {}
    public static Definition definition(BusinessEventType type) { return Objects.requireNonNull(DEFINITIONS.get(type)); }
    public static Set<String> categories() {
        return DEFINITIONS.values().stream().map(Definition::category).collect(Collectors.toUnmodifiableSet());
    }
}

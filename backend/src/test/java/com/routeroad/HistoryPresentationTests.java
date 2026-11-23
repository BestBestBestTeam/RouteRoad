package com.routeroad;

import com.routeroad.execution.application.HistoryPresentation;
import com.routeroad.execution.domain.BusinessEventType;
import com.routeroad.execution.infrastructure.HistoryChanges;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;

class HistoryPresentationTests {
    private final HistoryChanges changes = new HistoryChanges(JsonMapper.builder().build());
    @ParameterizedTest @CsvSource({
        "DATA_LOADED,data_loaded,Данные загружены", "PLAN_ACTIVATED,routes_built,План активирован",
        "STATUS_CHANGED,status_changed,Статус заявки изменён", "ASSIGNMENT_CHANGED,assignment_changed,Назначение заявки изменено",
        "ORDER_RESCHEDULED,status_changed,Заявка перенесена", "COURIER_DELAYED,status_changed,Представитель сообщил о задержке",
        "ORDER_REFUSED,alert_created,Представитель отказался от заявки", "COURIER_REFUSED,alert_created,Представитель отказался от работы на день",
        "ALERT_CREATED,alert_created,Создан алерт", "ALERT_ORDER_RESOLVED,alert_resolved,Решена одна из заявок алерта",
        "ALERT_RESOLVED,alert_resolved,Алерт закрыт", "OFFLINE_ACTION_CONFLICT,sync_conflict,Офлайн-действие отклонено",
        "SCENARIO_RESET,data_loaded,Сценарий сброшен"})
    void allInternalTypesHaveAccurateTitlesAndSevenUiCategories(String type, String category, String title) {
        var definition=HistoryPresentation.definition(BusinessEventType.valueOf(type));
        assertThat(definition.category()).isEqualTo(category);assertThat(definition.title()).isEqualTo(title);
        assertThat(HistoryPresentation.categories()).hasSize(7);
    }
    @ParameterizedTest @ValueSource(strings={"null","{}","[null]","[{}]","[1]","bad",
        "[{\"field\":\"status\",\"before\":null}]", "[{\"field\":\"\",\"before\":null,\"after\":1}]",
        "[{\"field\":\"version\",\"before\":1,\"after\":9223372036854775808}]"})
    void invalidChangesReturnEmptyArray(String value) { assertThat(changes.read(value)).isEmpty(); }
    @Test void nullChangesAreEmpty() { assertThat(changes.read(null)).isEmpty(); }
    @Test void validChangesPreserveNullAndEmptyValuesAndStringifyLongVersionsRecursively() {
        var result=changes.read("""
            [{"field":"version","before":9007199254740993,"after":"9223372036854775807"},
             {"field":"snapshot","before":null,"after":{"plan_version":42,"revision":null,"count":2,"items":[{"base_version":3}]}},
             {"field":"reason","before":"","after":null}]
            """);
        assertThat(result).hasSize(3);assertThat(result.get(0).before().asString()).isEqualTo("9007199254740993");
        assertThat(result.get(0).before().isString()).isTrue();assertThat(result.get(1).before().isNull()).isTrue();
        assertThat(result.get(1).after().get("plan_version").isString()).isTrue();
        assertThat(result.get(1).after().get("count").isIntegralNumber()).isTrue();
        assertThat(result.get(1).after().path("items").get(0).path("base_version").isString()).isTrue();
        assertThat(result.get(2).before().asString()).isEmpty();assertThat(result.get(2).after().isNull()).isTrue();
    }
}

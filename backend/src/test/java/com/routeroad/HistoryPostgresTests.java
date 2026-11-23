package com.routeroad;

import com.routeroad.execution.api.HistoryEntry;
import com.routeroad.execution.application.*;
import com.routeroad.execution.domain.BusinessEventType;
import com.routeroad.execution.infrastructure.HistoryReadSql;
import com.routeroad.shared.api.Page;
import com.routeroad.simulation.infrastructure.ScenarioStateRepository;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.*;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("local")
@EnabledIfEnvironmentVariable(named="ROUTEROAD_REPOSITORY_TESTS",matches="true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Transactional @Rollback
@Sql({"/repository-fixtures.sql","/history-fixtures.sql"})
class HistoryPostgresTests {
    static final LocalDate DATE=LocalDate.of(2099,11,9);
    @Autowired HistoryQueryService service;
    @Autowired com.routeroad.execution.infrastructure.BusinessEventRepository events;
    @Autowired jakarta.persistence.EntityManager entityManager;
    @Autowired JdbcTemplate jdbc;
    @MockitoSpyBean NamedParameterJdbcTemplate namedJdbc;
    @Autowired ScenarioStateRepository scenarios;
    @Autowired EntityManagerFactory entityManagerFactory;
    @MockitoBean java.time.Clock realClock;
    static UUID id(int group,int n) {return UUID.fromString("a0f8d%03x-0000-4000-8000-%012x".formatted(group,n));}
    @BeforeAll void preserveData() {assumeTrue(scenarios.count()==0,"Existing scenario must be preserved");}
    @BeforeEach void setup() {when(realClock.instant()).thenReturn(Instant.parse("2099-11-09T09:00:10Z"));}
    private Page<HistoryEntry> list(String search,String type,String role,UUID order,int page,int size) {
        return service.get(null,search,type,role,order,page,size);
    }
    private Page<HistoryEntry> all() {return list(null,null,null,null,1,100);}
    private HistoryEntry event(int n) {return all().items().stream().filter(e->e.id().equals(id(0xc00,n))).findFirst().orElseThrow();}
    @Test void persistedTypesUseAccuratePublicClassification() {
        var all=all();assertThat(all.total()).isEqualTo(15);
        for(int n=1;n<=12;n++) {
            var internal=BusinessEventType.valueOf(jdbc.queryForObject("select type from business_event where id=?",String.class,id(0xc00,n)));
            assertThat(event(n).type()).isEqualTo(HistoryPresentation.definition(internal).category());
            assertThat(event(n).title()).isEqualTo(HistoryPresentation.definition(internal).title());
        }
        assertThat(event(7).title()).isEqualTo("Представитель отказался от заявки");
        assertThat(event(10).title()).isEqualTo("Решена одна из заявок алерта");
        assertThat(event(12).title()).isEqualTo("Сценарий сброшен");
    }
    @Test void sqlDictionaryMatchesAllThirteenSupportedTypes() {
        for(var type:BusinessEventType.values()) {
            var definition=HistoryPresentation.definition(type);
            assertThat(jdbc.queryForObject("select "+HistoryReadSql.dictionaryCase(HistoryPresentation.Definition::category)
                +" from (select cast(? as text) as type) e",String.class,type.name())).isEqualTo(definition.category());
            assertThat(jdbc.queryForObject("select "+HistoryReadSql.dictionaryCase(HistoryPresentation.Definition::title)
                +" from (select cast(? as text) as type) e",String.class,type.name())).isEqualTo(definition.title());
        }
    }
    private UUID saveOfflineConflict() {
        var payload = Map.<String,Object>of(
                "reason_code", "ORDER_CANCELLED",
                "client_action_ids", List.of(id(0xa00,1).toString()),
                "command_ids", List.of(id(0x900,1).toString()),
                "actor", Map.of("id",id(0x600,2).toString(),"name","Имя на момент конфликта","role","representative"),
                "changes", List.of(Map.of("field","attempted_status","before","ARRIVED","after","IN_PROGRESS")),
                "secret", "must not be exposed");
        var event = new com.routeroad.execution.infrastructure.BusinessEventEntity(id(0xc00,13),
                BusinessEventType.OFFLINE_ACTION_CONFLICT, DATE, null, id(0x200,1), id(0x600,2),
                Instant.parse("2099-11-09T01:07:00Z"), Instant.parse("2099-11-09T10:13:00Z"),
                "Заявка отменена до применения офлайн-действия", payload);
        events.save(event);
        entityManager.flush();
        entityManager.clear();
        return event.getId();
    }
    @Test void offlineConflictPersistsAndIsReadByRepositoryAndHistoryWithFullContract() {
        var eventId = saveOfflineConflict();
        var stored = events.findById(eventId).orElseThrow();
        assertThat(stored.getType()).isEqualTo(BusinessEventType.OFFLINE_ACTION_CONFLICT);
        assertThat(stored.getReason()).isEqualTo("Заявка отменена до применения офлайн-действия");
        assertThat(stored.getOccurredAt()).isEqualTo(Instant.parse("2099-11-09T01:07:00Z"));
        assertThat(stored.getPayload()).containsEntry("reason_code","ORDER_CANCELLED");
        var page = list(null,"sync_conflict",null,null,1,10);
        assertThat(page.total()).isEqualTo(1);
        assertThat(page.items()).hasSize(1);
        var view = page.items().getFirst();
        assertThat(view.id()).isEqualTo(eventId);
        assertThat(view.type()).isEqualTo("sync_conflict");
        assertThat(view.title()).isEqualTo("Офлайн-действие отклонено");
        assertThat(view.reason()).isEqualTo(stored.getReason());
        assertThat(view.createdAt()).isEqualTo(stored.getCreatedAt());
        assertThat(view.actor()).isEqualTo(new com.routeroad.shared.api.Actor(id(0x600,2),
                "Имя на момент конфликта",com.routeroad.shared.api.Actor.Role.REPRESENTATIVE));
        assertThat(view.orderId()).isNull();assertThat(view.alertId()).isNull();
        assertThat(view.changes()).hasSize(1);
        assertThat(view.changes().getFirst().field()).isEqualTo("attempted_status");
        assertThat(view.changes().getFirst().before().asString()).isEqualTo("ARRIVED");
        assertThat(view.changes().getFirst().after().asString()).isEqualTo("IN_PROGRESS");
        assertThat(jdbc.queryForList("select distinct type from business_event",String.class))
                .containsExactlyInAnyOrderElementsOf(Arrays.stream(BusinessEventType.values()).map(Enum::name).toList());
        assertThat(all().total()).isEqualTo(16);
    }
    @Test void offlineDescriptionUsesSavedCodeAndValidatedReferencesWithoutRawPayload() {
        var eventId=saveOfflineConflict();
        var page=list("ORDER_CANCELLED","sync_conflict","representative",null,1,10);
        assertThat(page.items()).extracting(HistoryEntry::id).containsExactly(eventId);
        assertThat(page.items().getFirst().description()).isEqualTo(
                "Заявка отменена до применения офлайн-действия; Код причины: ORDER_CANCELLED; Действия: "
                +id(0xa00,1)+"; Команды: "+id(0x900,1));
        assertThat(page.items().getFirst().description()).doesNotContain("secret","must not be exposed");
    }
    @Test void unknownTypeStillFailsTheNamedCheckWithoutChangingAnyEvent() {
        var before=digests();
        jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Void>) connection -> {
            var savepoint=connection.setSavepoint();
            try {
                assertThatThrownBy(()-> {try(var statement=connection.prepareStatement("""
                        insert into business_event(id,type,service_date,occurred_at,created_at,reason,payload)
                        values(?,'UNKNOWN_EVENT_TYPE','2099-11-09','2099-11-09T01:00:00Z','2099-11-09T10:00:00Z','Test','{}')
                        """)) {
                    statement.setObject(1,id(0xc00,99));statement.executeUpdate();
                }}).isInstanceOf(java.sql.SQLException.class).satisfies(ex->{
                    assertThat(((java.sql.SQLException)ex).getSQLState()).isEqualTo("23514");
                    assertThat(ex.getMessage()).contains("business_event_type_check");
                });
            } finally {connection.rollback(savepoint);connection.releaseSavepoint(savepoint);}
            return null;
        });
        assertThat(digests()).isEqualTo(before);
    }
    @Test void createdAtAndUuidDetermineOrderRegardlessOfModelOccurredAtOrPlanVersion() {
        var result=all().items();assertThat(result.subList(0,3)).extracting(HistoryEntry::id).containsExactly(id(0xc00,11),id(0xc00,12),id(0xc00,10));
        assertThat(event(3).createdAt()).isEqualTo(Instant.parse("2099-11-09T10:03:00Z"));
        assertThat(list(null,null,null,null,1,1).items()).extracting(HistoryEntry::id).containsExactly(id(0xc00,11));
        assertThat(list(null,null,null,null,2,1).items()).extracting(HistoryEntry::id).containsExactly(id(0xc00,12));
    }
    @Test void allSevenCategoryFiltersAreAcceptedAndCountBeforePagination() {
        var counts=Map.of("data_loaded",2L,"routes_built",3L,"status_changed",3L,"assignment_changed",1L,"alert_created",4L,"alert_resolved",2L,"sync_conflict",0L);
        counts.forEach((type,total)-> {var page=list(null,type,null,null,1,1);assertThat(page.total()).isEqualTo(total.intValue());
            assertThat(page.items()).allSatisfy(e->assertThat(e.type()).isEqualTo(type));});
    }
    @Test void actorRoleUsesTheSameHistoricalSnapshotAsPublicActor() {
        assertThat(event(3).actor().name()).isEqualTo("Историческое имя");
        assertThat(event(3).actor().role().name()).isEqualTo("REPRESENTATIVE");
        assertThat(list(null,null,"representative",null,1,100).total()).isEqualTo(3);
        assertThat(list(null,null,"dispatcher",null,1,100).items()).extracting(HistoryEntry::id).containsExactly(id(0x700,1));
        assertThat(list(null,null,"system",null,1,100).total()).isEqualTo(11);
        assertThat(event(6).actor().id()).isNull();assertThat(event(6).actor().name()).isEqualTo("Система");
    }
    @Test void missingInvalidOrMismatchedSnapshotsFallBackToCurrentUserMetadata() {
        for(String actor:List.of("null","{}","{\"id\":\"bad\",\"name\":\"Fake\",\"role\":\"system\"}",
            "{\"id\":\""+id(0x600,2)+"\",\"name\":\"Other\",\"role\":\"representative\"}")) {
            jdbc.update("update business_event set payload=jsonb_set(payload,'{actor}',cast(? as jsonb)) where id=?",actor,id(0xc00,3));
            assertThat(event(3).actor().name()).isEqualTo("Repo dispatcher");assertThat(event(3).actor().id()).isEqualTo(id(0x600,1));
            assertThat(list(null,null,"dispatcher",null,1,100).total()).isEqualTo(2);
        }
    }
    @Test void nullActorIdAlwaysMeansSystemEvenWhenPayloadContainsAnotherAuthor() {
        jdbc.update("update business_event set payload=(select payload from business_event where id=?) where id=?",id(0xc00,3),id(0xc00,1));
        assertThat(event(1).actor().id()).isNull();assertThat(event(1).actor().role().name()).isEqualTo("SYSTEM");
    }
    @Test void directAndGroupOrderIdsIncludeEachEventOnce() {
        assertThat(list(null,null,null,id(0x300,1),1,100).total()).isEqualTo(4);
        assertThat(list(null,null,null,id(0x300,2),1,100).items()).extracting(HistoryEntry::id).containsExactly(id(0xc00,2),id(0x700,2));
        assertThat(event(2).orderId()).isNull(); // Do not manufacture one order_id for a group.
        jdbc.update("update business_event set payload=jsonb_set(payload,'{order_ids}',(payload->'order_ids')||(payload->'order_ids')) where id=?",id(0xc00,2));
        assertThat(list(null,null,null,id(0x300,2),1,100).total()).isEqualTo(2);
    }
    @Test void malformedOrderArraysNeverMatchAndNeverCauseInvalidUuidCasts() {
        for(String array:List.of("null","{}","\""+id(0x300,2)+"\"","[1]","[null]","[\"bad\"]",
            "[\""+id(0x300,2)+"\",7]","[\""+id(0x300,2)+"\",\"bad\"]")) {
            jdbc.update("update business_event set payload=jsonb_set(payload,'{order_ids}',cast(? as jsonb)) where id=?",array,id(0xc00,2));
            assertThat(list(null,null,null,id(0x300,2),1,100).total()).as(array).isEqualTo(1);
        }
    }
    @Test void searchCoversExactlyDisplayedTitleDescriptionAndReasonWithLiteralEscaping() {
        for(String term:List.of("_%!","ОПИСАНИЕ_%!","Причина_%!","%","!"))
            assertThat(list(term,null,null,null,1,100).items()).as(term).extracting(HistoryEntry::id).containsExactly(id(0xc00,3));
        assertThat(list("Сценарий сброшен",null,null,null,1,100).items()).extracting(HistoryEntry::id).containsExactly(id(0xc00,12));
        assertThat(list("Заявка перенесена",null,null,null,1,100).items()).extracting(HistoryEntry::id).containsExactly(id(0xc00,5));
        for(String term:List.of("Игнорировать заголовок","Repo client A","Историческое имя","' OR 1=1 --","описаниеX"))
            assertThat(list(term,null,null,null,1,100).items()).as(term).isEmpty();
        assertThat(list("   ",null,null,null,1,100).total()).isEqualTo(15);
    }
    @Test void descriptionChangesAlertIdAndEmptyReasonRespectSavedValues() {
        var event=event(3);assertThat(event.description()).isEqualTo("Сохранённое описание_%!");
        assertThat(event.reason()).isEqualTo("Причина_%!");assertThat(event.alertId()).isEqualTo(id(0x800,1));
        assertThat(event.changes()).hasSize(2);assertThat(event.changes().getFirst().before().isString()).isTrue();
        assertThat(event(8).reason()).isEmpty();assertThat(event(1).changes()).isEmpty();assertThat(event(1).alertId()).isNull();
        for(String value:List.of("null","1","\"bad\"","{}")) {
            jdbc.update("update business_event set payload=jsonb_set(payload,'{alert_id}',cast(? as jsonb)) where id=?",value,id(0xc00,3));
            assertThat(event(3).alertId()).isNull();
        }
    }
    @Test void allFiltersCombineBeforeLimitAndTotalAndEmptyPageKeepsTotal() {
        var result=list("_%!","status_changed","representative",id(0x300,1),1,1);
        assertThat(result.total()).isEqualTo(1);assertThat(result.items()).extracting(HistoryEntry::id).containsExactly(id(0xc00,3));
        assertThat(list(null,null,null,null,3,7).items()).hasSize(1);
        var empty=list(null,null,null,null,4,7);assertThat(empty.items()).isEmpty();assertThat(empty.total()).isEqualTo(15);
        assertThat(list(null,null,null,null,Integer.MAX_VALUE,100).items()).isEmpty();
    }
    @Test void explicitDateAndDefaultScenarioDayUseServiceDateAndKeepSameClock() {
        assertThat(service.get(DATE,null,null,null,null,1,100)).isEqualTo(all());
        var tomorrow=service.get(DATE.plusDays(1),null,null,null,null,1,100);
        assertThat(tomorrow.items()).isEmpty();assertThat(tomorrow.clock()).isEqualTo(all().clock());
    }
    @Test void historicalEntriesDoNotChangeWithCurrentOrderAssignmentStatusOrNames() {
        var before=all();jdbc.update("update service_order set status='CANCELLED',client_name='Changed',version=version+1");
        jdbc.update("update assignment set reason_text='Changed decision'");jdbc.update("update courier set name='Changed courier'");
        assertThat(all()).isEqualTo(before);
        jdbc.update("update app_user set name='Renamed dispatcher' where id=?",id(0x600,1));
        assertThat(event(3)).isEqualTo(before.items().stream().filter(e->e.id().equals(id(0xc00,3))).findFirst().orElseThrow());
        assertThat(all().items().stream().filter(e->e.id().equals(id(0x700,1))).findFirst().orElseThrow().actor().name()).isEqualTo("Renamed dispatcher");
    }
    @Test void payloadScenarioTagsAreNotUsedAsFalseGenerationProof() {
        var before=all();jdbc.update("update business_event set payload=jsonb_set(payload,'{scenario_id}',to_jsonb(cast(? as text)))",UUID.randomUUID().toString());
        assertThat(all()).isEqualTo(before);
        jdbc.update("update scenario_state set scenario_id=?",UUID.randomUUID());
        assertThat(all().items()).isEqualTo(before.items()); // Future reset must DELETE old events; GET cannot infer generation.
    }
    @Test void emptyHistoryIsAValidPage() {jdbc.update("delete from alert_order");jdbc.update("delete from alert");jdbc.update("delete from business_event");assertThat(all().total()).isZero();assertThat(all().items()).isEmpty();}
    @Test void allGetsLeaveAllBusinessRowsUnchanged() {var before=digests();all();list("_%!",null,null,null,1,1);service.get(DATE.plusDays(1),null,null,null,null,1,10);assertThat(digests()).isEqualTo(before);}
    @Test void growingEventsUsesTwoJdbcQueriesAndOneScenarioQueryWithoutNPlusOne() {
        var stats=entityManagerFactory.unwrap(SessionFactory.class).getStatistics();boolean enabled=stats.isStatisticsEnabled();stats.setStatisticsEnabled(true);
        try {
            clearInvocations(namedJdbc);stats.clear();all();assertQueries();assertThat(stats.getPrepareStatementCount()).isEqualTo(1);
            jdbc.update("""
                insert into business_event(id,type,service_date,actor_id,occurred_at,created_at,reason,payload)
                select md5('history-bulk-'||n)::uuid,'STATUS_CHANGED','2099-11-09',?,'2099-11-09T00:00:00Z',
                    timestamptz '2099-11-09T11:00:00Z'+n*interval '1 second','Bulk history','{}' from generate_series(1,200) n
                """,id(0x600,1));
            clearInvocations(namedJdbc);stats.clear();var result=all();assertThat(result.total()).isEqualTo(215);assertThat(result.items()).hasSize(100);
            assertQueries();assertThat(stats.getPrepareStatementCount()).isEqualTo(1);
            System.out.println("History SQL queries: 3 -> 3 for 15 -> 215 events (scenario + count + page).");
        } finally {stats.setStatisticsEnabled(enabled);}
    }
    private void assertQueries() {
        verify(namedJdbc,times(1)).queryForObject(eq(HistoryReadSql.COUNT),any(SqlParameterSource.class),eq(Long.class));
        verify(namedJdbc,times(1)).query(eq(HistoryReadSql.ROWS),any(SqlParameterSource.class),any(RowMapper.class));
    }
    private Map<String,String> digests() {
        var result=new LinkedHashMap<String,String>();
        for(String table:List.of("office","courier","service_order","planning_run","route","route_stop","assignment","app_user","business_event","alert","alert_order","api_command","scenario_state"))
            result.put(table,jdbc.queryForObject("select md5(coalesce(string_agg(row_to_json(t)::text,',' order by row_to_json(t)::text),'')) from "+table+" t",String.class));
        return result;
    }
}

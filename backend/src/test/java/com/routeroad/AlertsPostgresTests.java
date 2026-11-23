package com.routeroad;

import com.routeroad.couriers.application.CourierQueryService;
import com.routeroad.execution.api.AlertView;
import com.routeroad.execution.application.AlertQueryService;
import com.routeroad.execution.domain.AlertSeverity;
import com.routeroad.execution.domain.AlertStatus;
import com.routeroad.orders.application.OrderQueryService;
import com.routeroad.shared.api.Page;
import com.routeroad.simulation.infrastructure.ScenarioStateRepository;
import com.routeroad.workday.application.WorkdayMapService;
import com.routeroad.workday.application.WorkdayService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@EnabledIfEnvironmentVariable(named="ROUTEROAD_REPOSITORY_TESTS",matches="true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Transactional
@Rollback
@Sql({"/repository-fixtures.sql","/schedule-fixtures.sql","/map-fixtures.sql","/alert-fixtures.sql"})
class AlertsPostgresTests {
    static final LocalDate DATE=LocalDate.of(2099,11,9);
    static final long PLAN=9_000_000_000_000_000_001L;
    @Autowired AlertQueryService service;
    @Autowired OrderQueryService orders;
    @Autowired CourierQueryService couriers;
    @Autowired WorkdayService workday;
    @Autowired WorkdayMapService map;
    @Autowired ScenarioStateRepository scenarios;
    @Autowired MockMvc mvc;
    @Autowired JsonMapper json;
    @Autowired PasswordEncoder passwords;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager entityManager;
    @Autowired EntityManagerFactory entityManagerFactory;
    @MockitoBean java.time.Clock realClock;
    private final String password=UUID.randomUUID().toString();
    private String hash;
    static UUID id(int group,int number) {return UUID.fromString("a0f8d%03x-0000-4000-8000-%012x".formatted(group,number));}
    @BeforeAll void preserveScenario() {assumeTrue(scenarios.count()==0,"Existing scenario must be preserved");hash=passwords.encode(password);}
    @BeforeEach void setup() {
        when(realClock.instant()).thenReturn(Instant.parse("2099-11-09T09:00:10Z"));
        jdbc.update("update app_user set password_hash=? where login in ('repo-dispatcher','repo-representative')",hash);
    }
    private Page<AlertView> list(String search,AlertStatus status,AlertSeverity severity,int page,int size) {
        return service.get(null,search,status,severity,page,size);
    }
    private Page<AlertView> all() {return list(null,null,null,1,100);}
    private AlertView alert(int number) {return all().items().stream().filter(a->a.id().equals(id(0x800,number))).findFirst().orElseThrow();}
    private MockHttpSession login(String login) throws Exception {
        var initial=mvc.perform(get("/api/me")).andExpect(status().isUnauthorized()).andReturn();
        var session=(MockHttpSession)initial.getRequest().getSession(false);
        mvc.perform(post("/api/auth/login").session(session).header("Origin","http://localhost")
                .header("X-CSRF-Token",initial.getResponse().getHeader("X-CSRF-Token"))
                .contentType("application/json").content(json.writeValueAsString(Map.of("login",login,"password",password))))
                .andExpect(status().isOk());
        return session;
    }
    @Test void exactPublicPageContractContainsFullGroupsAndStringClockRevision() throws Exception {
        jdbc.update("update scenario_state set revision=9007199254740993");
        var response=mvc.perform(get("/api/alerts").session(login("repo-dispatcher"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(10)).andExpect(jsonPath("$.page").value(1)).andExpect(jsonPath("$.page_size").value(10))
                .andExpect(jsonPath("$.clock.revision").value("9007199254740993"))
                .andExpect(jsonPath("$.clock.now").value("2099-11-09T09:00:10Z")).andReturn();
        var root=json.readTree(response.getResponse().getContentAsString());
        assertThat(root.size()).isEqualTo(5);assertThat(root.get("items").size()).isEqualTo(10);
        for(var item:root.get("items")) {
            assertThat(item.size()).isEqualTo(21);assertThat(item.has("resolution_reason")).isTrue();
            for(var entry:item.get("orders")) assertThat(entry.size()).isEqualTo(3);
        }
        var first=root.get("items").get(0);
        assertThat(first.get("resolution_reason").isNull()).isTrue();
        assertThat(first.get("resolution").get("reason").asString()).isEmpty();
        assertThat(first.get("resolution").get("actor").get("id").isNull()).isTrue();
        assertThat(first.get("resolution").get("actor").get("role").asString()).isEqualTo("system");
        assertThat(response.getResponse().getContentAsString()).doesNotContain(hash,password,"password_hash");
    }
    @Test void newHeadersFirstAndEqualTimeIdsAscendingAreStableAcrossPages() {
        assertThat(all().items()).extracting(AlertView::id).containsExactly(id(0x800,11),id(0x800,10),id(0x800,9),id(0x800,8),
                id(0x800,7),id(0x800,6),id(0x800,5),id(0x800,1),id(0x800,3),id(0x800,2));
        assertThat(list(null,null,null,8,1).items()).extracting(AlertView::id).containsExactly(id(0x800,1));
        assertThat(list(null,null,null,9,1).items()).extracting(AlertView::id).containsExactly(id(0x800,3));
        assertThat(all().items()).isEqualTo(all().items());
    }
    @Test void optionalStatusAndSeverityFiltersApplyBeforePagination() {
        assertThat(list(null,AlertStatus.OPEN,null,1,100).total()).isEqualTo(7);
        assertThat(list(null,AlertStatus.RESOLVED,null,1,100).total()).isEqualTo(3);
        assertThat(list(null,null,AlertSeverity.CRITICAL,1,100).total()).isEqualTo(5);
        assertThat(list(null,null,AlertSeverity.WARNING,1,100).total()).isEqualTo(5);
        assertThat(list(null,AlertStatus.OPEN,AlertSeverity.CRITICAL,1,100).items()).extracting(AlertView::id)
                .containsExactly(id(0x800,9),id(0x800,8),id(0x800,5),id(0x800,1));
        assertThat(list(null,AlertStatus.RESOLVED,AlertSeverity.CRITICAL,1,100).items()).extracting(AlertView::id).containsExactly(id(0x800,6));
    }
    @Test void searchMatchesReasonCodeTextAndHistoricalCourierNameWithoutCaseSensitivity() {
        assertThat(list("courier_refused",null,null,1,100).items()).extracting(AlertView::id).containsExactly(id(0x800,9),id(0x800,6),id(0x800,5));
        assertThat(list("ГРУППА",null,null,1,100).items()).extracting(AlertView::id).containsExactly(id(0x800,6),id(0x800,5));
        assertThat(list("REPO COURIER A",null,null,1,100).items()).extracting(AlertView::id)
                .containsExactly(id(0x800,11),id(0x800,9),id(0x800,6),id(0x800,5),id(0x800,1));
        assertThat(list("repo courier b",null,null,1,100).items()).extracting(AlertView::id).containsExactly(id(0x800,7));
    }
    @Test void matchingOneOrderIncludesAllPendingAndResolvedMembersOfEveryMatchingGroup() {
        var result=list("schedule-5",null,null,1,100);
        assertThat(result.total()).isEqualTo(3);
        assertThat(result.items()).extracting(AlertView::id).containsExactly(id(0x800,6),id(0x800,5),id(0x800,3));
        var group=result.items().get(1);
        assertThat(group.orderIds()).containsExactly(id(0x300,1),id(0x300,4),id(0x300,5));
        assertThat(group.orders()).extracting(AlertView.OrderEntry::status).containsExactly("pending","pending","resolved");
        assertThat(group.orders().get(2).resolution().reason()).isEqualTo("Передано диспетчером");
        assertThat(group.impact()).containsExactly("Затронуто заявок: 3","Ожидают решения: 2");
    }
    @Test void multipleMatchingChildrenDoNotDuplicateTheHeaderOrInflateTotal() {
        var result=list("schedule-",null,null,1,2);
        assertThat(result.total()).isEqualTo(5);assertThat(result.items()).extracting(AlertView::id).containsExactly(id(0x800,9),id(0x800,7));
        assertThat(list("schedule-",null,null,2,2).items()).extracting(AlertView::id).containsExactly(id(0x800,6),id(0x800,5));
        assertThat(list("schedule-",null,null,3,2).items()).extracting(AlertView::id).containsExactly(id(0x800,3));
        assertThat(list("Группа: отказ",null,null,1,1).items().getFirst().orders()).hasSize(3);
    }
    @Test void orderClientNameAndAddressAreSearchableIncludingResolvedMembers() {
        jdbc.update("update service_order set client_name=?,address=? where id=?","Уникальный_КЛИЕНТ%!","Уникальный_АДРЕС%!",id(0x300,5));
        for(String term:List.of("уникальный_клиент%!","УНИКАЛЬНЫЙ_АДРЕС%!"))
            assertThat(list(term,null,null,1,100).items()).extracting(AlertView::id).containsExactly(id(0x800,6),id(0x800,5),id(0x800,3));
    }
    @Test void percentUnderscoreAndEscapeCharacterAreLiteralAndSqlIsParameterized() {
        for(String term:List.of("%","!","_%!","Поиск_","  Поиск_%!  "))
            assertThat(list(term,null,null,1,100).items()).as(term).extracting(AlertView::id).containsExactly(id(0x800,10));
        for(String term:List.of("ПоискX","!%","' OR 1=1 --","Заявки переданы","Отказ от заявок на день"))
            assertThat(list(term,null,null,1,100).items()).as(term).isEmpty();
        assertThat(list("   ",null,null,1,100).total()).isEqualTo(10);
    }
    @Test void allFiltersCombineBeforeTotalAndLimitOffset() {
        var result=list("Repo courier A",AlertStatus.OPEN,AlertSeverity.CRITICAL,2,1);
        assertThat(result.total()).isEqualTo(3);assertThat(result.items()).extracting(AlertView::id).containsExactly(id(0x800,5));
    }
    @Test void paginationDoesNotSplitGroupsAndSupportsLastEmptyAndVeryLargePages() {
        var first=list(null,null,null,1,3);assertThat(first.total()).isEqualTo(10);assertThat(first.items()).hasSize(3);
        assertThat(list(null,null,null,4,3).items()).extracting(AlertView::id).containsExactly(id(0x800,2));
        var empty=list(null,null,null,5,3);assertThat(empty.items()).isEmpty();assertThat(empty.total()).isEqualTo(10);
        assertThat(list(null,null,null,Integer.MAX_VALUE,100).items()).isEmpty();
        var group=list("Группа: отказ",null,null,1,1);assertThat(group.total()).isEqualTo(1);assertThat(group.items()).hasSize(1);
        assertThat(group.items().getFirst().orders()).hasSize(3);
    }
    @Test void actionsDependOnPendingEligibleOrdersAndHeaderStatus() {
        for(int n:List.of(1,3,5)) assertThat(alert(n).availableActions()).containsExactly("REASSIGN","AUTO_REASSIGN");
        for(int n:List.of(2,6,7,8,9,10,11)) assertThat(alert(n).availableActions()).isEmpty();
        assertThat(alert(5).title()).isEqualTo("Отказ от заявок на день");
        assertThat(alert(7).title()).isEqualTo("Задержка встречи");
        assertThat(alert(8).status()).isEqualTo("open");assertThat(alert(8).orders().getFirst().status()).isEqualTo("resolved");
    }
    @Test void generalAndIndividualResolutionsRemainSeparate() {
        var group=alert(6);assertThat(group.status()).isEqualTo("resolved");assertThat(group.resolution().reason()).isEqualTo("Заявки переданы");
        assertThat(group.orders().get(0).resolution().reason()).isEqualTo("Передано автоматически");
        assertThat(group.orders().get(0).resolution().actor().id()).isNull();
        assertThat(group.orders().get(1).resolution().actor().id()).isEqualTo(id(0x600,1));
        assertThat(alert(5).resolution()).isNull();assertThat(alert(11).resolutionReason()).isNull();
        assertThat(alert(11).resolution().reason()).isEmpty();assertThat(alert(9).reasonText()).isEmpty();
    }
    @Test void alertViewsMatchDispatcherOrderDetailsIncludingWholeGroups() {
        for(var alert:all().items()) for(var order:alert.orderIds())
            assertThat(orders.detail(order,DATE,"repo-dispatcher").alerts()).contains(alert);
    }
    @Test void openListMatchesWorkdayCourierCountersAndMapWithoutAttributingNullCouriers() {
        var open=list(null,AlertStatus.OPEN,null,1,100);var summary=workday.get(DATE);var mapView=map.get(DATE);
        assertThat(open.total()).isEqualTo(summary.counts().openAlerts());
        assertThat(mapView.alerts()).hasSize(open.total());
        for(var projected:mapView.alerts()) {
            var full=open.items().stream().filter(a->a.id().equals(projected.id())).findFirst().orElseThrow();
            assertThat(projected.courierId()).isEqualTo(full.courierId());assertThat(projected.orderIds()).isEqualTo(full.orderIds());
        }
        for(var courier:couriers.get(DATE,null,null,null,null,1,100).items())
            assertThat(courier.openAlertCount()).isEqualTo(open.items().stream().filter(a->courier.id().equals(a.courierId())).count());
        assertThat(open.items().stream().filter(a->a.courierId()==null).count()).isEqualTo(3);
        assertThat(open.clock()).isEqualTo(summary.clock()).isEqualTo(mapView.clock());
    }
    @Test void reassignmentDoesNotTransferAlertOwnershipOrCourierCounters() {
        jdbc.update("insert into assignment(order_id,plan_version,courier_id,route_id,source_order_version,reason_code,reason_text,decided_by,created_at) values(?,?,?,?,2,'OPTIMIZED','Переназначено','SYSTEM','2099-11-09T09:02:00Z')",id(0x300,1),PLAN+1,id(0x200,2),id(0x500,2));
        jdbc.update("update business_event set payload=jsonb_set(payload,'{order_ids}',(payload->'order_ids') || to_jsonb(cast(? as text))) where id=?",id(0x300,1).toString(),id(0x700,2));
        assertThat(orders.detail(id(0x300,1),DATE,"repo-dispatcher").order().assignment().courierId()).isEqualTo(id(0x200,2));
        assertThat(alert(1).courierId()).isEqualTo(id(0x200,1));
        assertThat(list("Repo courier B",null,null,1,100).items()).extracting(AlertView::id).containsExactly(id(0x800,7));
        var people=couriers.get(DATE,null,null,null,null,1,100).items();
        assertThat(people.stream().filter(c->c.id().equals(id(0x200,1))).findFirst().orElseThrow().openAlertCount()).isEqualTo(3);
        assertThat(people.stream().filter(c->c.id().equals(id(0x200,2))).findFirst().orElseThrow().openAlertCount()).isEqualTo(1);
    }
    @Test void explicitOtherDateUsesHeaderDateAndSuppressesActions() {
        var result=service.get(DATE.plusDays(1),null,null,null,1,10);
        assertThat(result.items()).extracting(AlertView::id).containsExactly(id(0x800,4));
        assertThat(result.items().getFirst().availableActions()).isEmpty();assertThat(result.clock()).isEqualTo(all().clock());
        assertThat(result.items().getFirst()).isIn(orders.detail(id(0x300,7),DATE.plusDays(1),"repo-dispatcher").alerts());
    }
    @Test void securityInvalidParametersAndMissingScenarioUseRealSessions() throws Exception {
        mvc.perform(get("/api/alerts")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/alerts").session(login("repo-representative"))).andExpect(status().isForbidden());
        var session=login("repo-dispatcher");mvc.perform(get("/api/alerts").session(session)).andExpect(status().isOk());
        for(var param:List.of(Map.entry("status","pending"),Map.entry("severity","urgent"),Map.entry("page_size","101"),Map.entry("date","2099-02-30")))
            mvc.perform(get("/api/alerts").param(param.getKey(),param.getValue()).session(session)).andExpect(status().isBadRequest());
        scenarios.deleteAll();scenarios.flush();
        mvc.perform(get("/api/alerts").session(session)).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("SCENARIO_NOT_INITIALIZED"));
        assertThat(scenarios.count()).isZero();
    }
    @Test @Sql("/empty-workday-fixture.sql") void emptyScenarioReturnsAnEmptyPage() {
        var result=all();assertThat(result.items()).isEmpty();assertThat(result.total()).isZero();assertThat(result.clock()).isNotNull();
    }
    @Test void allGetsLeaveEveryBusinessTableUnchanged() {
        var before=digests();all();list("schedule-5",AlertStatus.OPEN,null,1,1);service.get(DATE.plusDays(1),null,null,null,1,10);
        entityManager.flush();assertThat(digests()).isEqualTo(before);
    }
    @Test void growingHeadersAndGroupMembersKeepsConstantQueryCount() {
        var stats=entityManagerFactory.unwrap(SessionFactory.class).getStatistics();boolean enabled=stats.isStatisticsEnabled();stats.setStatisticsEnabled(true);
        try {
            entityManager.clear();stats.clear();all();long baseline=stats.getPrepareStatementCount();
            jdbc.update("""
                    insert into service_order(id,number,client_name,address,product,lat,lon,window_from,window_to,service_duration_s,status,service_date,version)
                    select md5('alert-order-'||n)::uuid,'alert-bulk-'||n,'Bulk client','Bulk address','Card',55,82,
                        '2099-11-09T09:00:00Z','2099-11-09T18:00:00Z',600,'NEW','2099-11-09',1 from generate_series(1,12) n
                    """);
            jdbc.update("""
                    insert into alert(id,source_event_id,service_date,kind,scope,severity,status,reason_code,reason_text,created_at,deadline_at)
                    select md5('alert-header-'||n)::uuid,?,'2099-11-09','refusal','courier_day','critical','open','BULK_TEST','Массовая группа',
                        timestamptz '2099-11-09T09:20:00Z'+n*interval '1 second','2099-11-09T09:30:00Z' from generate_series(1,80) n
                    """,id(0x700,3));
            jdbc.update("""
                    insert into alert_order(alert_id,order_id,status)
                    select md5('alert-header-'||n)::uuid,md5('alert-order-'||m)::uuid,'pending'
                    from generate_series(1,80) n cross join generate_series(1,12) m
                    """);
            entityManager.clear();stats.clear();var result=all();
            assertThat(result.total()).isEqualTo(90);assertThat(result.items()).hasSize(90);
            assertThat(result.items().getFirst().orders()).hasSize(12);
            assertThat(stats.getPrepareStatementCount()).isEqualTo(baseline).isEqualTo(4);
            System.out.printf("Alert SQL queries: %d -> %d for 10 -> 90 headers and 960 additional group members.%n",baseline,stats.getPrepareStatementCount());
        } finally {stats.setStatisticsEnabled(enabled);}
    }
    private Map<String,String> digests() {
        var result=new LinkedHashMap<String,String>();
        for(String table:List.of("office","courier","service_order","planning_run","route","route_stop","assignment","app_user","business_event","alert","alert_order","api_command","scenario_state"))
            result.put(table,jdbc.queryForObject("select md5(coalesce(string_agg(row_to_json(t)::text,',' order by row_to_json(t)::text),'')) from "+table+" t",String.class));
        return result;
    }
}

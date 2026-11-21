package com.routeroad;

import com.routeroad.orders.application.OrderQueryService;
import com.routeroad.orders.api.OrderView;
import com.routeroad.couriers.api.CourierView;
import com.routeroad.orders.domain.ServiceOrderStatus;
import com.routeroad.shared.api.Actor;
import com.routeroad.shared.api.Page;
import com.routeroad.simulation.infrastructure.ScenarioStateRepository;
import com.routeroad.workday.application.WorkdayService;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@EnabledIfEnvironmentVariable(named = "ROUTEROAD_REPOSITORY_TESTS", matches = "true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Transactional
@Rollback
@Sql({"/repository-fixtures.sql", "/workday-fixtures.sql", "/courier-fixtures.sql"})
class CouriersPostgresTests {
    private static final LocalDate DATE = LocalDate.of(2099, 11, 9);
    private static final long PLAN = 9_000_000_000_000_000_001L;
    private final String password = UUID.randomUUID().toString();
    private String hash;
    @Autowired OrderQueryService orders;
    @Autowired com.routeroad.couriers.application.CourierQueryService service;
    @Autowired WorkdayService workday;
    @Autowired MockMvc mvc;
    @Autowired JsonMapper json;
    @Autowired PasswordEncoder passwords;
    @Autowired ScenarioStateRepository scenarios;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean java.time.Clock realClock;
    private static UUID id(int group, int number) {
        return UUID.fromString("a0f8d%03x-0000-4000-8000-%012x".formatted(group, number));
    }
    @BeforeAll void requireEmptyScenario() {
        assumeTrue(scenarios.count() == 0, "Existing scenario must be preserved");
        hash = passwords.encode(password);
    }
    @BeforeEach void setup() {
        when(realClock.instant()).thenReturn(Instant.parse("2099-11-09T09:00:10Z"));
        jdbc.update("update app_user set password_hash = ? where login in ('repo-dispatcher','repo-representative','workday-empty-dispatcher')", hash);
    }
    private MockHttpSession login(String login) throws Exception {
        var initial = mvc.perform(get("/api/me")).andExpect(status().isUnauthorized()).andReturn();
        var session = (MockHttpSession) initial.getRequest().getSession(false);
        mvc.perform(post("/api/auth/login").session(session).header("Origin", "http://localhost")
                .header("X-CSRF-Token", initial.getResponse().getHeader("X-CSRF-Token"))
                .contentType("application/json").content(json.writeValueAsString(Map.of("login", login, "password", password))))
                .andExpect(status().isOk());
        return session;
    }
    private Page<CourierView> list(LocalDate date, String search, Boolean available, Boolean alerts, Boolean workload, int page, int size) {
        return service.get(date,search,available,alerts,workload,page,size);
    }
    private Page<CourierView> all() { return list(null,null,null,null,null,1,100); }
    private CourierView courier(int n) { return all().items().stream().filter(c->c.id().equals(id(0x200,n))).findFirst().orElseThrow(); }
    private void activateSecondDecision(boolean assigned) {
        jdbc.update("insert into assignment(order_id,plan_version,courier_id,route_id,source_order_version,reason_code,reason_text,decided_by,created_at) values(?,?,?,?,1,'OPTIMIZED','Test decision','SYSTEM','2099-11-09T09:02:00Z')",
                id(0x300,1),PLAN+1,assigned?id(0x200,3):null,assigned?id(0x500,4):null);
        jdbc.update("update business_event set payload=jsonb_set(payload,'{order_ids}',(payload->'order_ids') || to_jsonb(cast(? as text))) where id=?", id(0x300,1).toString(),id(0x700,2));
    }
    @Test void completeHttpContractAndPositionAndStringRevision() throws Exception {
        jdbc.update("update scenario_state set revision=9007199254740993");
        var result=mvc.perform(get("/api/couriers").session(login("repo-dispatcher"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(5)).andExpect(jsonPath("$.page_size").value(10))
                .andExpect(jsonPath("$.clock.revision").value("9007199254740993"))
                .andExpect(jsonPath("$.items[0].total_orders").value(3)).andExpect(jsonPath("$.items[0].open_alert_count").value(2))
                .andExpect(jsonPath("$.items[0].available_actions").isEmpty())
                .andExpect(jsonPath("$.items[2].position.lat").value(55.123))
                .andExpect(jsonPath("$.items[2].position.lon").value(82.456))
                .andExpect(jsonPath("$.items[2].position.at").value("2099-11-09T09:00:00Z"))
                .andExpect(jsonPath("$.items[3].position").isEmpty()).andReturn();
        var body=json.readTree(result.getResponse().getContentAsString());
        assertThat(body.size()).isEqualTo(5);
        for(var row:body.get("items")) assertThat(row.size()).isEqualTo(11);
        assertThat(body.get("items").get(2).get("office_id").asString()).isEqualTo(id(0x100,1).toString());
        assertThat(body.get("clock").get("revision").isString()).isTrue();
        assertThat(result.getResponse().getContentAsString()).doesNotContain("password",hash);
    }
    @Test void partialPlansUnconfirmedPlansAndTransfersUseSharedAssignmentSnapshot() {
        assertThat(courier(1).totalOrders()).isEqualTo(3);
        assertThat(courier(2).totalOrders()).isEqualTo(1);
        assertThat(courier(2).completedOrders()).isEqualTo(1);
        assertThat(courier(3).totalOrders()).isEqualTo(1);
        assertThat(courier(4).totalOrders()).isEqualTo(1);
        assertThat(courier(5).totalOrders()).isZero();
        assertThat(orders.get(DATE,null,null,id(0x200,1),true,1,100).items()).extracting(OrderView::id)
                .containsExactlyInAnyOrder(id(0x300,1),id(0x300,9),id(0x300,10));
    }
    @Test void workloadOnlyUsesFourUnfinishedStatuses() {
        assertThat(list(DATE,null,null,null,true,1,100).items()).extracting(CourierView::id).containsExactly(id(0x200,1));
        assertThat(list(DATE,null,null,null,false,1,100).total()).isEqualTo(4);
        // NEW and UNASSIGNED retain real confirmed assignments but do not create workload.
        assertThat(courier(3).totalOrders()).isEqualTo(1); assertThat(courier(4).totalOrders()).isEqualTo(1);
        jdbc.update("update service_order set status='ASSIGNED' where id=?",id(0x300,11));
        assertThat(list(DATE,null,null,null,true,1,100).items()).extracting(CourierView::id).containsExactly(id(0x200,1),id(0x200,3));
    }
    @Test void completedAndCancelledAssignmentsRemainInTotalsWithoutWorkload() {
        jdbc.update("update service_order set status='COMPLETED' where id=?",id(0x300,1));
        jdbc.update("update service_order set status='CANCELLED' where id=?",id(0x300,10));
        assertThat(courier(1).totalOrders()).isEqualTo(3); assertThat(courier(1).completedOrders()).isEqualTo(1);
        assertThat(list(DATE,null,null,null,true,1,100).items()).isEmpty();
    }
    @Test void availabilityAndWorkloadAreIndependentAfterDayRefusal() {
        jdbc.update("update courier set is_available=false where id=?",id(0x200,1));
        jdbc.update("insert into business_event(id,type,service_date,courier_id,occurred_at,created_at,reason,payload) values(?,'COURIER_REFUSED',?,?,'2099-11-09T09:03:00Z','2099-11-09T09:03:00Z','Refused','{}')",id(0x700,10),DATE,id(0x200,1));
        var result=list(DATE,null,false,null,true,1,100);
        assertThat(result.items()).extracting(CourierView::id).containsExactly(id(0x200,1));
        assertThat(result.items().getFirst().totalOrders()).isEqualTo(3);
        assertThat(result.items().getFirst().available()).isFalse();
    }
    @Test void alertHeadersAreCountedOnceOnlyForTheirCourierAndDay() {
        assertThat(courier(1).openAlertCount()).isEqualTo(2); // includes one group with two items
        assertThat(courier(2).openAlertCount()).isEqualTo(1);
        assertThat(courier(3).openAlertCount()).isZero(); // its alert belongs to tomorrow
        assertThat(list(DATE,null,null,true,null,1,100).total()).isEqualTo(2);
        assertThat(list(DATE,null,null,false,null,1,100).total()).isEqualTo(3);
    }
    @Test void reassignmentDoesNotMoveAlertAttribution() {
        activateSecondDecision(true);
        assertThat(courier(1).totalOrders()).isEqualTo(2); assertThat(courier(3).totalOrders()).isEqualTo(2);
        assertThat(courier(1).openAlertCount()).isEqualTo(2); assertThat(courier(3).openAlertCount()).isZero();
        assertThat(list(DATE,null,null,null,true,1,100).total()).isEqualTo(2);
    }
    @Test void laterActivationWithSameSourceVersionAndOccurredAtCanRemoveAssignment() {
        jdbc.update("update service_order set status='COMPLETED' where id=?",id(0x300,10));
        activateSecondDecision(false);
        jdbc.update("update business_event set occurred_at='2099-11-09T09:00:00Z' where type='PLAN_ACTIVATED'");
        assertThat(courier(1).totalOrders()).isEqualTo(2); assertThat(courier(1).completedOrders()).isEqualTo(1);
        assertThat(list(DATE,null,null,null,true,1,100).items()).isEmpty();
        assertThat(workday.get(DATE).counts().ordersAssigned()).isEqualTo(5);
        assertThat(orders.get(DATE,null,null,null,true,1,100).total()).isEqualTo(5);
    }
    @Test void searchIsCaseInsensitiveLiteralAndSqlBound() {
        assertThat(list(DATE,"REPO COURIER A",null,null,null,1,100).items()).extracting(CourierView::id).containsExactly(id(0x200,1));
        for(String search:List.of("иван","ТЕСТ","_","%","!"))
            assertThat(list(DATE,search,null,null,null,1,100).items()).extracting(CourierView::id).containsExactly(id(0x200,5));
        assertThat(list(DATE,"' OR 1=1 --",null,null,null,1,100).total()).isZero();
        assertThat(list(DATE,"   ",null,null,null,1,100).total()).isEqualTo(5);
    }
    @Test void filtersCombineBeforePaginationAndKeepFullTotal() {
        assertThat(list(DATE,null,true,null,null,1,1).total()).isEqualTo(3);
        assertThat(list(DATE,null,false,null,null,1,100).total()).isEqualTo(2);
        var result=list(DATE,null,false,true,false,1,1);
        assertThat(result.total()).isEqualTo(1); assertThat(result.items()).extracting(CourierView::id).containsExactly(id(0x200,2));
        assertThat(list(DATE,null,true,false,true,1,100).total()).isZero();
    }
    @Test void pagesAreStableWithUniqueTieBreakerAndRetainTotalBeyondEnd() {
        var first=list(DATE,"same",null,null,null,1,1);
        var second=list(DATE,"same",null,null,null,2,1);
        assertThat(first.total()).isEqualTo(2);
        assertThat(first.items()).extracting(CourierView::id).containsExactly(id(0x200,3));
        assertThat(second.items()).extracting(CourierView::id).containsExactly(id(0x200,4));
        assertThat(list(DATE,"same",null,null,null,2,1).items()).isEqualTo(second.items());
        var beyond=list(DATE,"same",null,null,null,Integer.MAX_VALUE,100);
        assertThat(beyond.items()).isEmpty(); assertThat(beyond.total()).isEqualTo(2);
    }
    @Test void explicitDateChangesCountersButNotCurrentScenarioAvailabilityOrClock() {
        var tomorrow=list(DATE.plusDays(1),null,null,null,null,1,100);
        assertThat(tomorrow.total()).isEqualTo(5);
        assertThat(tomorrow.items()).allSatisfy(c->{assertThat(c.totalOrders()).isZero();assertThat(c.completedOrders()).isZero();});
        assertThat(tomorrow.items().get(2).openAlertCount()).isEqualTo(1);
        assertThat(tomorrow.clock()).isEqualTo(all().clock());
        assertThat(list(DATE.plusDays(1),null,null,null,true,1,100).total()).isZero();
        assertThat(list(DATE.plusDays(1),null,true,null,null,1,100).total()).isEqualTo(3);
    }
    @Test void countersAgreeWithOrdersAndWorkdayOnTheSameSnapshot() {
        var result=all();var counts=workday.get(DATE).counts();
        assertThat(result.total()).isEqualTo(counts.couriersTotal());
        assertThat(result.items().stream().mapToInt(CourierView::totalOrders).sum()).isEqualTo(counts.ordersAssigned()).isEqualTo(6);
        assertThat(result.items().stream().mapToInt(CourierView::completedOrders).sum()).isEqualTo(counts.ordersCompleted()).isEqualTo(1);
        for(var courier:result.items()) {
            assertThat(orders.get(DATE,null,null,courier.id(),true,1,100).total()).isEqualTo(courier.totalOrders());
            assertThat(orders.get(DATE,null,ServiceOrderStatus.COMPLETED,courier.id(),true,1,100).total()).isEqualTo(courier.completedOrders());
        }
        int unattributed=jdbc.queryForObject("select count(*) from alert where status='open' and service_date=? and courier_id is null",Integer.class,DATE);
        assertThat(result.items().stream().mapToInt(CourierView::openAlertCount).sum()+unattributed).isEqualTo(counts.openAlerts());
    }
    @Test void realSecurityAndInvalidParameters() throws Exception {
        mvc.perform(get("/api/couriers")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/couriers").session(login("repo-representative"))).andExpect(status().isForbidden());
        var session=login("repo-dispatcher");
        for(var param:List.of(Map.entry("date","2099-02-30"),Map.entry("workload","unknown"),Map.entry("page","0"),
                Map.entry("page_size","101"),Map.entry("is_available","yes"),Map.entry("has_open_alerts","1")))
            mvc.perform(get("/api/couriers").param(param.getKey(),param.getValue()).session(session)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
    }
    @Test void missingScenarioIsConflictAndDoesNotCreateState() throws Exception {
        scenarios.deleteAll();scenarios.flush();
        mvc.perform(get("/api/couriers").session(login("repo-dispatcher"))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("SCENARIO_NOT_INITIALIZED"));
        assertThat(scenarios.count()).isZero();
    }
    @Test @Sql("/empty-workday-fixture.sql") void emptyExistingScenarioHasEmptyPage() throws Exception {
        mvc.perform(get("/api/couriers").session(login("workday-empty-dispatcher"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty()).andExpect(jsonPath("$.total").value(0));
    }
    @Test void readsDoNotModifyAnyBusinessTable() {
        var before=digests();all();orders.get(DATE,null,null,null,null,1,100);workday.get(DATE);
        assertThat(digests()).isEqualTo(before);
    }
    private Map<String,String> digests() {
        var result=new java.util.LinkedHashMap<String,String>();
        for(String table:List.of("office","courier","service_order","planning_run","route","route_stop","assignment","app_user","business_event","alert","alert_order","api_command","scenario_state"))
            result.put(table,jdbc.queryForObject("select md5(coalesce(string_agg(row_to_json(t)::text,',' order by row_to_json(t)::text),'')) from "+table+" t",String.class));
        return result;
    }
}

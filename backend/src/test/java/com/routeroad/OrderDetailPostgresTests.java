package com.routeroad;

import com.routeroad.orders.application.OrderQueryService;
import com.routeroad.orders.api.OrderView;
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
@Sql({"/repository-fixtures.sql", "/workday-fixtures.sql", "/order-detail-fixtures.sql"})
class OrderDetailPostgresTests {
    private static final LocalDate DATE = LocalDate.of(2099, 11, 9);
    private static final long PLAN = 9_000_000_000_000_000_001L;
    private final String password = UUID.randomUUID().toString();
    private String hash;
    @Autowired OrderQueryService service;
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
        jdbc.update("update app_user set password_hash = ? where login in ('repo-dispatcher','repo-representative','detail-representative-b','workday-empty-dispatcher')", hash);
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
    private Page<OrderView> list(LocalDate date, String search, ServiceOrderStatus status, UUID courier, Boolean assigned, int page, int size) {
        return service.get(date, search, status, courier, assigned, page, size);
    }
    private Page<OrderView> all() { return list(null, null, null, null, null, 1, 100); }
    private OrderView order(int number) { return all().items().stream().filter(o -> o.id().equals(id(0x300, number))).findFirst().orElseThrow(); }
    private void confirmSecondPlanForFirstOrder(int sourceVersion) {
        jdbc.update("insert into assignment(order_id,plan_version,source_order_version,reason_code,reason_text,decided_by,created_at) "
                + "values(?,?,?,'NO_CAPACITY','Нет вместимости','SYSTEM','2099-11-09T09:02:00Z')", id(0x300, 1), PLAN + 1, sourceVersion);
        jdbc.update("update business_event set payload=jsonb_set(payload,'{order_ids}',(payload->'order_ids') || to_jsonb(cast(? as text))) where id=?",
                id(0x300, 1).toString(), id(0x700, 2));
    }
    private tools.jackson.databind.JsonNode readCard(int number, String login, String date) throws Exception {
        var request=get("/api/orders/"+id(0x300,number)).session(login(login));
        if(date!=null) request.param("date",date);
        return json.readTree(mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
    private com.routeroad.orders.api.OrderDetailView detail(int number,String login,LocalDate date) {
        return service.detail(id(0x300,number),date,login);
    }
    private void reassign(int number,long plan,int courier) {
        jdbc.update("insert into assignment(order_id,plan_version,courier_id,route_id,source_order_version,reason_code,reason_text,decided_by,created_at) "
                + "values(?,?,?,?,2,'OPTIMIZED','Reassigned','SYSTEM','2099-11-09T09:02:00Z')",
                id(0x300,number),plan,id(0x200,courier),id(0x500,courier));
        jdbc.update("update business_event set payload=jsonb_set(payload,'{order_ids}',(payload->'order_ids') || to_jsonb(cast(? as text))) "
                + "where type='PLAN_ACTIVATED' and payload->>'plan_version'=?",id(0x300,number).toString(),Long.toString(plan));
    }
    private void dayRefusal() {
        jdbc.update("insert into business_event(id,type,service_date,courier_id,occurred_at,created_at,reason,payload) "
                + "values(?,'COURIER_REFUSED',?,?, '2099-11-09T09:05:00Z','2099-11-09T09:05:00Z','Day refusal','{}')",
                id(0x700,30),DATE,id(0x200,1));
        jdbc.update("update courier set is_available=false where id=?",id(0x200,1));
    }
    @Test void dispatcherGetsCompleteContractWithSeveralAlertsAndBigVersions() throws Exception {
        long large=9_007_199_254_740_993L;
        jdbc.update("update service_order set version=? where id=?",large,id(0x300,9));
        jdbc.update("update assignment set source_order_version=? where order_id=?",large,id(0x300,9));
        jdbc.update("update scenario_state set revision=?",large);
        var body=readCard(9,"repo-dispatcher",null);
        assertThat(body.size()).isEqualTo(3);
        assertThat(body.get("order").size()).isEqualTo(17);
        assertThat(body.get("order").get("version").asString()).isEqualTo("9007199254740993");
        assertThat(body.get("order").get("assignment").get("plan_version").asString()).isEqualTo(Long.toString(PLAN));
        assertThat(body.get("order").get("assignment").get("source_order_version").asString()).isEqualTo("9007199254740993");
        assertThat(body.get("clock").get("revision").isString()).isTrue();
        assertThat(body.get("alerts").size()).isEqualTo(3);
        for(var alert:body.get("alerts")) {
            assertThat(alert.size()).isEqualTo(21);
            assertThat(alert.has("source_event_id")).isTrue();assertThat(alert.has("resolution_reason")).isTrue();
            assertThat(alert.has("title")).isTrue();assertThat(alert.has("impact")).isTrue();
            assertThat(alert.has("orders")).isTrue();assertThat(alert.has("available_actions")).isTrue();
        }
        assertThat(body.toString()).doesNotContain("password_hash",hash);
    }
    @Test void directIdLookupIsNotLimitedToTheFirstPageAndMatchesList() throws Exception {
        var first=service.get(DATE,null,null,null,null,1,1);
        assertThat(first.items()).extracting(OrderView::id).doesNotContain(id(0x300,8));
        var detail=detail(8,"repo-dispatcher",DATE);
        var fromList=service.get(DATE,null,null,null,null,1,100).items().stream().filter(o->o.id().equals(id(0x300,8))).findFirst().orElseThrow();
        assertThat(detail.order()).isEqualTo(fromList);
        assertThat(readCard(8,"repo-dispatcher",null).get("order").get("id").asString()).isEqualTo(id(0x300,8).toString());
    }
    @Test void representativeOwnOrderShowsOnlySingleCourierAlert() throws Exception {
        var body=readCard(9,"repo-representative",null);
        assertThat(body.get("order").get("available_actions").toString()).isEqualTo("[\"EN_ROUTE\",\"REPORT_DELAY\",\"REFUSE_ORDER\"]");
        assertThat(body.get("alerts").size()).isEqualTo(1);
        var alert=body.get("alerts").get(0);
        assertThat(alert.get("id").asString()).isEqualTo(id(0x800,4).toString());
        assertThat(alert.get("order_ids").size()).isEqualTo(1);
        assertThat(alert.get("orders").size()).isEqualTo(1);
        assertThat(alert.get("order_ids").get(0).asString()).isEqualTo(id(0x300,9).toString());
        assertThat(alert.get("available_actions").size()).isZero();
        assertThat(body.toString()).doesNotContain("PRIVATE-OTHER","PRIVATE-FOREIGN",id(0x300,8).toString());
    }
    @Test void dispatcherSeesAllGroupMembersAndTheirIndividualResolutions() {
        var group=detail(9,"repo-dispatcher",DATE).alerts().stream().filter(a->a.scope().equals("courier_day")).findFirst().orElseThrow();
        assertThat(group.orderIds()).containsExactly(id(0x300,8),id(0x300,9));
        assertThat(group.orders().getFirst().status()).isEqualTo("resolved");
        assertThat(group.orders().getFirst().resolution().reason()).isEqualTo("PRIVATE-OTHER-RESOLUTION");
        assertThat(group.orders().get(1).resolution()).isNull();
        assertThat(group.availableActions()).containsExactly("REASSIGN","AUTO_REASSIGN");
        assertThat(group.impact()).contains("Затронуто заявок: 2","Ожидают решения: 1");
        assertThat(group.resolution()).isNull();
        assertThat(detail(9,"repo-dispatcher",DATE).order().availableActions()).containsExactly("REASSIGN");
    }
    @Test void resolvedAlertReturnsResolutionActorAndNoCommands() {
        var alert=detail(9,"repo-dispatcher",DATE).alerts().stream().filter(a->a.id().equals(id(0x800,5))).findFirst().orElseThrow();
        assertThat(alert.status()).isEqualTo("resolved");
        assertThat(alert.resolution().actor().id()).isEqualTo(id(0x600,1));
        assertThat(alert.resolution().actor().role()).isEqualTo(Actor.Role.DISPATCHER);
        assertThat(alert.resolution().reason()).isEqualTo("PRIVATE-FOREIGN-RESOLUTION");
        assertThat(alert.orders().getFirst().resolution()).isNotNull();
        assertThat(alert.availableActions()).isEmpty();
    }
    @Test void systemResolutionHandlesNullableStoredReasonWithoutInventingOne() {
        jdbc.update("update alert set status='resolved',resolved_at='2099-11-09T09:05:00Z' where id=?",id(0x800,4));
        jdbc.update("update alert_order set status='resolved',resolved_at='2099-11-09T09:05:00Z' where alert_id=?",id(0x800,4));
        var alert=detail(9,"repo-representative",DATE).alerts().getFirst();
        assertThat(alert.resolution().actor()).isEqualTo(new Actor(null,"Система",Actor.Role.SYSTEM));
        assertThat(alert.resolution().reason()).isEmpty();
        assertThat(alert.resolutionReason()).isNull();
    }
    @Test void groupLikeMalformedSingleAlertCannotLeakOtherMembers() {
        jdbc.update("update alert set scope='order' where id=?",id(0x800,3));
        var detail=detail(9,"repo-representative",DATE);
        assertThat(detail.alerts()).extracting(a->a.id()).containsExactly(id(0x800,4));
    }
    @Test void refusalKeepsReadAccessWhileBlockingFurtherActions() throws Exception {
        var body=readCard(1,"repo-representative",null);
        assertThat(body.get("order").get("assignment").get("courier_id").asString()).isEqualTo(id(0x200,1).toString());
        assertThat(body.get("order").get("available_actions").size()).isZero();
        assertThat(body.get("alerts").size()).isEqualTo(1);
    }
    @Test void representativeTransitionGraphIsAppliedToTheCurrentOrder() throws Exception {
        var expectations=Map.of("ASSIGNED",List.of("EN_ROUTE","REPORT_DELAY","REFUSE_ORDER"),
                "EN_ROUTE",List.of("ARRIVED","REPORT_DELAY","REFUSE_ORDER"),
                "ARRIVED",List.of("IN_PROGRESS","REPORT_DELAY","REFUSE_ORDER"),
                "IN_PROGRESS",List.of("COMPLETED","REPORT_DELAY"),
                "COMPLETED",List.<String>of(),"CANCELLED",List.<String>of());
        for(var entry:expectations.entrySet()) {
            jdbc.update("update service_order set status=? where id=?",entry.getKey(),id(0x300,9));
            assertThat(detail(9,"repo-representative",DATE).order().availableActions()).isEqualTo(entry.getValue());
        }
        jdbc.update("update service_order set status='CANCELLED' where id=?",id(0x300,9));
        assertThat(readCard(9,"repo-representative",null).get("order").get("status").asString()).isEqualTo("CANCELLED");
    }
    @Test void dayRefusalPreservesCompletionOfStartedMeeting() throws Exception {
        dayRefusal();
        assertThat(detail(9,"repo-representative",DATE).order().availableActions()).isEmpty();
        jdbc.update("update service_order set status='IN_PROGRESS' where id=?",id(0x300,9));
        var body=readCard(9,"repo-representative",null);
        assertThat(body.get("order").get("available_actions").toString()).isEqualTo("[\"COMPLETED\",\"REPORT_DELAY\"]");
    }
    @Test void formerExecutorIsForbiddenEvenWhenClientClaimsHisCourierId() throws Exception {
        var session=login("repo-representative");
        reassign(9,PLAN+1,2);
        var result=mvc.perform(get("/api/orders/"+id(0x300,9)).param("courier_id",id(0x200,1).toString()).session(session))
                .andExpect(status().isForbidden()).andReturn();
        assertThat(result.getResponse().getContentAsString()).doesNotContain("Detail client","PRIVATE-",id(0x800,3).toString());
        assertThat(readCard(9,"detail-representative-b",null).get("order").get("assignment").get("courier_id").asString()).isEqualTo(id(0x200,2).toString());
    }
    @Test void pythonPlanDoesNotChangeOwnershipBeforeActivation() throws Exception {
        reassign(9,PLAN+2,2); // no PLAN_ACTIVATED event for this run
        assertThat(readCard(9,"repo-representative",null).get("order").get("assignment").get("plan_version").asString()).isEqualTo(Long.toString(PLAN));
        mvc.perform(get("/api/orders/"+id(0x300,9)).session(login("detail-representative-b"))).andExpect(status().isForbidden());
    }
    @Test void partialRunAndNewUnassignedDecisionArePreserved() throws Exception {
        assertThat(detail(1,"repo-dispatcher",DATE).order().assignment().planVersion()).isEqualTo(PLAN);
        assertThat(detail(4,"repo-dispatcher",DATE).order().assignment().planVersion()).isEqualTo(PLAN+1);
        var noCourier=detail(7,"repo-dispatcher",DATE).order().assignment();
        assertThat(noCourier).isNotNull();assertThat(noCourier.courierId()).isNull();
        mvc.perform(get("/api/orders/"+id(0x300,7)).session(login("repo-representative"))).andExpect(status().isForbidden());
    }
    @Test void unassignedDecisionRevokesReadAccessEvenWhenOldRouteStaysActive() throws Exception {
        jdbc.update("insert into assignment(order_id,plan_version,source_order_version,reason_code,reason_text,decided_by,created_at) "
                + "values(?,?,1,'NO_CAPACITY','Нет вместимости','SYSTEM','2099-11-09T09:02:00Z')",id(0x300,9),PLAN+1);
        jdbc.update("update business_event set payload=jsonb_set(payload,'{order_ids}',(payload->'order_ids') || to_jsonb(cast(? as text))) where id=?",
                id(0x300,9).toString(),id(0x700,2));
        assertThat(detail(9,"repo-dispatcher",DATE).order().assignment().courierId()).isNull();
        mvc.perform(get("/api/orders/"+id(0x300,9)).session(login("repo-representative"))).andExpect(status().isForbidden());
    }
    @Test void newOrderWithoutAssignmentIsVisibleOnlyToDispatcher() throws Exception {
        var view=detail(3,"repo-dispatcher",DATE.plusDays(1)).order();
        assertThat(view.assignment()).isNull();
        mvc.perform(get("/api/orders/"+id(0x300,3)).param("date",DATE.plusDays(1).toString()).session(login("repo-representative")))
                .andExpect(status().isForbidden());
    }
    @Test void dispatcherSeesBothDatesOfTransferButRepresentativeCannotUseOldDate() throws Exception {
        var previous=detail(5,"repo-dispatcher",DATE).order();
        var current=detail(5,"repo-dispatcher",DATE.plusDays(1)).order();
        assertThat(previous.dayStatus()).isEqualTo(ServiceOrderStatus.RESCHEDULED);
        assertThat(previous.status()).isEqualTo(ServiceOrderStatus.NEW);
        assertThat(current.dayStatus()).isEqualTo(ServiceOrderStatus.NEW);
        mvc.perform(get("/api/orders/"+id(0x300,5)).param("date",DATE.toString()).session(login("repo-representative")))
                .andExpect(status().isForbidden());
    }
    @Test void representativeCannotReadHistoricalDateEvenWhileStillOwningTheOrder() throws Exception {
        jdbc.update("update service_order set service_date=? where id=?",DATE.plusDays(1),id(0x300,9));
        jdbc.update("insert into planning_run(plan_version,request_id,run_type,event_type,algorithm,plan_date,model_now,created_at,metrics) "
                + "values(?,?,'ASSIGN',null,'greedy',?,'2099-11-10T09:00:00Z','2099-11-10T09:00:00Z','{\"total_distance_m\":0,\"violated_windows\":0,\"unassigned_count\":0,\"solve_time_ms\":0}')",PLAN+3,UUID.randomUUID(),DATE.plusDays(1));
        jdbc.update("insert into route(id,courier_id,plan_date,plan_version,is_active,algorithm,total_distance_m,violated_windows,created_at) "
                + "values(?,?,?,?,true,'greedy',0,0,'2099-11-10T09:00:00Z')",id(0x500,4),id(0x200,1),DATE.plusDays(1),PLAN+3);
        jdbc.update("insert into assignment(order_id,plan_version,courier_id,route_id,source_order_version,reason_code,reason_text,decided_by,created_at) "
                + "values(?,?,?,?,1,'NEAREST','New day','SYSTEM','2099-11-10T09:00:00Z')",id(0x300,9),PLAN+3,id(0x200,1),id(0x500,4));
        jdbc.update("insert into business_event(id,type,service_date,occurred_at,created_at,reason,payload) "
                + "values(?,'PLAN_ACTIVATED',?,'2099-11-10T09:00:00Z','2099-11-10T09:00:00Z','New day',cast(? as jsonb))",id(0x700,31),DATE.plusDays(1),json.writeValueAsString(Map.of("plan_version",Long.toString(PLAN+3),"order_ids",List.of(id(0x300,9)))));
        var session=login("repo-representative");
        mvc.perform(get("/api/orders/"+id(0x300,9)).param("date",DATE.toString()).session(session)).andExpect(status().isForbidden());
        mvc.perform(get("/api/orders/"+id(0x300,9)).param("date",DATE.plusDays(1).toString()).session(session)).andExpect(status().isOk())
                .andExpect(jsonPath("$.order.available_actions").isEmpty());
    }
    @Test void anonymousMissingMalformedAndNoScenarioUseAgreedErrors() throws Exception {
        mvc.perform(get("/api/orders/"+id(0x300,9))).andExpect(status().isUnauthorized());
        var session=login("repo-dispatcher");
        mvc.perform(get("/api/orders/"+UUID.randomUUID()).session(session)).andExpect(status().isNotFound());
        mvc.perform(get("/api/orders/not-uuid").session(session)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/orders/"+id(0x300,9)).param("date","bad").session(session)).andExpect(status().isBadRequest());
        scenarios.deleteAll();scenarios.flush();
        mvc.perform(get("/api/orders/"+id(0x300,9)).session(session)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("SCENARIO_NOT_INITIALIZED"));
        assertThat(scenarios.count()).isZero();
    }
    @Test void getDoesNotChangeAnyBusinessRow() throws Exception {
        var tables=List.of("office","courier","service_order","planning_run","route","route_stop","assignment","app_user","business_event","alert","alert_order","api_command","scenario_state");
        var before=new java.util.LinkedHashMap<String,String>();
        for(var table:tables) before.put(table,jdbc.queryForObject("select md5(coalesce(string_agg(to_jsonb(t)::text,E'\\n' order by to_jsonb(t)::text),'')) from "+table+" t",String.class));
        readCard(9,"repo-dispatcher",null);readCard(9,"repo-representative",null);readCard(1,"repo-representative",null);
        for(var table:tables) assertThat(jdbc.queryForObject("select md5(coalesce(string_agg(to_jsonb(t)::text,E'\\n' order by to_jsonb(t)::text),'')) from "+table+" t",String.class)).isEqualTo(before.get(table));
    }
}

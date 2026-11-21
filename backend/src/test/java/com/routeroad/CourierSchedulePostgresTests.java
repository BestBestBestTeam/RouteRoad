package com.routeroad;

import com.routeroad.couriers.api.ScheduleView;
import com.routeroad.couriers.application.CourierScheduleService;
import com.routeroad.couriers.application.CourierQueryService;
import com.routeroad.orders.api.OrderView;
import com.routeroad.orders.application.OrderQueryService;
import com.routeroad.planning.api.RouteStop;
import com.routeroad.planning.domain.RouteStopType;
import com.routeroad.simulation.infrastructure.ScenarioStateRepository;
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
@Sql({"/repository-fixtures.sql", "/schedule-fixtures.sql"})
class CourierSchedulePostgresTests {
    static final LocalDate DATE = LocalDate.of(2099, 11, 9);
    static final long PLAN = 9_000_000_000_000_000_001L;
    @Autowired CourierScheduleService service;
    @Autowired CourierQueryService couriers;
    @Autowired OrderQueryService orders;
    @Autowired MockMvc mvc;
    @Autowired JsonMapper json;
    @Autowired PasswordEncoder passwords;
    @Autowired JdbcTemplate jdbc;
    @Autowired ScenarioStateRepository scenarios;
    @Autowired EntityManager entityManager;
    @Autowired EntityManagerFactory entityManagerFactory;
    @MockitoBean java.time.Clock realClock;
    private final String password = UUID.randomUUID().toString();
    private String hash;
    static UUID id(int group, int number) {
        return UUID.fromString("a0f8d%03x-0000-4000-8000-%012x".formatted(group, number));
    }
    @BeforeAll void protectScenario() {
        assumeTrue(scenarios.count() == 0, "Existing scenario must be preserved");
        hash = passwords.encode(password);
    }
    @BeforeEach void setup() {
        when(realClock.instant()).thenReturn(Instant.parse("2099-11-09T09:00:10Z"));
        jdbc.update("update app_user set password_hash=? where login in ('repo-dispatcher','repo-representative')", hash);
    }
    private ScheduleView schedule(int courier) { return service.get(id(0x200, courier), null, "repo-dispatcher"); }
    private MockHttpSession login(String login) throws Exception {
        var initial = mvc.perform(get("/api/me")).andExpect(status().isUnauthorized()).andReturn();
        var session = (MockHttpSession) initial.getRequest().getSession(false);
        mvc.perform(post("/api/auth/login").session(session).header("Origin", "http://localhost")
                .header("X-CSRF-Token", initial.getResponse().getHeader("X-CSRF-Token"))
                .contentType("application/json").content(json.writeValueAsString(Map.of("login", login, "password", password))))
                .andExpect(status().isOk());
        return session;
    }
    private String path(int courier) { return "/api/couriers/" + id(0x200, courier) + "/schedule"; }
    @Test void fullHttpDtoContractUsesStringVersionsZonedTimesAndExplicitNulls() throws Exception {
        var response = mvc.perform(get(path(1)).session(login("repo-dispatcher"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.route.plan_version").value(Long.toString(PLAN)))
                .andExpect(jsonPath("$.route.algorithm").value("greedy"))
                .andExpect(jsonPath("$.route.is_active").value(true))
                .andExpect(jsonPath("$.route.plan_date").value(DATE.toString()))
                .andExpect(jsonPath("$.route.total_distance_m").value(450))
                .andExpect(jsonPath("$.route.violated_windows").value(1))
                .andExpect(jsonPath("$.route.created_at").value("2099-11-09T09:00:00Z"))
                .andExpect(jsonPath("$.route.geometry.type").value("LineString"))
                .andExpect(jsonPath("$.route.geometry_kind").value("straight"))
                .andExpect(jsonPath("$.courier.total_orders").value(4))
                .andExpect(jsonPath("$.courier.completed_orders").value(1))
                .andExpect(jsonPath("$.courier.open_alert_count").value(1))
                .andExpect(jsonPath("$.courier.position.lat").value(55.123))
                .andExpect(jsonPath("$.courier.position.lon").value(82.456))
                .andExpect(jsonPath("$.courier.position.at").value("2099-11-09T09:00:00Z"))
                .andExpect(jsonPath("$.courier.is_available").value(true))
                .andExpect(jsonPath("$.courier.shift_start").value("2099-11-09T09:00:00Z"))
                .andExpect(jsonPath("$.courier.shift_end").value("2099-11-09T18:00:00Z"))
                .andExpect(jsonPath("$.courier.available_actions").isEmpty()).andReturn();
        var root = json.readTree(response.getResponse().getContentAsString());
        assertThat(root.size()).isEqualTo(6);
        assertThat(root.get("route").size()).isEqualTo(12);
        assertThat(root.get("courier").size()).isEqualTo(11);
        assertThat(root.get("clock").size()).isEqualTo(4);
        for (var stop : root.get("route").get("stops")) {
            assertThat(stop.size()).isEqualTo(9);
            assertThat(stop.get(stop.get("type").asString().equals("OFFICE") ? "order_id" : "office_id").isNull()).isTrue();
        }
        for (var order : root.get("orders")) {
            assertThat(order.size()).isEqualTo(17);
            assertThat(order.get("assignment").size()).isEqualTo(11);
            assertThat(order.get("version").isString()).isTrue();
            assertThat(order.get("assignment").get("plan_version").isString()).isTrue();
            assertThat(order.get("assignment").get("source_order_version").isString()).isTrue();
            assertThat(order.get("district").isNull()).isTrue();
            assertThat(order.get("available_actions").isEmpty()).isTrue();
        }
        assertThat(root.get("orders").get(1).get("version").asString()).isEqualTo("9007199254740993");
        assertThat(root.get("orders").get(1).get("assignment").get("actor").get("role").asString()).isEqualTo("dispatcher");
        assertThat(response.getResponse().getContentAsString()).doesNotContain(hash, password, "password_hash", "availability_date");
    }
    @Test void stopsAndGeometryFollowSequenceAndUseBothOfficeAndOrderCoordinates() {
        var result = schedule(1);
        assertThat(result.route().stops()).extracting(RouteStop::sequence).containsExactly(1, 2, 3, 4, 5, 6);
        assertThat(result.route().stops()).extracting(RouteStop::routeId).containsOnly(id(0x500, 1));
        assertThat(result.route().stops().get(2).arrivalEta()).isEqualTo(Instant.parse("2099-11-09T10:02:00Z"));
        assertThat(result.route().stops().get(2).departureEta()).isEqualTo(Instant.parse("2099-11-09T10:12:00Z"));
        assertThat(result.route().stops().get(2).distanceFromPrevM()).isEqualTo(100);
        assertThat(result.route().stops().get(2).windowViolationS()).isEqualTo(120);
        assertThat(result.route().geometry().coordinates()).containsExactly(List.of(82.0,55.0), List.of(82.0,55.0),
                List.of(82.05,55.05), List.of(82.04,55.04), List.of(82.06,55.06), List.of(83.0,56.0));
        assertThat(result.offices()).extracting(o -> o.id()).containsExactly(id(0x100,1), id(0x100,2));
    }
    @Test void officeOnlyRouteAndDifferentPlanVersionsAreValid() {
        var result = schedule(2);
        assertThat(result.route().planVersion()).isEqualTo(PLAN+1);
        assertThat(result.route().stops()).hasSize(1).allSatisfy(s -> assertThat(s.type()).isEqualTo(RouteStopType.OFFICE));
        assertThat(result.route().geometry().coordinates()).containsExactly(List.of(83.0,56.0));
        assertThat(result.orders()).isEmpty();
        assertThat(schedule(1).route().planVersion()).isEqualTo(PLAN);
    }
    @Test void emptyScheduleAndAssignmentsWithoutAnActiveRouteRemainRealData() {
        var empty = schedule(3);
        assertThat(empty.route()).isNull(); assertThat(empty.orders()).isEmpty();
        assertThat(empty.offices()).extracting(o -> o.id()).containsExactly(id(0x100,1));
        jdbc.update("update route set is_active=false where id=?", id(0x500,1));
        var assigned = schedule(1);
        assertThat(assigned.route()).isNull(); assertThat(assigned.orders()).hasSize(4);
    }
    @Test void emptyStopsDoNotProduceInventedGeometry() {
        jdbc.update("delete from route_stop where route_id=?", id(0x500,2));
        assertThat(schedule(2).route().geometry().coordinates()).isEmpty();
    }
    @Test void pythonPlanIsHiddenEvenIfItsActiveFlagWasIncorrectlySet() {
        assertThat(schedule(1).route().planVersion()).isEqualTo(PLAN);
        assertThat(schedule(1).orders()).allSatisfy(o -> assertThat(o.assignment().planVersion()).isEqualTo(PLAN));
        jdbc.update("update route set is_active=false where id=?", id(0x500,1));
        jdbc.update("update route set is_active=true where id=?", id(0x500,3));
        assertThat(schedule(1).route()).isNull();
        assertThat(schedule(1).orders()).allSatisfy(o -> assertThat(o.assignment().planVersion()).isEqualTo(PLAN));
    }
    @Test void partialRebuildKeepsUnaffectedAssignmentsAndOtherCouriersOlderRoutes() {
        jdbc.update("insert into route_stop select ?,sequence,type,order_id,office_id,arrival_eta,departure_eta,distance_from_prev_m,window_violation_s from route_stop where route_id=? and sequence>1",
                id(0x500,3), id(0x500,1));
        jdbc.update("update route set is_active=false where id=?", id(0x500,1));
        jdbc.update("update route set is_active=true where id=?", id(0x500,3));
        jdbc.update("insert into business_event(id,type,service_date,occurred_at,created_at,reason,payload) values(?,'PLAN_ACTIVATED',?,'2099-11-09T09:04:00Z','2099-11-09T09:04:00Z','Partial rebuild',cast(? as jsonb))",
                id(0x700,30), DATE, "{\"plan_version\":\""+(PLAN+2)+"\",\"order_ids\":[\""+id(0x300,1)+"\"]}");
        var result = schedule(1);
        assertThat(result.route().id()).isEqualTo(id(0x500,3));
        assertThat(result.orders()).hasSize(4).anySatisfy(o -> assertThat(o.assignment().planVersion()).isEqualTo(PLAN+2))
                .anySatisfy(o -> assertThat(o.assignment().planVersion()).isEqualTo(PLAN));
        assertThat(schedule(2).route().planVersion()).isEqualTo(PLAN+1);
    }
    private void unassignOrder5() {
        jdbc.update("insert into assignment(order_id,plan_version,source_order_version,reason_code,reason_text,decided_by,created_at) values(?,?,1,'NO_CAPACITY','Нет времени','SYSTEM','2099-11-09T09:02:00Z')", id(0x300,5), PLAN+1);
        jdbc.update("update business_event set payload=jsonb_set(payload,'{order_ids}',(payload->'order_ids') || to_jsonb(cast(? as text))) where id=?", id(0x300,5).toString(), id(0x700,2));
    }
    @Test void explicitUnassignedDecisionRemovesOrderWithoutFallingBackToOlderAssignment() {
        unassignOrder5();
        jdbc.update("delete from route_stop where route_id=? and order_id=?", id(0x500,1), id(0x300,5));
        assertThat(schedule(1).orders()).extracting(OrderView::id).doesNotContain(id(0x300,5));
    }
    @Test void staleUnassignedStopIsDataErrorWithNoPartialResponse() throws Exception {
        unassignOrder5();
        var response = mvc.perform(get(path(1)).session(login("repo-dispatcher"))).andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("INTERNAL_ERROR")).andReturn();
        assertThat(response.getResponse().getContentAsString()).doesNotContain("Client", "route_id", "order_id");
    }
    @Test void foreignStopIsDataErrorWithoutRevealingForeignOrders() throws Exception {
        jdbc.update("insert into route_stop(route_id,sequence,type,order_id,arrival_eta,departure_eta,distance_from_prev_m,window_violation_s) values(?,7,'ORDER',?,'2099-11-09T12:10:00Z','2099-11-09T12:20:00Z',100,0)", id(0x500,1), id(0x300,2));
        mvc.perform(get(path(1)).session(login("repo-representative"))).andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("INTERNAL_ERROR")).andExpect(jsonPath("$.orders").doesNotExist());
    }
    @Test void liveAssignmentMissingAStopIsDataError() {
        jdbc.update("delete from route_stop where route_id=? and order_id=?", id(0x500,1), id(0x300,5));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> schedule(1))
                .isInstanceOf(com.routeroad.couriers.application.InvalidScheduleException.class);
    }
    @Test void completedOrCancelledAssignmentsMayBeOmittedAfterARebuild() {
        jdbc.update("delete from route_stop where route_id=? and order_id=?", id(0x500,1), id(0x300,6));
        assertThat(schedule(1).orders()).hasSize(4);
    }
    @Test void representativeActionsHonorSingleOrderAndWorkdayRefusal() {
        var initial = service.get(id(0x200,1), DATE, "repo-representative");
        assertThat(initial.orders().get(0).availableActions()).isEmpty(); // individual refusal
        assertThat(initial.orders().get(1).availableActions()).containsExactly("COMPLETED", "REPORT_DELAY");
        assertThat(initial.orders().get(2).availableActions()).containsExactly("EN_ROUTE", "REPORT_DELAY", "REFUSE_ORDER");
        assertThat(initial.courier().availableActions()).containsExactly("REFUSE_WORKDAY");
        jdbc.update("update courier set is_available=false where id=?", id(0x200,1));
        jdbc.update("insert into business_event(id,type,service_date,courier_id,occurred_at,created_at,reason,payload) values(?,'COURIER_REFUSED',?,?,'2099-11-09T09:10:00Z','2099-11-09T09:10:00Z','Отказ','{}')", id(0x700,31), DATE, id(0x200,1));
        entityManager.clear();
        var refused = service.get(id(0x200,1), DATE, "repo-representative");
        assertThat(refused.courier().available()).isFalse();
        assertThat(refused.courier().availableActions()).isEmpty();
        assertThat(refused.orders().get(1).availableActions()).containsExactly("COMPLETED", "REPORT_DELAY");
        assertThat(refused.orders().get(2).availableActions()).isEmpty();
    }

    @Test void systemActorComesFromConfirmedActivationInsteadOfAssignmentDecidedBy() {
        jdbc.update("insert into assignment(order_id,plan_version,courier_id,route_id,source_order_version,reason_code,reason_text,decided_by,created_at) values(?,?,?,?,1,'OPTIMIZED','Оптимальный маршрут','SYSTEM','2099-11-09T09:02:00Z')",
                id(0x300,6), PLAN+1, id(0x200,2), id(0x500,2));
        jdbc.update("update business_event set payload=jsonb_set(payload,'{order_ids}',(payload->'order_ids') || to_jsonb(cast(? as text))) where id=?",id(0x300,6).toString(),id(0x700,2));
        jdbc.update("delete from route_stop where route_id=? and order_id=?",id(0x500,1),id(0x300,6));
        jdbc.update("insert into route_stop(route_id,sequence,type,order_id,arrival_eta,departure_eta,distance_from_prev_m,window_violation_s) values(?,2,'ORDER',?,'2099-11-09T11:30:00Z','2099-11-09T11:40:00Z',100,0)",id(0x500,2),id(0x300,6));
        var actor = schedule(2).orders().getFirst().assignment().actor();
        assertThat(actor.id()).isNull();
        assertThat(actor.name()).isEqualTo("Система");
        assertThat(actor.role()).isEqualTo(com.routeroad.shared.api.Actor.Role.SYSTEM);
    }
    @Test void scheduleOrdersAndCountersAgreeWithBothExistingGetServices() {
        for (int n=1;n<=3;n++) {
            var courierId = id(0x200,n);
            var schedule = schedule(n);
            var list = orders.get(DATE,null,null,id(0x200,n),true,1,100).items();
            assertThat(schedule.orders()).isEqualTo(list.stream().map(o -> o.withActions(List.of())).toList());
            var courier = couriers.get(DATE,null,null,null,null,1,100).items().stream()
                    .filter(c -> c.id().equals(courierId)).findFirst().orElseThrow();
            assertThat(schedule.courier()).isEqualTo(courier);
        }
    }
    @Test void explicitDateHasNoInventedRouteAndDisablesActions() {
        var result = service.get(id(0x200,1), DATE.plusDays(1), "repo-representative");
        assertThat(result.date()).isEqualTo(DATE.plusDays(1));
        assertThat(result.route()).isNull(); assertThat(result.orders()).isEmpty();
        assertThat(result.courier().availableActions()).isEmpty();
        assertThat(result.clock()).isEqualTo(schedule(1).clock());
    }
    @Test void allSecurityOutcomesAndMissingScenarioUseRealSessionAndRepositories() throws Exception {
        mvc.perform(get(path(1))).andExpect(status().isUnauthorized());
        var representative = login("repo-representative");
        mvc.perform(get(path(1)).session(representative)).andExpect(status().isOk());
        mvc.perform(get(path(2)).session(representative)).andExpect(status().isForbidden());
        var dispatcher = login("repo-dispatcher");
        mvc.perform(get(path(2)).session(dispatcher)).andExpect(status().isOk());
        mvc.perform(get(path(99)).session(dispatcher)).andExpect(status().isNotFound());
        mvc.perform(get("/api/couriers/bad/schedule").session(dispatcher)).andExpect(status().isBadRequest());
        scenarios.deleteAll(); scenarios.flush();
        mvc.perform(get(path(1)).session(dispatcher)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("SCENARIO_NOT_INITIALIZED"));
        assertThat(scenarios.count()).isZero();
    }
    @Test void getDoesNotModifyAnyBusinessTable() {
        var before = digests();
        schedule(1); schedule(2); schedule(3);
        service.get(id(0x200,1), DATE, "repo-representative");
        entityManager.flush();
        assertThat(digests()).isEqualTo(before);
    }
    private Map<String,String> digests() {
        var result = new LinkedHashMap<String,String>();
        for (String table : List.of("office","courier","service_order","planning_run","route","route_stop","assignment","app_user","business_event","alert","alert_order","api_command","scenario_state"))
            result.put(table,jdbc.queryForObject("select md5(coalesce(string_agg(row_to_json(t)::text,',' order by row_to_json(t)::text),'')) from "+table+" t",String.class));
        return result;
    }
    @Test void moreThanOnePageOfOrdersDoesNotAddQueries() {
        var stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        boolean enabled = stats.isStatisticsEnabled();
        stats.setStatisticsEnabled(true);
        try {
            entityManager.clear(); stats.clear(); schedule(1);
            long baseline = stats.getPrepareStatementCount();
            jdbc.update("""
                    insert into service_order(id,number,client_name,address,product,lat,lon,window_from,window_to,service_duration_s,status,service_date,version)
                    select md5('schedule-'||n)::uuid,'schedule-bulk-'||n,'Bulk client','Bulk address','Card',55,82,
                        '2099-11-09T09:00:00Z','2099-11-09T18:00:00Z',600,'ASSIGNED','2099-11-09',1
                    from generate_series(100,204) n
                    """);
            jdbc.update("""
                    insert into assignment(order_id,plan_version,courier_id,route_id,source_order_version,reason_code,reason_text,decided_by,created_at)
                    select md5('schedule-'||n)::uuid,?,?,?,1,'NEAREST','Bulk decision','SYSTEM','2099-11-09T09:00:00Z'
                    from generate_series(100,204) n
                    """,PLAN,id(0x200,1),id(0x500,1));
            jdbc.update("""
                    insert into route_stop(route_id,sequence,type,order_id,arrival_eta,departure_eta,distance_from_prev_m,window_violation_s)
                    select ?,n,'ORDER',md5('schedule-'||n)::uuid,'2099-11-09T13:00:00Z','2099-11-09T13:10:00Z',100,0
                    from generate_series(100,204) n
                    """,id(0x500,1));
            jdbc.update("update business_event set payload=jsonb_set(payload,'{order_ids}',(payload->'order_ids') || (select jsonb_agg(md5('schedule-'||n)::uuid::text) from generate_series(100,204) n)) where id=?",id(0x700,1));
            entityManager.clear(); stats.clear();
            assertThat(schedule(1).orders()).hasSize(109);
            assertThat(stats.getPrepareStatementCount()).isEqualTo(baseline).isLessThanOrEqualTo(10);
        } finally { stats.setStatisticsEnabled(enabled); }
    }
}

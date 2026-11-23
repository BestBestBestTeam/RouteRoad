package com.routeroad;

import com.routeroad.couriers.application.CourierQueryService;
import com.routeroad.couriers.application.CourierScheduleService;
import com.routeroad.orders.application.OrderQueryService;
import com.routeroad.orders.domain.ServiceOrderStatus;
import com.routeroad.planning.api.RouteStop;
import com.routeroad.planning.infrastructure.RouteRepository;
import com.routeroad.simulation.infrastructure.ScenarioStateRepository;
import com.routeroad.workday.api.MapView;
import com.routeroad.workday.api.MapView.*;
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
import org.springframework.jdbc.core.ConnectionCallback;
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
@EnabledIfEnvironmentVariable(named = "ROUTEROAD_REPOSITORY_TESTS", matches = "true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Transactional
@Rollback
@Sql({"/repository-fixtures.sql","/schedule-fixtures.sql","/map-fixtures.sql"})
class WorkdayMapPostgresTests {
    static final LocalDate DATE=LocalDate.of(2099,11,9);
    static final long PLAN=9_000_000_000_000_000_001L;
    @Autowired WorkdayMapService service;
    @Autowired WorkdayService workday;
    @Autowired OrderQueryService orders;
    @Autowired CourierQueryService couriers;
    @Autowired CourierScheduleService schedules;
    @Autowired RouteRepository routes;
    @Autowired ScenarioStateRepository scenarios;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired JsonMapper json;
    @Autowired PasswordEncoder passwords;
    @Autowired EntityManager entityManager;
    @Autowired EntityManagerFactory entityManagerFactory;
    @MockitoBean java.time.Clock realClock;
    private final String password=UUID.randomUUID().toString();
    private String hash;
    static UUID id(int group,int number) {
        return UUID.fromString("a0f8d%03x-0000-4000-8000-%012x".formatted(group,number));
    }
    @BeforeAll void preserveScenario() {
        assumeTrue(scenarios.count()==0,"Existing scenario must be preserved");
        hash=passwords.encode(password);
    }
    @BeforeEach void setup() {
        when(realClock.instant()).thenReturn(Instant.parse("2099-11-09T09:00:10Z"));
        jdbc.update("update app_user set password_hash=? where login in ('repo-dispatcher','repo-representative')",hash);
    }
    private MapView map() { return service.get(null); }
    private MockHttpSession login(String login) throws Exception {
        var initial=mvc.perform(get("/api/me")).andExpect(status().isUnauthorized()).andReturn();
        var session=(MockHttpSession)initial.getRequest().getSession(false);
        mvc.perform(post("/api/auth/login").session(session).header("Origin","http://localhost")
                .header("X-CSRF-Token",initial.getResponse().getHeader("X-CSRF-Token"))
                .contentType("application/json").content(json.writeValueAsString(Map.of("login",login,"password",password))))
                .andExpect(status().isOk());
        return session;
    }
    @Test void mapProjectsAllDayDataWithoutPaginationOrExtraFields() throws Exception {
        var response=mvc.perform(get("/api/workday/map").session(login("repo-dispatcher"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.orders.length()").value(6)).andExpect(jsonPath("$.couriers.length()").value(3))
                .andExpect(jsonPath("$.offices.length()").value(3)).andExpect(jsonPath("$.routes.length()").value(2))
                .andExpect(jsonPath("$.alerts.length()").value(2)).andExpect(jsonPath("$.clock.revision").value("1")).andReturn();
        var body=json.readTree(response.getResponse().getContentAsString());
        assertThat(body.size()).isEqualTo(6);
        for(var row:body.get("orders")) assertThat(row.size()).isEqualTo(7);
        for(var row:body.get("couriers")) assertThat(row.size()).isEqualTo(3);
        for(var row:body.get("routes")) {assertThat(row.size()).isEqualTo(6);assertThat(row.get("plan_version").isString()).isTrue();}
        for(var row:body.get("alerts")) assertThat(row.size()).isEqualTo(3);
        for(var row:body.get("offices")) assertThat(row.size()).isEqualTo(5);
        assertThat(response.getResponse().getContentAsString()).doesNotContain(hash,password,"password_hash","available_actions","assignment","is_available");
    }
    @Test void unassignedOrdersAndUnavailableCouriersRemainOnTheMap() {
        var result=map();
        assertThat(result.orders()).anySatisfy(o->{assertThat(o.id()).isEqualTo(id(0x300,2));assertThat(o.dayStatus()).isEqualTo(ServiceOrderStatus.UNASSIGNED);});
        assertThat(result.couriers()).extracting(MapCourier::id).containsExactly(id(0x200,3),id(0x200,1),id(0x200,2));
        assertThat(result.couriers()).filteredOn(c->c.id().equals(id(0x200,2))).allSatisfy(c->assertThat(c.position()).isNull());
        assertThat(result.couriers()).filteredOn(c->c.id().equals(id(0x200,1))).allSatisfy(c->{
            assertThat(c.position().lat()).isEqualTo(55.123);assertThat(c.position().lon()).isEqualTo(82.456);
            assertThat(c.position().at()).isEqualTo(Instant.parse("2099-11-09T09:00:00Z"));
        });
    }
    @Test void geometryAndStopSequenceExactlyMatchSchedules() {
        var result=map();
        for(var route:result.routes()) {
            var schedule=schedules.get(route.courierId(),DATE,"repo-dispatcher").route();
            assertThat(route.id()).isEqualTo(schedule.id());
            assertThat(route.planVersion()).isEqualTo(schedule.planVersion());
            assertThat(route.stops()).isEqualTo(schedule.stops());
            assertThat(route.geometry()).isEqualTo(schedule.geometry());
            assertThat(route.geometryKind()).isEqualTo(schedule.geometryKind()).isEqualTo("straight");
        }
        var route=result.routes().stream().filter(r->r.courierId().equals(id(0x200,1))).findFirst().orElseThrow();
        assertThat(route.stops()).extracting(RouteStop::sequence).containsExactly(1,2,3,4,5,6);
        assertThat(route.geometry().coordinates()).containsExactly(List.of(82.0,55.0),List.of(82.0,55.0),
                List.of(82.05,55.05),List.of(82.04,55.04),List.of(82.06,55.06),List.of(83.0,56.0));
    }
    @Test void mapAndOtherReadsUseTheSameOrdersCouriersOfficesAndClock() {
        var result=map();
        var orderList=orders.get(DATE,null,null,null,null,1,100);
        assertThat(result.orders()).isEqualTo(orderList.items().stream().map(o->new MapOrder(o.id(),o.number(),o.address(),o.clientName(),o.lat(),o.lon(),o.dayStatus())).toList());
        var courierList=couriers.get(DATE,null,null,null,null,1,100);
        assertThat(result.couriers()).isEqualTo(courierList.items().stream().map(c->new MapCourier(c.id(),c.name(),c.position())).toList());
        var summary=workday.get(DATE);
        assertThat(result.offices()).isEqualTo(summary.offices());
        assertThat(result.orders()).hasSize(summary.counts().ordersTotal());
        assertThat(result.routes().isEmpty()).isEqualTo(!summary.hasRoutes());
        assertThat(result.clock()).isEqualTo(summary.clock()).isEqualTo(orderList.clock()).isEqualTo(courierList.clock());
        for(var route:result.routes()) for(var stop:route.stops()) if(stop.orderId()!=null) {
            var order=orderList.items().stream().filter(o->o.id().equals(stop.orderId())).findFirst().orElseThrow();
            assertThat(order.assignment().courierId()).isEqualTo(route.courierId());
        }
    }
    @Test void transfersKeepHistoricalDayStatusAndDoNotCreateCurrentRouteMembership() {
        var result=map();
        assertThat(result.orders()).anySatisfy(o->{assertThat(o.id()).isEqualTo(id(0x300,7));assertThat(o.dayStatus()).isEqualTo(ServiceOrderStatus.RESCHEDULED);});
        assertThat(result.routes()).allSatisfy(r->assertThat(r.stops()).extracting(RouteStop::orderId).doesNotContain(id(0x300,7)));
        var tomorrow=service.get(DATE.plusDays(1));
        assertThat(tomorrow.orders()).hasSize(2).allSatisfy(o->assertThat(o.dayStatus()).isEqualTo(ServiceOrderStatus.NEW));
        assertThat(tomorrow.routes()).isEmpty();
        assertThat(tomorrow.couriers()).isEqualTo(result.couriers());
        assertThat(tomorrow.clock()).isEqualTo(result.clock());
    }
    @Test void onlyOpenAlertsForSelectedDayIncludeAllTheirOrderLinks() {
        assertThat(map().alerts()).containsExactly(new MapAlert(id(0x800,1),List.of(id(0x300,1)),id(0x200,1)),
                new MapAlert(id(0x800,3),List.of(id(0x300,4),id(0x300,5)),null));
        assertThat(service.get(DATE.plusDays(1)).alerts()).extracting(MapAlert::id).containsExactly(id(0x800,4));
    }
    @Test void openAlertWithoutLinksIsNotDroppedByTheBatchRead() {
        jdbc.update("insert into alert(id,source_event_id,service_date,kind,scope,severity,status,reason_code,reason_text,created_at,deadline_at) values(?,?,?,'conflict','order','warning','open','CONFLICT','Empty','2099-11-09T09:00:00Z','2099-11-09T09:10:00Z')",id(0x800,5),id(0x700,3),DATE);
        assertThat(map().alerts()).contains(new MapAlert(id(0x800,5),List.of(),null));
    }
    @Test void pythonPlanCannotBecomeCurrentWithoutJavaConfirmation() {
        assertThat(map().routes()).extracting(MapRoute::planVersion).containsExactly(PLAN,PLAN+1);
        jdbc.update("update route set is_active=false where id=?",id(0x500,1));
        jdbc.update("update route set is_active=true where id=?",id(0x500,3));
        entityManager.clear();
        assertThat(map().routes()).extracting(MapRoute::id).containsExactly(id(0x500,2));
        assertThat(schedules.get(id(0x200,1),DATE,"repo-dispatcher").route()).isNull();
    }
    @Test void partialRebuildUsesTheSameActivationRuleAtDifferentPlanVersions() {
        jdbc.update("insert into route_stop select ?,sequence,type,order_id,office_id,arrival_eta,departure_eta,distance_from_prev_m,window_violation_s from route_stop where route_id=? and sequence>1",id(0x500,3),id(0x500,1));
        jdbc.update("update route set is_active=false where id=?",id(0x500,1));
        jdbc.update("update route set is_active=true where id=?",id(0x500,3));
        jdbc.update("insert into business_event(id,type,service_date,occurred_at,created_at,reason,payload) values(?,'PLAN_ACTIVATED',?,'2099-11-09T09:04:00Z','2099-11-09T09:04:00Z','Partial',cast(? as jsonb))",id(0x700,30),DATE,
                "{\"plan_version\":\""+(PLAN+2)+"\",\"order_ids\":[\""+id(0x300,1)+"\"]}");
        entityManager.clear();
        assertThat(map().routes()).extracting(MapRoute::planVersion).containsExactly(PLAN+2,PLAN+1);
        geometryAndStopSequenceExactlyMatchSchedules();
    }
    @Test void confirmedUnassignmentOverridesOlderAssignedDecision() {
        jdbc.update("insert into assignment(order_id,plan_version,source_order_version,reason_code,reason_text,decided_by,created_at) values(?,?,1,'NO_CAPACITY','Нет времени','SYSTEM','2099-11-09T09:02:00Z')",id(0x300,5),PLAN+1);
        jdbc.update("update business_event set payload=jsonb_set(payload,'{order_ids}',(payload->'order_ids') || to_jsonb(cast(? as text))) where id=?",id(0x300,5).toString(),id(0x700,2));
        jdbc.update("delete from route_stop where route_id=? and order_id=?",id(0x500,1),id(0x300,5));
        assertThat(map().orders()).extracting(MapOrder::id).contains(id(0x300,5));
        assertThat(map().routes()).allSatisfy(r->assertThat(r.stops()).extracting(RouteStop::orderId).doesNotContain(id(0x300,5)));
        assertThat(orders.get(DATE,null,null,id(0x200,1),true,1,100).items()).noneSatisfy(o->assertThat(o.id()).isEqualTo(id(0x300,5)));
    }
    @Test void staleForeignOrUnassignedStopHasTheSameDataErrorAsSchedule() throws Exception {
        jdbc.update("insert into route_stop(route_id,sequence,type,order_id,arrival_eta,departure_eta,distance_from_prev_m,window_violation_s) values(?,7,'ORDER',?,'2099-11-09T12:10:00Z','2099-11-09T12:20:00Z',100,0)",id(0x500,1),id(0x300,2));
        mvc.perform(get("/api/workday/map").session(login("repo-dispatcher"))).andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("INTERNAL_ERROR")).andExpect(jsonPath("$.orders").doesNotExist());
        assertThatThrownBy(()->schedules.get(id(0x200,1),DATE,"repo-dispatcher"))
                .isInstanceOf(com.routeroad.couriers.application.InvalidScheduleException.class);
    }
    @Test void missingOrderCoordinatesAreRejectedByExistingSchemaWithoutInventingAPoint() {
        jdbc.execute((ConnectionCallback<Void>)connection->{
            var savepoint=connection.setSavepoint();
            try(var statement=connection.createStatement()) {
                assertThatThrownBy(()->statement.executeUpdate("update service_order set lat=null where id='a0f8d300-0000-4000-8000-000000000001'"))
                        .isInstanceOf(java.sql.SQLException.class).extracting(e->((java.sql.SQLException)e).getSQLState()).isEqualTo("23502");
            } finally {connection.rollback(savepoint);connection.releaseSavepoint(savepoint);}
            return null;
        });
        assertThat(map().orders()).anySatisfy(o->{assertThat(o.id()).isEqualTo(id(0x300,1));assertThat(o.lat()).isEqualTo(55);assertThat(o.lon()).isEqualTo(82);});
    }
    @Test void realSecurityParametersAndMissingScenario() throws Exception {
        mvc.perform(get("/api/workday/map")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/workday/map").session(login("repo-representative"))).andExpect(status().isForbidden());
        var session=login("repo-dispatcher");
        mvc.perform(get("/api/workday/map").session(session)).andExpect(status().isOk());
        mvc.perform(get("/api/workday/map").param("date","2099-02-30").session(session)).andExpect(status().isBadRequest());
        scenarios.deleteAll();scenarios.flush();
        mvc.perform(get("/api/workday/map").session(session)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("SCENARIO_NOT_INITIALIZED"));
        assertThat(scenarios.count()).isZero();
    }
    @Test @Sql("/empty-workday-fixture.sql")
    void emptyScenarioHasNoInventedData() {
        var result=map();
        assertThat(result.orders()).isEmpty();assertThat(result.couriers()).isEmpty();assertThat(result.offices()).isEmpty();
        assertThat(result.routes()).isEmpty();assertThat(result.alerts()).isEmpty();
    }
    @Test @Sql("/empty-workday-fixture.sql") void officesOnlyNeedNoCouriersOrRoutes() {
        jdbc.update("insert into office(id,name,address,lat,lon) values(?,'Office','Address',55,82)",id(0x100,1));
        var result=map();assertThat(result.offices()).hasSize(1);assertThat(result.orders()).isEmpty();
        assertThat(result.couriers()).isEmpty();assertThat(result.routes()).isEmpty();
    }
    @Test void couriersWithoutActiveRoutesKeepTheirOwnPositions() {
        jdbc.update("update route set is_active=false where is_active");
        assertThat(map().routes()).isEmpty();
        assertThat(map().couriers()).hasSize(3).anySatisfy(c->{assertThat(c.id()).isEqualTo(id(0x200,2));assertThat(c.position()).isNull();});
    }
    @Test void getDoesNotChangeAnyBusinessRow() {
        var before=digests();map();service.get(DATE.plusDays(1));entityManager.flush();assertThat(digests()).isEqualTo(before);
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints={0,1,2})
    void mapAndScheduleShareNullableGeometryWithZeroOneOrTwoRealStops(int count) throws Exception {
        jdbc.update("delete from route_stop where route_id=? and sequence>?",id(0x500,2),count);
        var route=map().routes().stream().filter(r->r.courierId().equals(id(0x200,2))).findFirst().orElseThrow();
        var schedule=schedules.get(id(0x200,2),DATE,"repo-dispatcher").route();
        assertThat(route.id()).isEqualTo(id(0x500,2));
        assertThat(route.stops()).hasSize(count).isEqualTo(schedule.stops());
        assertThat(route.geometry()).isEqualTo(schedule.geometry());
        if(count<2) assertThat(route.geometry()).isNull();
        else assertThat(route.geometry().coordinates()).containsExactly(List.of(83.0,56.0),List.of(82.0,55.0));
        var session=login("repo-dispatcher");
        var mapResponse=mvc.perform(get("/api/workday/map").session(session)).andExpect(status().isOk()).andReturn();
        var scheduleResponse=mvc.perform(get("/api/couriers/"+id(0x200,2)+"/schedule").session(session)).andExpect(status().isOk()).andReturn();
        var mapGeometry=json.readTree(mapResponse.getResponse().getContentAsString()).get("routes").get(1).get("geometry");
        var scheduleRoute=json.readTree(scheduleResponse.getResponse().getContentAsString()).get("route");
        assertThat(scheduleRoute.get("id").asString()).isEqualTo(id(0x500,2).toString());
        assertThat(scheduleRoute.get("stops").size()).isEqualTo(count);
        assertThat(mapGeometry.isNull()).isEqualTo(count<2);
        assertThat(mapGeometry).isEqualTo(scheduleRoute.get("geometry"));
    }
    @Test void latestActivationTimeWinsOverTheNumericallyLargerPlanVersion() {
        jdbc.update("insert into assignment(order_id,plan_version,source_order_version,reason_code,reason_text,decided_by,created_at) values(?,?,1,'NO_CAPACITY','Нет времени','SYSTEM','2099-11-09T12:00:00Z')",id(0x300,5),PLAN+1);
        jdbc.update("update business_event set created_at='2099-11-09T08:59:00Z',payload=jsonb_set(payload,'{order_ids}',(payload->'order_ids') || to_jsonb(cast(? as text))) where id=?",id(0x300,5).toString(),id(0x700,2));
        assertThat(map().routes().getFirst().stops()).extracting(RouteStop::orderId).contains(id(0x300,5));
        assertThat(orders.get(DATE,null,null,id(0x200,1),true,1,100).items()).anySatisfy(o->assertThat(o.id()).isEqualTo(id(0x300,5)));
    }
    @Test void manyCouriersOrdersStopsAndAlertsDoNotIncreaseTheQueryCount() {
        var stats=entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        boolean enabled=stats.isStatisticsEnabled();stats.setStatisticsEnabled(true);
        try {
            entityManager.clear();stats.clear();map();long baseline=stats.getPrepareStatementCount();
            jdbc.update("""
                    insert into courier(id,name,office_id,shift_start,shift_end,is_available)
                    select md5('map-courier-'||n)::uuid,'Bulk courier '||n,?,'2099-11-09T09:00:00Z','2099-11-09T18:00:00Z',n%2=0
                    from generate_series(10,21) n
                    """,id(0x100,1));
            jdbc.update("""
                    insert into route(id,courier_id,plan_date,plan_version,is_active,algorithm,total_distance_m,violated_windows,created_at)
                    select md5('map-route-'||n)::uuid,md5('map-courier-'||n)::uuid,'2099-11-09',?,true,'greedy',1200,0,'2099-11-09T09:00:00Z'
                    from generate_series(10,21) n
                    """,PLAN);
            jdbc.update("""
                    insert into service_order(id,number,client_name,address,product,lat,lon,window_from,window_to,service_duration_s,status,service_date,version)
                    select md5('map-order-'||n||'-'||m)::uuid,'map-bulk-'||n||'-'||m,'Bulk client','Bulk address','Card',55,82,
                        '2099-11-09T09:00:00Z','2099-11-09T18:00:00Z',600,'ASSIGNED','2099-11-09',1
                    from generate_series(10,21) n cross join generate_series(1,12) m
                    """);
            jdbc.update("""
                    insert into assignment(order_id,plan_version,courier_id,route_id,source_order_version,reason_code,reason_text,decided_by,created_at)
                    select md5('map-order-'||n||'-'||m)::uuid,?,md5('map-courier-'||n)::uuid,md5('map-route-'||n)::uuid,
                        1,'NEAREST','Bulk decision','SYSTEM','2099-11-09T09:00:00Z'
                    from generate_series(10,21) n cross join generate_series(1,12) m
                    """,PLAN);
            jdbc.update("""
                    insert into route_stop(route_id,sequence,type,office_id,arrival_eta,departure_eta,distance_from_prev_m,window_violation_s)
                    select md5('map-route-'||n)::uuid,1,'OFFICE',?,'2099-11-09T09:00:00Z','2099-11-09T09:00:00Z',0,0
                    from generate_series(10,21) n
                    """,id(0x100,1));
            jdbc.update("""
                    insert into route_stop(route_id,sequence,type,order_id,arrival_eta,departure_eta,distance_from_prev_m,window_violation_s)
                    select md5('map-route-'||n)::uuid,m+1,'ORDER',md5('map-order-'||n||'-'||m)::uuid,
                        timestamptz '2099-11-09T09:00:00Z'+m*interval '10 minutes',
                        timestamptz '2099-11-09T09:00:00Z'+(m+1)*interval '10 minutes',100,0
                    from generate_series(10,21) n cross join generate_series(1,12) m
                    """);
            jdbc.update("""
                    update business_event set payload=jsonb_set(payload,'{order_ids}',(payload->'order_ids')||
                        (select jsonb_agg(md5('map-order-'||n||'-'||m)::uuid::text)
                         from generate_series(10,21) n cross join generate_series(1,12) m)) where id=?
                    """,id(0x700,1));
            jdbc.update("""
                    insert into alert(id,source_event_id,service_date,courier_id,kind,scope,severity,status,reason_code,reason_text,created_at,deadline_at)
                    select md5('map-alert-'||n)::uuid,?,'2099-11-09',md5('map-courier-'||n)::uuid,'refusal','courier_day',
                        'warning','open','COURIER_REFUSED','Bulk alert','2099-11-09T09:00:00Z','2099-11-09T09:10:00Z'
                    from generate_series(10,21) n
                    """,id(0x700,3));
            jdbc.update("""
                    insert into alert_order(alert_id,order_id,status)
                    select md5('map-alert-'||n)::uuid,md5('map-order-'||n||'-'||m)::uuid,'pending'
                    from generate_series(10,21) n cross join generate_series(1,12) m
                    """);
            entityManager.clear();stats.clear();var result=map();
            assertThat(result.couriers()).hasSize(15);assertThat(result.routes()).hasSize(14);
            assertThat(result.orders()).hasSize(150);assertThat(result.alerts()).hasSize(14);
            assertThat(stats.getPrepareStatementCount()).isEqualTo(baseline).isLessThanOrEqualTo(8);
            System.out.printf("Map SQL queries: %d -> %d for 3 -> 15 couriers, 6 -> 150 orders and 2 -> 14 routes.%n",
                    baseline,stats.getPrepareStatementCount());
        } finally {stats.setStatisticsEnabled(enabled);}
    }
    private Map<String,String> digests() {
        var result=new LinkedHashMap<String,String>();
        for(String table:List.of("office","courier","service_order","planning_run","route","route_stop","assignment","app_user","business_event","alert","alert_order","api_command","scenario_state"))
            result.put(table,jdbc.queryForObject("select md5(coalesce(string_agg(row_to_json(t)::text,',' order by row_to_json(t)::text),'')) from "+table+" t",String.class));
        return result;
    }
}

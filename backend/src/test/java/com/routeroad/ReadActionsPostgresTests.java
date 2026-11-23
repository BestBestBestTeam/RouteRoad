package com.routeroad;

import com.routeroad.couriers.application.CourierScheduleService;
import com.routeroad.execution.api.AlertView;
import com.routeroad.execution.application.AlertQueryService;
import com.routeroad.orders.api.OrderView;
import com.routeroad.orders.application.OrderQueryService;
import com.routeroad.simulation.infrastructure.ScenarioStateRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("local")
@EnabledIfEnvironmentVariable(named="ROUTEROAD_REPOSITORY_TESTS",matches="true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Transactional @Rollback
@Sql({"/repository-fixtures.sql","/schedule-fixtures.sql","/map-fixtures.sql","/alert-fixtures.sql"})
class ReadActionsPostgresTests {
    private static final LocalDate DATE=LocalDate.of(2099,11,9);
    private static final Instant REAL=Instant.parse("2099-11-09T09:00:10Z");
    private static final Instant DEADLINE=Instant.parse("2099-11-09T09:13:00Z");
    private static final long PLAN=9_000_000_000_000_000_001L;
    @Autowired OrderQueryService orders;
    @Autowired AlertQueryService alerts;
    @Autowired CourierScheduleService schedules;
    @Autowired ScenarioStateRepository scenarios;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager entityManager;
    @Autowired EntityManagerFactory entityManagerFactory;
    @MockitoBean java.time.Clock realClock;
    static UUID id(int group,int n) {return UUID.fromString("a0f8d%03x-0000-4000-8000-%012x".formatted(group,n));}
    @BeforeAll void preserveScenario() {assumeTrue(scenarios.count()==0,"Existing scenario must be preserved");}
    @BeforeEach void prepareConcretePendingGroup() {
        // Keep one open group: order 5 pending, order 4 resolved. Other alerts cannot affect the cases below.
        jdbc.update("update alert set status='resolved',resolved_at=created_at where status='open' and id<>?",id(0x800,3));
        jdbc.update("update service_order set status='ARRIVED' where id in (?,?)",id(0x300,4),id(0x300,5));
        clock(DEADLINE.minusSeconds(1),1);
    }
    private void clock(Instant modelNow,double speed) {
        jdbc.update("update scenario_state set model_anchor_at=?,real_anchor_at=?,speed=?",modelNow.atOffset(ZoneOffset.UTC),REAL.atOffset(ZoneOffset.UTC),speed);
        when(realClock.instant()).thenReturn(REAL);
        entityManager.clear();
    }
    private OrderView card(int order, String login) {return orders.detail(id(0x300,order),DATE,login).order();}
    private OrderView scheduled(int order,String login) {
        return schedules.get(id(0x200,1),DATE,login).orders().stream().filter(o->o.id().equals(id(0x300,order))).findFirst().orElseThrow();
    }
    private AlertView group() {return alerts.get(DATE,null,null,null,1,100).items().stream().filter(a->a.id().equals(id(0x800,3))).findFirst().orElseThrow();}
    @Test void pendingGroupBlocksOnlyThePendingMeetingAndPreservesOtherOrders() {
        var blocked=card(5,"repo-representative");
        assertThat(blocked.availableActions()).containsExactly("REPORT_DELAY","REFUSE_ORDER");
        assertThat(scheduled(5,"repo-representative").availableActions()).isEqualTo(blocked.availableActions());
        assertThat(card(4,"repo-representative").availableActions()).contains("IN_PROGRESS");
        assertThat(scheduled(4,"repo-representative").availableActions()).contains("IN_PROGRESS");
        assertThat(group().orders()).extracting(AlertView.OrderEntry::status).containsExactly("resolved","pending");
        assertThat(orders.get(DATE,null,null,null,null,1,100).items()).allSatisfy(o->assertThat(o.availableActions()).doesNotContain("IN_PROGRESS"));
    }
    @Test void resolvingOneElementRestoresOnlyItsMeetingAndFullyResolvedGroupHasNoActions() {
        jdbc.update("update alert_order set status='resolved',resolved_at=? where alert_id=? and order_id=?",DEADLINE.atOffset(ZoneOffset.UTC),id(0x800,3),id(0x300,5));
        assertThat(card(5,"repo-representative").availableActions()).contains("IN_PROGRESS");
        assertThat(scheduled(5,"repo-representative").availableActions()).contains("IN_PROGRESS");
        assertThat(group().availableActions()).isEmpty();
    }
    @Test void closedHeaderDoesNotBlockEvenIfAnElementStillSaysPending() {
        jdbc.update("update alert set status='resolved',resolved_at=? where id=?",DEADLINE.atOffset(ZoneOffset.UTC),id(0x800,3));
        assertThat(card(5,"repo-representative").availableActions()).contains("IN_PROGRESS");
        assertThat(scheduled(5,"repo-representative").availableActions()).contains("IN_PROGRESS");
        assertThat(group().availableActions()).isEmpty();
    }
    @Test void startedMeetingKeepsCompletionAndDelayWhileAwaitingDecision() {
        jdbc.update("update service_order set status='IN_PROGRESS' where id=?",id(0x300,5));
        assertThat(card(5,"repo-representative").availableActions()).containsExactly("COMPLETED","REPORT_DELAY");
        assertThat(scheduled(5,"repo-representative").availableActions()).containsExactly("COMPLETED","REPORT_DELAY");
        assertThat(group().availableActions()).isEmpty();
    }
    @Test void changingPendingMemberDoesNotBlockTheWholeCourier() {
        jdbc.update("update alert_order set status='resolved',resolved_at=? where alert_id=? and order_id=?",DEADLINE.atOffset(ZoneOffset.UTC),id(0x800,3),id(0x300,5));
        jdbc.update("update alert_order set status='pending',resolved_at=null,resolved_by=null,resolution_reason=null where alert_id=? and order_id=?",id(0x800,3),id(0x300,4));
        assertThat(card(4,"repo-representative").availableActions()).doesNotContain("IN_PROGRESS");
        assertThat(card(5,"repo-representative").availableActions()).contains("IN_PROGRESS");
    }
    @ParameterizedTest @ValueSource(ints={-1,0,1})
    void deadlineIsStrictAndAgreesAcrossListCardScheduleAndAlerts(int offset) {
        clock(DEADLINE.plusSeconds(offset),1);
        var expected=offset<0 ? List.of("REASSIGN") : List.<String>of();
        var list=orders.get(DATE,null,null,null,null,1,100).items().stream().filter(o->o.id().equals(id(0x300,5))).findFirst().orElseThrow();
        assertThat(list.availableActions()).isEqualTo(expected);
        assertThat(card(5,"repo-dispatcher").availableActions()).isEqualTo(expected);
        assertThat(scheduled(5,"repo-dispatcher").availableActions()).isEqualTo(expected);
        var expectedAlert=offset<0 ? List.of("REASSIGN","AUTO_REASSIGN") : List.of("AUTO_REASSIGN");
        assertThat(group().availableActions()).isEqualTo(expectedAlert);
        var related=orders.detail(id(0x300,5),DATE,"repo-dispatcher").alerts().stream().filter(a->a.id().equals(id(0x800,3))).findFirst().orElseThrow();
        assertThat(related.availableActions()).isEqualTo(expectedAlert);
        assertThat(card(4,"repo-dispatcher").availableActions()).isEmpty(); // resolved element
    }
    @Test void pausedModelClockDoesNotExpireWithRealTime() {
        clock(DEADLINE.minusSeconds(1),0);
        when(realClock.instant()).thenReturn(REAL.plusSeconds(86_400));
        var view=orders.detail(id(0x300,5),DATE,"repo-dispatcher");
        assertThat(view.clock().now()).isEqualTo(DEADLINE.minusSeconds(1));
        assertThat(view.order().availableActions()).containsExactly("REASSIGN");
        assertThat(group().availableActions()).containsExactly("REASSIGN","AUTO_REASSIGN");
    }
    @Test void anotherUnexpiredPendingAlertCanOfferManualActionWithoutRevivingAnExpiredHeader() {
        clock(DEADLINE,0);
        jdbc.update("update alert set status='open',resolved_at=null,resolved_by=null,resolution_reason=null where id=?",id(0x800,5));
        jdbc.update("update alert_order set status='pending',resolved_at=null,resolved_by=null,resolution_reason=null where alert_id=? and order_id=?",id(0x800,5),id(0x300,5));
        assertThat(card(5,"repo-dispatcher").availableActions()).containsExactly("REASSIGN");
        assertThat(group().availableActions()).containsExactly("AUTO_REASSIGN");
    }
    @Test void reassignmentRevokesTheFormerCouriersAccessAndActions() {
        jdbc.update("insert into assignment(order_id,plan_version,courier_id,route_id,source_order_version,reason_code,reason_text,decided_by,created_at) values(?,?,?,?,1,'OPTIMIZED','Transferred','SYSTEM','2099-11-09T09:02:00Z')",id(0x300,5),PLAN+1,id(0x200,2),id(0x500,2));
        jdbc.update("update business_event set payload=jsonb_set(payload,'{order_ids}',(payload->'order_ids')||to_jsonb(cast(? as text))) where id=?",id(0x300,5).toString(),id(0x700,2));
        jdbc.update("update alert_order set status='resolved',resolved_at=? where alert_id=? and order_id=?",DEADLINE.atOffset(ZoneOffset.UTC),id(0x800,3),id(0x300,5));
        jdbc.update("delete from route_stop where route_id=? and order_id=?",id(0x500,1),id(0x300,5));
        assertThatThrownBy(()->card(5,"repo-representative")).isInstanceOf(AccessDeniedException.class);
        assertThat(schedules.get(id(0x200,1),DATE,"repo-representative").orders()).noneMatch(o->o.id().equals(id(0x300,5)));
        assertThat(card(5,"repo-dispatcher").assignment().courierId()).isEqualTo(id(0x200,2));
    }
    @Test void computingActionsDoesNotWriteAnyBusinessRows() {
        var before=digests();
        orders.get(DATE,null,null,null,null,1,100);card(5,"repo-dispatcher");card(5,"repo-representative");
        schedules.get(id(0x200,1),DATE,"repo-representative");schedules.get(id(0x200,1),DATE,"repo-dispatcher");group();
        entityManager.flush();assertThat(digests()).isEqualTo(before);
    }
    @Test void pendingDecisionAndDeadlineFlagsDoNotAddNPlusOneQueries() {
        var stats=entityManagerFactory.unwrap(SessionFactory.class).getStatistics();boolean enabled=stats.isStatisticsEnabled();stats.setStatisticsEnabled(true);
        try {
            var before=queryCounts(stats);
            jdbc.update("""
                    insert into service_order(id,number,client_name,address,product,lat,lon,window_from,window_to,service_duration_s,status,service_date,version)
                    select md5('actions-'||n)::uuid,'actions-'||n,'Client','Address','Card',55,82,'2099-11-09T09:00:00Z','2099-11-09T18:00:00Z',600,'ARRIVED','2099-11-09',1
                    from generate_series(100,299) n
                    """);
            jdbc.update("""
                    insert into assignment(order_id,plan_version,courier_id,route_id,source_order_version,reason_code,reason_text,decided_by,created_at)
                    select md5('actions-'||n)::uuid,?,?,?,1,'OPTIMIZED','Bulk','SYSTEM','2099-11-09T09:00:00Z' from generate_series(100,299) n
                    """,PLAN,id(0x200,1),id(0x500,1));
            jdbc.update("""
                    insert into route_stop(route_id,sequence,type,order_id,arrival_eta,departure_eta,distance_from_prev_m,window_violation_s)
                    select ?,n,'ORDER',md5('actions-'||n)::uuid,'2099-11-09T13:00:00Z','2099-11-09T13:10:00Z',100,0 from generate_series(100,299) n
                    """,id(0x500,1));
            jdbc.update("update business_event set payload=jsonb_set(payload,'{order_ids}',(payload->'order_ids')||(select jsonb_agg(md5('actions-'||n)::uuid::text) from generate_series(100,299) n)) where id=?",id(0x700,1));
            jdbc.update("insert into alert_order(alert_id,order_id,status) select ?,md5('actions-'||n)::uuid,'pending' from generate_series(100,299) n",id(0x800,3));
            assertThat(queryCounts(stats)).isEqualTo(before);
            assertThat(schedules.get(id(0x200,1),DATE,"repo-representative").orders()).hasSize(204);
            System.out.println("Action SQL queries before/after 200 extra pending orders: "+before);
        } finally {stats.setStatisticsEnabled(enabled);}
    }
    private List<Long> queryCounts(org.hibernate.stat.Statistics stats) {
        var counts=new ArrayList<Long>();
        entityManager.clear();stats.clear();orders.get(DATE,null,null,null,null,1,100);counts.add(stats.getPrepareStatementCount());
        entityManager.clear();stats.clear();group();counts.add(stats.getPrepareStatementCount());
        entityManager.clear();stats.clear();schedules.get(id(0x200,1),DATE,"repo-representative");counts.add(stats.getPrepareStatementCount());
        return counts;
    }
    private Map<String,String> digests() {
        var result=new LinkedHashMap<String,String>();
        for(String table:List.of("office","courier","service_order","planning_run","route","route_stop","assignment","app_user","business_event","alert","alert_order","api_command","scenario_state"))
            result.put(table,jdbc.queryForObject("select md5(coalesce(string_agg(row_to_json(t)::text,',' order by row_to_json(t)::text),'')) from "+table+" t",String.class));
        return result;
    }
}

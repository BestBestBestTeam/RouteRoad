package com.routeroad;

import com.routeroad.orders.application.OrderReader;
import com.routeroad.orders.api.OrderView;
import com.routeroad.planning.infrastructure.RouteRepository;
import com.routeroad.planning.infrastructure.RouteStopId;
import com.routeroad.planning.infrastructure.RouteStopRepository;
import com.routeroad.simulation.infrastructure.ScenarioStateRepository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@SpringBootTest
@ActiveProfiles("local")
@EnabledIfEnvironmentVariable(named = "ROUTEROAD_REPOSITORY_TESTS", matches = "true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Transactional
@Rollback
@Sql({"/repository-fixtures.sql", "/workday-fixtures.sql", "/courier-fixtures.sql"})
class CourierScheduleRepositoryPostgresTests {
    private static final LocalDate DATE = LocalDate.of(2099, 11, 9);
    private static final long PLAN = 9_000_000_000_000_000_001L;
    @Autowired OrderReader orders;
    @Autowired RouteRepository routes;
    @Autowired RouteStopRepository stops;
    @Autowired ScenarioStateRepository scenarios;
    @Autowired JdbcTemplate jdbc;
    private static UUID id(int group, int number) {
        return UUID.fromString("a0f8d%03x-0000-4000-8000-%012x".formatted(group, number));
    }
    @BeforeAll void preserveExistingScenario() {
        assumeTrue(scenarios.count() == 0, "Existing scenario must be preserved");
    }
    @Test void scopedOrdersAgreeWithSharedListIncludingPartialPlansAndExplicitUnassignment() {
        for (int n = 1; n <= 5; n++) {
            var courier = id(0x200, n);
            assertThat(orders.findSchedule(courier, DATE).stream().map(OrderReader.ScheduledOrder::order).toList())
                    .isEqualTo(orders.find(new OrderReader.Filter(DATE, null, null, courier, true), 100, 0, false).items());
        }
        assertThat(orders.findSchedule(id(0x200, 1), DATE)).extracting(o -> o.order().id())
                .containsExactlyInAnyOrder(id(0x300, 1), id(0x300, 9), id(0x300, 10));
        assertThat(orders.findSchedule(id(0x200, 1), DATE.plusDays(1))).isEmpty();
    }
    @Test void refusalsAreReadWithOrdersInOneQuery() {
        var initial = orders.findSchedule(id(0x200, 1), DATE);
        assertThat(initial).filteredOn(o -> o.order().id().equals(id(0x300, 1)))
                .allSatisfy(o -> assertThat(o.refusals()).isEqualTo(new OrderReader.Refusals(true, false)));
        jdbc.update("insert into business_event(id,type,service_date,courier_id,occurred_at,created_at,reason,payload) values(?,'COURIER_REFUSED',?,?,'2099-11-09T10:00:00Z','2099-11-09T10:00:00Z','Refused','{}')",
                id(0x700, 30), DATE, id(0x200, 1));
        assertThat(orders.findSchedule(id(0x200, 1), DATE)).allSatisfy(o -> assertThat(o.refusals().dayRefused()).isTrue());
    }
    @Test void activeRoutesCanHaveDifferentVersionsAndOfficeOnlyStops() {
        assertThat(routes.findConfirmedActive(id(0x200, 1), DATE).orElseThrow().getPlanningRun().getPlanVersion()).isEqualTo(PLAN);
        assertThat(routes.findConfirmedActive(id(0x200, 2), DATE).orElseThrow().getPlanningRun().getPlanVersion()).isEqualTo(PLAN + 1);
        assertThat(stops.findById_RouteIdOrderById_SequenceAsc(id(0x500, 2))).hasSize(1);
        assertThat(routes.findConfirmedActive(id(0x200, 5), DATE)).isEmpty();
    }
    @Test void activeFlagWithoutJavaConfirmationIsInsufficient() {
        jdbc.update("update route set is_active=false where id=?", id(0x500, 1));
        jdbc.update("update route set is_active=true where id=?", id(0x500, 3));
        assertThat(routes.findConfirmedActive(id(0x200, 1), DATE)).isEmpty();
        assertThat(orders.findSchedule(id(0x200, 1), DATE).stream().map(OrderReader.ScheduledOrder::order))
                .extracting(o -> o.assignment().planVersion()).containsOnly(PLAN);
    }
    @Test void stopOrderAndCompositeKeysArePreserved() {
        assertThat(stops.findById_RouteIdOrderById_SequenceAsc(id(0x500, 1)))
                .extracting(s -> s.getId().getSequence()).containsExactly(1, 2, 3);
        assertThat(stops.findById(new RouteStopId(id(0x500, 1), 1)).orElseThrow().getOfficeId()).isEqualTo(id(0x100, 1));
        assertThat(stops.findById(new RouteStopId(id(0x500, 2), 1)).orElseThrow().getOfficeId()).isEqualTo(id(0x100, 2));
    }
    @Test void newerActivationCanMoveAnOrderAwayFromCandidateCourier() {
        jdbc.update("insert into assignment(order_id,plan_version,courier_id,route_id,source_order_version,reason_code,reason_text,decided_by,created_at) values(?,?,?,?,2,'OPTIMIZED','Transferred','SYSTEM','2099-11-09T09:02:00Z')",
                id(0x300, 1), PLAN + 1, id(0x200, 2), id(0x500, 2));
        jdbc.update("update business_event set payload=jsonb_set(payload,'{order_ids}',(payload->'order_ids') || to_jsonb(cast(? as text))) where id=?",
                id(0x300, 1).toString(), id(0x700, 2));
        assertThat(orders.findSchedule(id(0x200, 1), DATE)).noneSatisfy(o -> assertThat(o.order().id()).isEqualTo(id(0x300, 1)));
        assertThat(orders.findSchedule(id(0x200, 2), DATE)).anySatisfy(o -> assertThat(o.order().id()).isEqualTo(id(0x300, 1)));
    }
    @Test void activationCreatedAtWinsOverPlanVersionAndModelTime() {
        jdbc.update("insert into assignment(order_id,plan_version,courier_id,route_id,source_order_version,reason_code,reason_text,decided_by,created_at) values(?,?,?,?,2,'OPTIMIZED','Transferred','SYSTEM','2099-11-09T12:00:00Z')",
                id(0x300, 1), PLAN + 1, id(0x200, 2), id(0x500, 2));
        jdbc.update("update business_event set created_at='2099-11-09T08:59:00Z',payload=jsonb_set(payload,'{order_ids}',(payload->'order_ids') || to_jsonb(cast(? as text))) where id=?",
                id(0x300, 1).toString(), id(0x700, 2));
        assertThat(orders.findSchedule(id(0x200, 1), DATE)).anySatisfy(o -> {
            assertThat(o.order().id()).isEqualTo(id(0x300, 1));
            assertThat(o.order().assignment().planVersion()).isEqualTo(PLAN);
        });
        assertThat(orders.findSchedule(id(0x200, 2), DATE)).noneSatisfy(o -> assertThat(o.order().id()).isEqualTo(id(0x300, 1)));
    }

}

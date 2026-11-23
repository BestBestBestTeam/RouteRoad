package com.routeroad;

import com.routeroad.couriers.infrastructure.CourierRepository;
import com.routeroad.execution.domain.AlertStatus;
import com.routeroad.execution.domain.BusinessEventType;
import com.routeroad.execution.infrastructure.AlertOrderId;
import com.routeroad.execution.infrastructure.AlertOrderRepository;
import com.routeroad.execution.infrastructure.AlertRepository;
import com.routeroad.execution.infrastructure.BusinessEventEntity;
import com.routeroad.execution.infrastructure.BusinessEventRepository;
import com.routeroad.offices.infrastructure.OfficeEntity;
import com.routeroad.offices.infrastructure.OfficeRepository;
import com.routeroad.orders.domain.ServiceOrderStatus;
import com.routeroad.orders.infrastructure.ServiceOrderEntity;
import com.routeroad.orders.infrastructure.ServiceOrderRepository;
import com.routeroad.planning.infrastructure.AssignmentId;
import com.routeroad.planning.infrastructure.AssignmentRepository;
import com.routeroad.planning.infrastructure.PlanningRunRepository;
import com.routeroad.planning.infrastructure.RouteRepository;
import com.routeroad.planning.infrastructure.RouteStopId;
import com.routeroad.planning.infrastructure.RouteStopRepository;
import com.routeroad.shared.infrastructure.ApiCommandRepository;
import com.routeroad.shared.infrastructure.AppUserRepository;
import com.routeroad.simulation.infrastructure.ScenarioStateRepository;
import jakarta.persistence.TransactionRequiredException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.core.support.AbstractRepositoryMetadata;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("local")
@EnabledIfEnvironmentVariable(named = "ROUTEROAD_REPOSITORY_TESTS", matches = "true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Transactional
@Rollback
@Sql("/repository-fixtures.sql")
class RepositoriesPostgresTests {

    private static final LocalDate DATE = LocalDate.of(2099, 11, 9);
    private static final long PLAN_ONE = 9_000_000_000_000_000_001L;
    private static final long PLAN_TWO = PLAN_ONE + 1;
    private static final long PLAN_THREE = PLAN_ONE + 2;

    @Autowired private ApplicationContext context;
    @Autowired private OfficeRepository offices;
    @Autowired private CourierRepository couriers;
    @Autowired private ServiceOrderRepository orders;
    @Autowired private PlanningRunRepository runs;
    @Autowired private RouteRepository routes;
    @Autowired private RouteStopRepository stops;
    @Autowired private AssignmentRepository assignments;
    @Autowired private AppUserRepository users;
    @Autowired private BusinessEventRepository events;
    @Autowired private AlertRepository alerts;
    @Autowired private AlertOrderRepository alertOrders;
    @Autowired private ApiCommandRepository commands;
    @Autowired private ScenarioStateRepository scenarios;

    private static UUID id(int group, int number) {
        return UUID.fromString("a0f8d%03x-0000-4000-8000-%012x".formatted(group, number));
    }

    @BeforeAll
    void requireNoActiveScenario() {
        assumeTrue(scenarios.count() == 0,
                "Repository fixtures need an empty local scenario_state; existing data must be preserved");
    }

    @Test
    void repositoriesAreCreatedAndUseActualPrimaryKeys() {
        assertThat(context.getBeansOfType(Repository.class)).hasSize(13);
        var types = Map.ofEntries(
                Map.entry(OfficeRepository.class, UUID.class),
                Map.entry(CourierRepository.class, UUID.class),
                Map.entry(ServiceOrderRepository.class, UUID.class),
                Map.entry(PlanningRunRepository.class, Long.class),
                Map.entry(RouteRepository.class, UUID.class),
                Map.entry(RouteStopRepository.class, RouteStopId.class),
                Map.entry(AssignmentRepository.class, AssignmentId.class),
                Map.entry(AppUserRepository.class, UUID.class),
                Map.entry(BusinessEventRepository.class, UUID.class),
                Map.entry(AlertRepository.class, UUID.class),
                Map.entry(AlertOrderRepository.class, AlertOrderId.class),
                Map.entry(ApiCommandRepository.class, UUID.class),
                Map.entry(ScenarioStateRepository.class, UUID.class));
        types.forEach((type, key) ->
                assertThat(AbstractRepositoryMetadata.getMetadata(type).getIdType()).isEqualTo(key));

        assertThat(offices.findById(id(0x100, 1))).isPresent();
        assertThat(couriers.findById(id(0x200, 1))).isPresent();
        assertThat(orders.findById(id(0x300, 1))).isPresent();
        assertThat(runs.findById(PLAN_ONE)).isPresent();
        assertThat(routes.findById(id(0x500, 1))).isPresent();
        assertThat(stops.findById(new RouteStopId(id(0x500, 1), 2))).isPresent();
        assertThat(assignments.findById(new AssignmentId(id(0x300, 1), PLAN_ONE))).isPresent();
        assertThat(users.findById(id(0x600, 1))).isPresent();
        assertThat(events.findById(id(0x700, 1))).isPresent();
        assertThat(alerts.findById(id(0x800, 1))).isPresent();
        assertThat(alertOrders.findById(new AlertOrderId(id(0x800, 1), id(0x300, 1)))).isPresent();
        assertThat(commands.findById(id(0x900, 1))).isPresent();
        assertThat(scenarios.findById(id(0xb00, 1))).isPresent();
        assertThat(assignments.findById(new AssignmentId(id(0x300, 1), PLAN_TWO))).isEmpty();
        assertThat(alertOrders.findById(new AlertOrderId(id(0x800, 1), id(0x300, 2)))).isEmpty();
    }

    @Test
    void usersOrdersAndCouriersSupportLookupFiltersAndPaging() {
        assertThat(users.findByLogin("repo-dispatcher")).get()
                .extracting(user -> user.getId()).isEqualTo(id(0x600, 1));
        assertThat(users.findByCourierId(id(0x200, 1))).get()
                .extracting(user -> user.getId()).isEqualTo(id(0x600, 2));
        assertThat(users.existsByLogin("repo-dispatcher")).isTrue();
        assertThat(users.existsByLogin("repo-missing")).isFalse();
        assertThat(users.existsByCourierId(id(0x200, 1))).isTrue();
        assertThat(users.existsByCourierId(id(0x200, 2))).isFalse();
        assertThat(orders.findByNumber("repo-A")).get()
                .extracting(order -> order.getId()).isEqualTo(id(0x300, 1));

        var first = orders.findByServiceDate(DATE, PageRequest.of(0, 1, Sort.by("number")));
        assertThat(first.getTotalElements()).isEqualTo(2);
        assertThat(first.getContent()).extracting(order -> order.getNumber()).containsExactly("repo-A");
        assertThat(orders.findByServiceDate(DATE, first.nextPageable()).getContent())
                .extracting(order -> order.getNumber()).containsExactly("repo-B");
        assertThat(orders.findByServiceDate(DATE.plusDays(1), PageRequest.of(0, 10)).getContent())
                .extracting(order -> order.getNumber()).containsExactly("repo-C");
        assertThat(couriers.findByOfficeId(id(0x100, 1), PageRequest.of(0, 10)).getContent())
                .extracting(courier -> courier.getId()).containsExactly(id(0x200, 1));
        assertThat(couriers.findAll(PageRequest.of(0, 1)).getTotalElements()).isEqualTo(2);

        var office = new OfficeEntity(id(0x100, 100), "Repo inserted office", "Repo address", 55.0, 82.0);
        var savedOffice = offices.saveAndFlush(office);
        assertThat(savedOffice).isNotSameAs(office);
        assertThat(offices.findById(office.getId())).isPresent();
        var order = new ServiceOrderEntity(id(0x300, 100), "repo-inserted", "Repo client",
                "Repo address", null, "Card", 55.0, 82.0, Instant.EPOCH,
                Instant.EPOCH.plusSeconds(3600), 600, ServiceOrderStatus.NEW, DATE.plusDays(2));
        assertThat(orders.saveAndFlush(order)).isSameAs(order);
        assertThat(order.getVersion()).isZero();
        var event = new BusinessEventEntity(id(0x700, 100), BusinessEventType.DATA_LOADED,
                DATE.plusDays(2), null, null, null, Instant.EPOCH, Instant.EPOCH,
                "Repository insert", Map.of());
        events.save(event);
        assertThat(events.findByServiceDate(DATE.plusDays(2), PageRequest.of(0, 10)).getContent())
                .extracting(item -> item.getId()).containsExactly(event.getId());
    }

    @Test
    void planningQueriesKeepVersionsSeparateAndSortStops() {
        assertThat(runs.findByRequestId(id(0x400, 3))).get()
                .extracting(run -> run.getPlanVersion()).isEqualTo(PLAN_THREE);
        assertThat(routes.findByCourierIdAndPlanDateAndActiveTrue(id(0x200, 1), DATE)).get()
                .extracting(route -> route.getId()).isEqualTo(id(0x500, 1));
        assertThat(routes.findByPlanDateAndActiveTrue(DATE))
                .extracting(route -> route.getId()).containsExactlyInAnyOrder(id(0x500, 1), id(0x500, 2));
        assertThat(routes.findByPlanningRun_PlanVersion(PLAN_THREE))
                .extracting(route -> route.getId()).containsExactly(id(0x500, 3));
        assertThat(stops.findById_RouteIdOrderById_SequenceAsc(id(0x500, 1)))
                .extracting(stop -> stop.getId().getSequence()).containsExactly(1, 2, 3);

        var history = assignments.findById_OrderIdOrderById_PlanVersionDesc(
                id(0x300, 1), PageRequest.of(0, 10));
        assertThat(history.getContent()).extracting(assignment -> assignment.getId().getPlanVersion())
                .containsExactly(PLAN_THREE, PLAN_ONE);
        assertThat(assignments.findById_PlanVersion(PLAN_THREE))
                .extracting(assignment -> assignment.getOrderId()).containsExactly(id(0x300, 1));
        var unassigned = assignments.findById(new AssignmentId(id(0x300, 2), PLAN_TWO)).orElseThrow();
        assertThat(unassigned.getCourierId()).isNull();
        assertThat(unassigned.getRouteId()).isNull();
    }

    @Test
    void executionAndCommandQueriesPreserveAlertKeysAndNullActorContext() {
        assertThat(events.findByServiceDate(DATE, PageRequest.of(0, 10)).getTotalElements()).isEqualTo(3);
        assertThat(events.findByOrderId(id(0x300, 1), PageRequest.of(0, 10)).getTotalElements()).isEqualTo(2);
        assertThat(alerts.findByServiceDate(DATE, PageRequest.of(0, 10)).getTotalElements()).isEqualTo(2);
        assertThat(alerts.findByServiceDateAndStatus(DATE, AlertStatus.OPEN, PageRequest.of(0, 10)).getContent())
                .extracting(alert -> alert.getId()).containsExactly(id(0x800, 1));
        assertThat(alertOrders.findById_AlertId(id(0x800, 1)))
                .extracting(item -> item.getId().getOrderId()).containsExactly(id(0x300, 1));
        assertThat(alertOrders.findById_OrderId(id(0x300, 2)))
                .extracting(item -> item.getId().getAlertId()).containsExactly(id(0x800, 2));

        var key = id(0xa00, 1);
        assertThat(commands.findByActorIdAndIdempotencyKey(null, key)).get()
                .extracting(command -> command.getId()).isEqualTo(id(0x900, 1));
        assertThat(commands.findByActorIdAndIdempotencyKey(id(0x600, 1), key)).get()
                .extracting(command -> command.getId()).isEqualTo(id(0x900, 2));
        assertThat(commands.findByActorIdAndIdempotencyKey(id(0x600, 2), key)).isEmpty();
    }

    @Test
    void routeUpdateChangesOnlyActiveAndRequiresCallerTransaction() {
        var original = routes.findById(id(0x500, 3)).orElseThrow();
        var originalFields = List.of(original.getCourierId(), original.getPlanDate(),
                original.getPlanningRun().getPlanVersion(), original.getAlgorithm(),
                original.getTotalDistanceM(), original.getViolatedWindows(), original.getCreatedAt());
        assertThat(routes.setActive(id(0x500, 1), false)).isEqualTo(1);
        assertThat(routes.setActive(id(0x500, 3), true)).isEqualTo(1);
        var updated = routes.findById(id(0x500, 3)).orElseThrow();
        assertThat(updated.getActive()).isTrue();
        assertThat(List.of(updated.getCourierId(), updated.getPlanDate(),
                updated.getPlanningRun().getPlanVersion(), updated.getAlgorithm(),
                updated.getTotalDistanceM(), updated.getViolatedWindows(), updated.getCreatedAt()))
                .isEqualTo(originalFields);
        assertThat(routes.findByCourierIdAndPlanDateAndActiveTrue(id(0x200, 1), DATE)).get()
                .extracting(route -> route.getId()).isEqualTo(id(0x500, 3));

        TestTransaction.end();
        assertThatThrownBy(() -> routes.setActive(id(0x500, 3), false))
                .hasRootCauseInstanceOf(TransactionRequiredException.class);
    }
}

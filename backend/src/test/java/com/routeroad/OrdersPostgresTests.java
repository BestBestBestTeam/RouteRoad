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
@Sql({"/repository-fixtures.sql", "/workday-fixtures.sql"})
class OrdersPostgresTests {
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
    @Test void fullHttpContractAndLargeVersions() throws Exception {
        long large = 9_007_199_254_740_993L;
        jdbc.update("update service_order set version=? where id=?", large, id(0x300, 1));
        jdbc.update("update assignment set source_order_version=? where order_id=? and plan_version=?", large, id(0x300, 1), PLAN);
        jdbc.update("update scenario_state set revision=?", large);
        var result = mvc.perform(get("/api/orders").session(login("repo-dispatcher"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(7)).andExpect(jsonPath("$.page").value(1)).andExpect(jsonPath("$.page_size").value(10))
                .andExpect(jsonPath("$.clock.revision").value("9007199254740993"))
                .andExpect(jsonPath("$.items[0].version").value("9007199254740993"))
                .andExpect(jsonPath("$.items[0].assignment.plan_version").value(Long.toString(PLAN)))
                .andExpect(jsonPath("$.items[0].assignment.source_order_version").value("9007199254740993"))
                .andExpect(jsonPath("$.items[0].client_name").value("Repo client A"))
                .andExpect(jsonPath("$.items[0].assignment.courier_name").value("Repo courier A"))
                .andExpect(jsonPath("$.items[0].assignment.actor.name").value("Repo dispatcher"))
                .andExpect(jsonPath("$.items[0].assignment.actor.role").value("dispatcher")).andReturn();
        var body = json.readTree(result.getResponse().getContentAsString());
        assertThat(body.size()).isEqualTo(5);
        assertThat(body.get("items").get(0).size()).isEqualTo(17);
        assertThat(body.get("items").get(0).get("version").isString()).isTrue();
        assertThat(result.getResponse().getContentAsString()).doesNotContain("password", hash);
    }
    @Test void paginationAndStableOrderingAreAppliedInSql() {
        var first = list(DATE, null, null, null, null, 1, 2);
        var second = list(DATE, null, null, null, null, 2, 2);
        assertThat(first.total()).isEqualTo(7);
        assertThat(first.items()).extracting(OrderView::number).containsExactly("repo-A", "repo-B");
        assertThat(second.items()).extracting(OrderView::number).containsExactly("workday-4", "workday-5");
        assertThat(list(DATE, null, null, null, null, 2, 2).items()).isEqualTo(second.items());
        var beyond = list(DATE, null, null, null, null, 100, 2);
        assertThat(beyond.items()).isEmpty(); assertThat(beyond.total()).isEqualTo(7);
    }
    @Test void searchUsesNumberClientAddressDistrictProductAndEscapesWildcards() {
        assertThat(list(DATE, "REPO-a", null, null, null, 1, 10).items()).extracting(OrderView::number).containsExactly("repo-A");
        jdbc.update("update service_order set district='Центральный район',product='Особая карта',address='Улица Тестовая 5',number='literal_%!' where id=?", id(0x300, 8));
        for (String search : List.of("центральный", "ОСОБАЯ", "тестовая", "_", "%", "!")) {
            assertThat(list(DATE, search, null, null, null, 1, 10).items()).extracting(OrderView::id).containsExactly(id(0x300, 8));
        }
        assertThat(list(DATE, "client a", null, null, null, 1, 10).total()).isEqualTo(1);
        assertThat(list(DATE, "' OR 1=1 --", null, null, null, 1, 10).items()).isEmpty();
    }
    @Test void statusUsesDayStatusAndTransfersAreUnique() {
        var moved = list(DATE, null, ServiceOrderStatus.RESCHEDULED, null, null, 1, 10);
        assertThat(moved.total()).isEqualTo(1);
        assertThat(moved.items().getFirst().status()).isEqualTo(ServiceOrderStatus.NEW);
        assertThat(moved.items().getFirst().dayStatus()).isEqualTo(ServiceOrderStatus.RESCHEDULED);
        assertThat(list(DATE, null, ServiceOrderStatus.NEW, null, null, 1, 10).total()).isZero();
        assertThat(list(DATE.plusDays(1), null, ServiceOrderStatus.NEW, null, null, 1, 10).total()).isEqualTo(2);
        assertThat(all().items()).extracting(OrderView::id).doesNotHaveDuplicates();
    }
    @Test void assignedAndCourierFiltersAgreeWithWorkdayCounts() {
        var counts = workday.get(DATE).counts();
        assertThat(all().total()).isEqualTo(counts.ordersTotal());
        assertThat(list(DATE, null, null, null, true, 1, 100).total()).isEqualTo(counts.ordersAssigned());
        assertThat(list(DATE, null, null, null, false, 1, 100).total()).isEqualTo(counts.ordersTotal()-counts.ordersAssigned());
        assertThat(list(DATE, null, ServiceOrderStatus.COMPLETED, null, null, 1, 100).total()).isEqualTo(counts.ordersCompleted());
        assertThat(list(DATE, null, null, id(0x200, 1), null, 1, 10).items()).extracting(OrderView::number).containsExactly("repo-A");
        assertThat(list(DATE, null, null, id(0x200, 2), true, 1, 10).items()).extracting(OrderView::number).containsExactly("workday-4");
        assertThat(list(DATE, null, null, id(0x200, 2), false, 1, 10).items()).isEmpty();
    }
    @Test void filteringPrecedesPagination() {
        var page = list(DATE, null, ServiceOrderStatus.UNASSIGNED, null, null, 1, 1);
        assertThat(page.total()).isEqualTo(2); assertThat(page.items()).hasSize(1);
        assertThat(list(DATE, null, ServiceOrderStatus.UNASSIGNED, null, null, 2, 1).items()).extracting(OrderView::number).containsExactly("workday-7");
    }
    @Test void newAndUnconfirmedOrdersHaveNoAssignment() {
        assertThat(order(6).assignment()).isNull();
        assertThat(order(8).assignment()).isNull(); // run is confirmed, but does not list this order
        assertThat(list(DATE.plusDays(1), null, null, null, null, 1, 10).items()).allSatisfy(o -> assertThat(o.assignment()).isNull());
    }
    @Test void explicitUnassignedDecisionIsNotNullAndHasSystemActor() {
        var assignment = order(2).assignment();
        assertThat(assignment).isNotNull();
        assertThat(assignment.courierId()).isNull(); assertThat(assignment.routeId()).isNull();
        assertThat(assignment.courierName()).isNull();
        assertThat(assignment.actor().id()).isNull();
        assertThat(assignment.actor().name()).isEqualTo("Система");
        assertThat(assignment.actor().role()).isEqualTo(Actor.Role.SYSTEM);
        assertThat(assignment.reasonText()).isEqualTo("Repo unassigned decision");
        assertThat(order(7).assignment().courierId()).isNull(); // replaces the old assigned decision
    }
    @Test void partialRunsAndUnconfirmedNewerRunKeepOlderConfirmedDecision() {
        assertThat(order(1).assignment().planVersion()).isEqualTo(PLAN);
        assertThat(order(4).assignment().planVersion()).isEqualTo(PLAN+1);
        assertThat(order(1).assignment().actor().id()).isEqualTo(id(0x600, 1));
    }
    @Test void laterCreatedAtWinsWithIdenticalSourceVersionAndOccurredAt() {
        confirmSecondPlanForFirstOrder(1);
        jdbc.update("update business_event set occurred_at='2099-11-09T09:00:00Z' where type='PLAN_ACTIVATED'");
        var chosen = order(1).assignment();
        assertThat(chosen.planVersion()).isEqualTo(PLAN+1);
        assertThat(chosen.courierId()).isNull(); assertThat(chosen.reasonText()).isEqualTo("Нет вместимости");
        assertThat(chosen.actor().role()).isEqualTo(Actor.Role.SYSTEM);
        assertThat(workday.get(DATE).counts().ordersAssigned()).isEqualTo(1);
        assertThat(list(DATE,null,null,null,true,1,100).total()).isEqualTo(1);
    }
    @Test void laterCreatedAtCanSelectSmallerPlanVersionAndLowerSourceVersion() {
        confirmSecondPlanForFirstOrder(100);
        jdbc.update("update business_event set created_at='2099-11-09T09:03:00Z',occurred_at='2099-11-09T08:00:00Z' where id=?", id(0x700, 1));
        assertThat(order(1).assignment().planVersion()).isEqualTo(PLAN);
        assertThat(order(1).assignment().sourceOrderVersion()).isEqualTo(1);
        assertThat(order(1).assignment().courierId()).isEqualTo(id(0x200, 1));
        assertThat(workday.get(DATE).counts().ordersAssigned()).isEqualTo(3);
    }
    @Test void equalCreatedAtIsOnlyRepeatableNotAChronologicalGuarantee() {
        confirmSecondPlanForFirstOrder(1);
        jdbc.update("update business_event set created_at='2099-11-09T09:00:00Z' where type='PLAN_ACTIVATED'");
        var selected = order(1).assignment();
        assertThat(order(1).assignment()).isEqualTo(selected);
        assertThat(list(DATE,null,null,null,true,1,100).total()).isEqualTo(workday.get(DATE).counts().ordersAssigned());
        // No assertion about which activation happened last: equal real timestamps cannot establish it.
    }
    @Test void actionsOnlyOfferDispatcherReassignmentForPendingOpenAlert() {
        assertThat(order(1).availableActions()).containsExactly("REASSIGN");
        for (int n : new int[]{2,4,5,6,7,8}) assertThat(order(n).availableActions()).isEmpty();
        jdbc.update("update service_order set status='IN_PROGRESS' where id=?", id(0x300,1));
        assertThat(order(1).availableActions()).isEmpty();
        jdbc.update("update service_order set status='CANCELLED' where id=?", id(0x300,1));
        assertThat(order(1).availableActions()).isEmpty();
        assertThat(list(DATE.plusDays(1),null,null,null,null,1,100).items()).allSatisfy(o -> assertThat(o.availableActions()).isEmpty());
    }
    @Test void resolvedAlertElementDoesNotOfferReassignment() {
        jdbc.update("update alert_order set status='resolved',resolved_at='2099-11-09T09:05:00Z' where alert_id=?",id(0x800,1));
        assertThat(order(1).availableActions()).isEmpty();
    }
    @Test void invalidParametersHaveJsonErrors() throws Exception {
        var session=login("repo-dispatcher");
        for (var param : List.of(Map.entry("date","2099-02-30"),Map.entry("date",""),Map.entry("page","0"),Map.entry("page","-1"),
                Map.entry("page","1.5"),Map.entry("page","2147483648"),Map.entry("page_size","0"),Map.entry("page_size","101"),
                Map.entry("status","UNKNOWN"),Map.entry("status","new"),Map.entry("courier_id","invalid"),Map.entry("assigned","yes"),
                Map.entry("assigned","1"),Map.entry("page_size",""))) {
            mvc.perform(get("/api/orders").param(param.getKey(),param.getValue()).session(session))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
        }
    }
    @Test void authenticationAndMissingScenario() throws Exception {
        mvc.perform(get("/api/orders")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/orders").session(login("repo-representative"))).andExpect(status().isForbidden());
        scenarios.deleteAll();scenarios.flush();
        mvc.perform(get("/api/orders").session(login("repo-dispatcher"))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("SCENARIO_NOT_INITIALIZED"));
        assertThat(scenarios.count()).isZero();
    }
    @Test @Sql("/empty-workday-fixture.sql")
    void emptyScenarioAndFarPageReturnAnEmptyList() throws Exception {
        mvc.perform(get("/api/orders").session(login("workday-empty-dispatcher"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty()).andExpect(jsonPath("$.total").value(0));
    }
}

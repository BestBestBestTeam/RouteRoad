package com.routeroad;

import com.routeroad.shared.infrastructure.AppUserRepository;
import com.routeroad.simulation.infrastructure.ScenarioStateRepository;
import com.routeroad.planning.infrastructure.RouteRepository;
import com.routeroad.workday.application.WorkdayService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.TestInstance;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.time.Instant;
import java.time.LocalDate;
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
class WorkdayPostgresTests {
    private static final LocalDate DATE = LocalDate.of(2099, 11, 9);
    private static final long PLAN = 9_000_000_000_000_000_001L;
    private final String password = UUID.randomUUID().toString();
    private String hash;
    @Autowired WorkdayService service;
    @Autowired MockMvc mvc;
    @Autowired JsonMapper json;
    @Autowired PasswordEncoder passwords;
    @Autowired ScenarioStateRepository scenarios;
    @Autowired RouteRepository routes;
    @Autowired AppUserRepository users;
    @Autowired JdbcTemplate jdbc;
    @Autowired jakarta.persistence.EntityManager entityManager;
    @MockitoBean java.time.Clock realClock;

    private static UUID id(int group, int number) {
        return UUID.fromString("a0f8d%03x-0000-4000-8000-%012x".formatted(group, number));
    }
    @BeforeAll void requireEmptyScenario() {
        assumeTrue(scenarios.count() == 0, "Existing scenario must be preserved; fixtures require empty local scenario");
        hash = passwords.encode(password);
    }
    @BeforeEach void setup() {
        when(realClock.instant()).thenReturn(Instant.parse("2099-11-09T09:00:10Z"));
        jdbc.update("update app_user set password_hash = ? where login in ('repo-dispatcher','repo-representative','workday-empty-dispatcher')", hash);
    }
    private MockHttpSession login(String login) throws Exception {
        var bootstrap = mvc.perform(get("/api/me")).andExpect(status().isUnauthorized()).andReturn();
        var session = (MockHttpSession) bootstrap.getRequest().getSession(false);
        mvc.perform(post("/api/auth/login").session(session).header("Origin", "http://localhost")
                .header("X-CSRF-Token", bootstrap.getResponse().getHeader("X-CSRF-Token"))
                .contentType("application/json").content(json.writeValueAsString(Map.of("login", login, "password", password))))
                .andExpect(status().isOk());
        return session;
    }
    @Test void fullHttpViewAggregatesAllOrdersTransfersAndConfirmedDecisions() throws Exception {
        jdbc.update("update scenario_state set revision = ?", 9_007_199_254_740_993L);
        var response = mvc.perform(get("/api/workday").session(login("repo-dispatcher")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.date").value("2099-11-09"))
                .andExpect(jsonPath("$.algorithm").value("greedy"))
                .andExpect(jsonPath("$.counts.orders_total").value(7)).andExpect(jsonPath("$.counts.orders_assigned").value(2))
                .andExpect(jsonPath("$.counts.orders_completed").value(1)).andExpect(jsonPath("$.counts.couriers_total").value(2))
                .andExpect(jsonPath("$.counts.open_alerts").value(1)).andExpect(jsonPath("$.has_routes").value(true))
                .andExpect(jsonPath("$.offices.length()").value(2)).andExpect(jsonPath("$.offices[0].address").value("Repo address A"))
                .andExpect(jsonPath("$.clock.now").value("2099-11-09T09:00:10Z"))
                .andExpect(jsonPath("$.clock.revision").value("9007199254740993")).andReturn();
        JsonNode body = json.readTree(response.getResponse().getContentAsString());
        assertThat(body.properties().stream().map(Map.Entry::getKey).toList()).containsExactlyInAnyOrder(
                "date", "counts", "has_routes", "algorithm", "offices", "clock");
        assertThat(body.get("clock").get("revision").isString()).isTrue();
    }
    @ParameterizedTest @ValueSource(strings = {"greedy", "ortools"})
    void algorithmComesFromConfigAndDoesNotChangeOtherFields(String algorithm) throws Exception {
        var before = service.get(DATE);
        jdbc.update("update scenario_state set config=jsonb_set(config,'{algorithm}',to_jsonb(cast(? as text)))", algorithm);
        // Direct fixture SQL bypasses the scenario entity loaded by the preceding baseline read.
        entityManager.clear();
        var stored = jdbc.queryForObject("select config::text from scenario_state", String.class);
        var after = service.get(DATE);
        assertThat(after.algorithm()).isEqualTo(algorithm);
        assertThat(after).usingRecursiveComparison().ignoringFields("algorithm").isEqualTo(before);
        mvc.perform(get("/api/workday").session(login("repo-dispatcher"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.algorithm").value(algorithm));
        assertThat(jdbc.queryForObject("select config::text from scenario_state", String.class)).isEqualTo(stored);
    }
    @ParameterizedTest @ValueSource(strings = {"null", "42", "true", "[]", "{}", "\"GREEDY\"", "\"\"", "\"invalid\""})
    void invalidPersistedAlgorithmReturns500AndDoesNotDefault(String value) throws Exception {
        jdbc.update("update scenario_state set config=jsonb_set(config,'{algorithm}',cast(? as jsonb))", value);
        var stored = jdbc.queryForObject("select config::text from scenario_state", String.class);
        mvc.perform(get("/api/workday").session(login("repo-dispatcher"))).andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("INTERNAL_ERROR")).andExpect(jsonPath("$.algorithm").doesNotExist());
        assertThat(jdbc.queryForObject("select config::text from scenario_state", String.class)).isEqualTo(stored);
    }
    @Test void invalidAlgorithmDoesNotChangeOtherGetContracts() throws Exception {
        jdbc.update("update scenario_state set config=cast(? as jsonb)", "{\"algorithm\":\"invalid\"}");
        var session = login("repo-dispatcher");
        for (String path : java.util.List.of("/api/orders", "/api/couriers", "/api/history"))
            mvc.perform(get(path).session(session)).andExpect(status().isOk());
    }
    @Test void explicitDateFiltersOrdersAndAlertsButUsesCurrentScenarioClock() throws Exception {
        mvc.perform(get("/api/workday").param("date", DATE.plusDays(1).toString()).session(login("repo-dispatcher")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.counts.orders_total").value(2))
                .andExpect(jsonPath("$.counts.orders_assigned").value(0)).andExpect(jsonPath("$.counts.orders_completed").value(0))
                .andExpect(jsonPath("$.counts.open_alerts").value(0)).andExpect(jsonPath("$.counts.couriers_total").value(2))
                .andExpect(jsonPath("$.has_routes").value(false)).andExpect(jsonPath("$.clock.now").value("2099-11-09T09:00:10Z"));
    }
    @Test void partialRecalculationKeepsMultipleConfirmedVersions() {
        assertThat(routes.findByPlanDateAndActiveTrue(DATE)).extracting(route -> route.getPlanningRun().getPlanVersion())
                .containsExactlyInAnyOrder(PLAN, PLAN + 1);
        var result = service.get(DATE);
        assertThat(result.hasRoutes()).isTrue();
        assertThat(result.counts().ordersAssigned()).isEqualTo(2);
        // No assignment for order 8 is listed in the activation order_ids; it is excluded.
        // The newer Python plan for order 1 is unconfirmed; its older decision remains current.
        // The latest confirmed decision for order 7 is unassigned; its older active route is not counted.
    }
    @Test void activeFlagAloneDoesNotConfirmPythonPlan() {
        routes.setActive(id(0x500, 1), false);
        routes.setActive(id(0x500, 2), false);
        routes.setActive(id(0x500, 3), true);
        assertThat(routes.findByPlanDateAndActiveTrue(DATE)).hasSize(1);
        var result = service.get(DATE);
        assertThat(result.hasRoutes()).isFalse();
        assertThat(result.counts().ordersAssigned()).isEqualTo(2);
    }
    @Test void routeActivityAloneDoesNotRewriteConfirmedAssignments() {
        routes.setActive(id(0x500, 1), false);
        routes.setActive(id(0x500, 2), false);
        var result = service.get(DATE);
        assertThat(result.hasRoutes()).isFalse();
        assertThat(result.counts().ordersAssigned()).isEqualTo(2);
        assertThat(result.counts().ordersTotal()).isEqualTo(7);
    }
    @Test void transferIsCountedOnceAndFutureCompletionDoesNotRewriteOriginalDay() {
        jdbc.update("update service_order set status='COMPLETED' where id = ?", id(0x300, 5));
        assertThat(service.get(DATE).counts().ordersTotal()).isEqualTo(7);
        assertThat(service.get(DATE).counts().ordersCompleted()).isEqualTo(1);
        assertThat(service.get(DATE.plusDays(1)).counts().ordersTotal()).isEqualTo(2);
        assertThat(service.get(DATE.plusDays(1)).counts().ordersCompleted()).isEqualTo(1);
    }
    @Test void newerConfirmedUnassignedDecisionSupersedesAnOlderActiveRoute() {
        // Confirm an unassigned decision for order 1. The old route stays active for other orders.
        jdbc.update("insert into assignment(order_id,plan_version,source_order_version,reason_code,reason_text,decided_by,created_at) "
                + "values(?,?,3,'NO_CAPACITY','No capacity','SYSTEM','2099-11-09T09:02:00Z')", id(0x300, 1), PLAN + 1);
        jdbc.update("update business_event set payload=jsonb_set(payload,'{order_ids}',(payload->'order_ids') || to_jsonb(cast(? as text))) where id = ?",
                id(0x300, 1).toString(), id(0x700, 2));
        assertThat(service.get(DATE).counts().ordersAssigned()).isEqualTo(1);
    }
    @Test void aNewDecisionCanReferToAnOlderStillActiveRoute() {
        jdbc.update("insert into assignment(order_id,plan_version,courier_id,route_id,source_order_version,reason_code,reason_text,decided_by,created_at) "
                + "values(?,?,?,?,3,'OPTIMIZED','Retained route','SYSTEM','2099-11-09T09:02:00Z')",
                id(0x300, 1), PLAN + 1, id(0x200, 1), id(0x500, 1));
        jdbc.update("update business_event set payload=jsonb_set(payload,'{order_ids}',(payload->'order_ids') || to_jsonb(cast(? as text))) where id = ?",
                id(0x300, 1).toString(), id(0x700, 2));
        assertThat(service.get(DATE).counts().ordersAssigned()).isEqualTo(2);
    }
    @Test void laterCreatedAtWinsEvenWithAnOlderSourceVersion() {
        // created_at is real activation event time; source version never orders decisions.
        jdbc.update("insert into assignment(order_id,plan_version,source_order_version,reason_code,reason_text,decided_by,created_at) "
                + "values(?,?,0,'NO_CAPACITY','Older snapshot','SYSTEM','2099-11-09T09:02:00Z')", id(0x300, 1), PLAN + 1);
        jdbc.update("update business_event set payload=jsonb_set(payload,'{order_ids}',(payload->'order_ids') || to_jsonb(cast(? as text))) where id = ?",
                id(0x300, 1).toString(), id(0x700, 2));
        assertThat(service.get(DATE).counts().ordersAssigned()).isEqualTo(1);
    }
    @Test void laterCreatedAtWinsWithIdenticalSourceVersionAndOccurredAt() {
        jdbc.update("insert into assignment(order_id,plan_version,source_order_version,reason_code,reason_text,decided_by,created_at) "
                + "values(?,?,1,'NO_CAPACITY','No capacity','SYSTEM','2099-11-09T09:02:00Z')", id(0x300, 1), PLAN + 1);
        jdbc.update("update business_event set payload=jsonb_set(payload,'{order_ids}',(payload->'order_ids') || to_jsonb(cast(? as text))) where id=?",
                id(0x300, 1).toString(), id(0x700, 2));
        jdbc.update("update business_event set occurred_at='2099-11-09T09:00:00Z' where type='PLAN_ACTIVATED'");
        assertThat(service.get(DATE).counts().ordersAssigned()).isEqualTo(1);
    }
    @Test void equalOccurredAtDoesNotOverrideDifferentCreatedAt() {
        jdbc.update("update business_event set occurred_at='2099-11-09T09:01:00Z' where type='PLAN_ACTIVATED'");
        assertThat(service.get(DATE).counts().ordersAssigned()).isEqualTo(2);
    }
    @Test void authorizationAndInvalidDateUseExistingSecurity() throws Exception {
        mvc.perform(get("/api/workday")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/workday").session(login("repo-representative"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/workday").param("date", "2099-02-30").session(login("repo-dispatcher")))
                .andExpect(status().isBadRequest());
    }
    @Test void absentScenarioIsAnErrorAndDoesNotCreateRows() throws Exception {
        scenarios.deleteAll(); scenarios.flush();
        mvc.perform(get("/api/workday").session(login("repo-dispatcher"))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("SCENARIO_NOT_INITIALIZED"));
        assertThat(scenarios.count()).isZero();
    }
    @Test
    @Sql(scripts = "/empty-workday-fixture.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void emptyExistingScenarioReturnsZeroCounts() throws Exception {
        mvc.perform(get("/api/workday").session(login("workday-empty-dispatcher"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.counts.orders_total").value(0)).andExpect(jsonPath("$.counts.orders_assigned").value(0))
                .andExpect(jsonPath("$.counts.orders_completed").value(0)).andExpect(jsonPath("$.counts.couriers_total").value(0))
                .andExpect(jsonPath("$.counts.open_alerts").value(0)).andExpect(jsonPath("$.has_routes").value(false))
                .andExpect(jsonPath("$.offices").isEmpty());
    }
}

package com.routeroad;

import com.routeroad.couriers.infrastructure.CourierRepository;
import com.routeroad.shared.domain.AppUserRole;
import com.routeroad.shared.infrastructure.AppUserEntity;
import com.routeroad.shared.infrastructure.AppUserRepository;
import com.routeroad.simulation.infrastructure.ScenarioStateRepository;
import com.routeroad.workday.application.WorkdayReader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("context-smoke")
class WorkdayHttpTests {
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    com.routeroad.execution.application.HistoryReader historyReader;

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    com.routeroad.execution.application.AlertReader alertReader;

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    com.routeroad.workday.application.WorkdayMapReader mapReader;

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    com.routeroad.couriers.application.CourierScheduleReader scheduleReader;

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    com.routeroad.execution.application.OrderAlerts orderAlerts;

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    com.routeroad.orders.application.OrderReader orderReader;

    private static final UUID SCENARIO = UUID.randomUUID();
    private static final LocalDate DATE = LocalDate.of(2026, 11, 15);
    private static final String PASSWORD = UUID.randomUUID().toString();
    private static String hash;
    @MockitoBean AppUserRepository users;
    @MockitoBean CourierRepository couriers;
    @MockitoBean ScenarioStateRepository scenarios;
    @MockitoBean WorkdayReader reader;
    @MockitoBean com.routeroad.simulation.application.ScenarioReader scenarioReader;
    @MockitoBean java.time.Clock realClock;
    @Autowired MockMvc mvc;
    @Autowired JsonMapper json;
    @Autowired PasswordEncoder passwords;

    @BeforeEach void setup() {
        if (hash == null) hash = passwords.encode(PASSWORD);
        when(users.findByLogin("dispatcher")).thenReturn(Optional.of(new AppUserEntity(UUID.randomUUID(),
                "dispatcher", hash, "Dispatcher", AppUserRole.DISPATCHER, null)));
        when(users.findByLogin("representative")).thenReturn(Optional.of(new AppUserEntity(UUID.randomUUID(),
                "representative", hash, "Representative", AppUserRole.REPRESENTATIVE, UUID.randomUUID())));
        when(realClock.instant()).thenReturn(Instant.parse("2026-11-15T07:00:10Z"));
        when(scenarioReader.findCurrent()).thenReturn(Optional.of(new com.routeroad.simulation.application.ScenarioReader.Scenario(SCENARIO, DATE,
                Instant.parse("2026-11-15T07:00:00Z"), Instant.parse("2026-11-15T07:00:00Z"), 0.5, 9_007_199_254_740_993L, java.util.Map.of())));
        when(reader.count(any())).thenReturn(new WorkdayReader.Counts(10, 7, 2, 5, 3, true));
        when(reader.findOffices()).thenReturn(List.of(new WorkdayReader.Office(UUID.randomUUID(), "Office", "Address", 55, 82)));
    }
    private MockHttpSession login(String login) throws Exception {
        var bootstrap = mvc.perform(get("/api/me")).andExpect(status().isUnauthorized()).andReturn();
        var session = (MockHttpSession) bootstrap.getRequest().getSession(false);
        mvc.perform(post("/api/auth/login").session(session).header("Origin", "http://localhost")
                .header("X-CSRF-Token", bootstrap.getResponse().getHeader("X-CSRF-Token"))
                .contentType("application/json").content(json.writeValueAsString(Map.of("login", login, "password", PASSWORD))))
                .andExpect(status().isOk());
        return session;
    }
    @Test void dispatcherGetsExactContractWithoutCsrfAndRevisionIsString() throws Exception {
        var result = mvc.perform(get("/api/workday").session(login("dispatcher")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.date").value("2026-11-15"))
                .andExpect(jsonPath("$.counts.orders_total").value(10)).andExpect(jsonPath("$.counts.orders_assigned").value(7))
                .andExpect(jsonPath("$.counts.orders_completed").value(2)).andExpect(jsonPath("$.counts.couriers_total").value(5))
                .andExpect(jsonPath("$.counts.open_alerts").value(3)).andExpect(jsonPath("$.has_routes").value(true))
                .andExpect(jsonPath("$.clock.scenario_id").value(SCENARIO.toString()))
                .andExpect(jsonPath("$.clock.now").value("2026-11-15T07:00:05Z"))
                .andExpect(jsonPath("$.clock.speed").value(0.5)).andExpect(jsonPath("$.clock.revision").value("9007199254740993"))
                .andExpect(jsonPath("$.offices[0].address").value("Address")).andReturn();
        var body = json.readTree(result.getResponse().getContentAsString());
        assertThat(body.properties().stream().map(Map.Entry::getKey).toList()).containsExactlyInAnyOrder(
                "date", "counts", "has_routes", "algorithm", "offices", "clock");
        assertThat(body.get("algorithm").asString()).isEqualTo("greedy");
        assertThat(body.get("counts").size()).isEqualTo(5);
        assertThat(body.get("clock").size()).isEqualTo(4);
        assertThat(body.get("offices").get(0).size()).isEqualTo(5);
        assertThat(body.get("clock").get("revision").isString()).isTrue();
        verify(reader).count(DATE);
    }
    private void algorithm(Object value) {
        var current = scenarioReader.findCurrent().orElseThrow();
        when(scenarioReader.findCurrent()).thenReturn(Optional.of(new com.routeroad.simulation.application.ScenarioReader.Scenario(
                current.id(), current.serviceDate(), current.modelAnchorAt(), current.realAnchorAt(), current.speed(),
                current.revision(), java.util.Collections.singletonMap("algorithm", value))));
    }
    @Test void ortoolsIsReturnedFromScenarioConfiguration() throws Exception {
        algorithm("ortools");
        mvc.perform(get("/api/workday").session(login("dispatcher"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.algorithm").value("ortools"));
    }
    @Test void invalidServerAlgorithmIs500WithoutLeakingConfiguration() throws Exception {
        algorithm("private-invalid-setting");
        var response = mvc.perform(get("/api/workday").session(login("dispatcher"))).andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("INTERNAL_ERROR")).andReturn();
        assertThat(response.getResponse().getContentAsString()).doesNotContain("private-invalid-setting", "algorithm");
        verifyNoInteractions(reader);
    }
    @Test void optionalDateIsPassedToTheApplication() throws Exception {
        mvc.perform(get("/api/workday").param("date", "2026-11-16").session(login("dispatcher")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.date").value("2026-11-16"));
        verify(reader).count(DATE.plusDays(1));
    }
    @Test void missingScenarioReturnsDocumentedConflict() throws Exception {
        when(scenarioReader.findCurrent()).thenReturn(Optional.empty());
        mvc.perform(get("/api/workday").session(login("dispatcher"))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("SCENARIO_NOT_INITIALIZED"))
                .andExpect(jsonPath("$.error.details").isMap());
        verify(reader, never()).count(any());
    }
    @Test void invalidDatesReturnJsonBadRequest() throws Exception {
        var session = login("dispatcher");
        for (String date : new String[]{"tomorrow", "2026-02-30", "15-11-2026", "2026-11-15T00:00:00Z", ""}) {
            mvc.perform(get("/api/workday").param("date", date).session(session)).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
        }
    }
    @Test void noSessionReturnsUnauthorized() throws Exception {
        mvc.perform(get("/api/workday")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
        verifyNoInteractions(reader);
    }
    @Test void representativeIsForbidden() throws Exception {
        mvc.perform(get("/api/workday").session(login("representative"))).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        verifyNoInteractions(reader);
    }
    @Test void emptyExistingScenarioReturnsZerosAndEmptyOffices() throws Exception {
        when(reader.count(any())).thenReturn(new WorkdayReader.Counts(0, 0, 0, 0, 0, false));
        when(reader.findOffices()).thenReturn(List.of());
        mvc.perform(get("/api/workday").session(login("dispatcher"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.counts.orders_total").value(0)).andExpect(jsonPath("$.counts.couriers_total").value(0))
                .andExpect(jsonPath("$.has_routes").value(false)).andExpect(jsonPath("$.offices").isEmpty());
    }
}

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
class CourierScheduleHttpTests {
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

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    com.routeroad.couriers.application.CourierReader courierReader;
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
        when(orderReader.find(any(), anyInt(), anyLong(), anyBoolean(), any()))
                .thenReturn(new com.routeroad.orders.application.OrderReader.Result(List.of(), 0));
        when(courierReader.find(any(),anyInt(),anyLong()))
                .thenReturn(new com.routeroad.couriers.application.CourierReader.Result(List.of(sample(true)),1));
    }
    private com.routeroad.couriers.api.CourierView sample(boolean positioned) {
        return new com.routeroad.couriers.api.CourierView(UUID.randomUUID(),"Courier",UUID.randomUUID(),
                Instant.parse("2026-11-15T07:00:00Z"),Instant.parse("2026-11-15T16:00:00Z"),
                positioned ? new com.routeroad.shared.api.Position(55,82,Instant.parse("2026-11-15T07:00:00Z")) : null,
                true,4,1,2,List.of());
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
    private static final UUID COURIER = UUID.randomUUID();
    private static final UUID OFFICE = UUID.randomUUID();
    @BeforeEach void scheduleSetup() {
        when(users.findByLogin("representative")).thenReturn(Optional.of(new AppUserEntity(UUID.randomUUID(),
                "representative", hash, "Representative", AppUserRole.REPRESENTATIVE, COURIER)));
        var courier = new com.routeroad.couriers.application.CourierScheduleReader.Courier(COURIER, "Courier", OFFICE,
                Instant.parse("2026-11-15T07:00:00Z"), Instant.parse("2026-11-15T16:00:00Z"), null, true);
        when(scheduleReader.findCourier(COURIER)).thenReturn(Optional.of(courier));
        when(orderReader.findSchedule(eq(COURIER), any(), anyBoolean(), any())).thenReturn(List.of());
        when(scheduleReader.details(eq(courier), any(), anyList())).thenReturn(
                new com.routeroad.couriers.application.CourierScheduleReader.Details(null,
                        List.of(new com.routeroad.offices.api.OfficeView(OFFICE, "Office", "Address", 55, 82)), 0, false));
    }
    private String path(UUID id) { return "/api/couriers/" + id + "/schedule"; }
    @Test void dispatcherGetsAnEmptyScheduleWithoutCsrf() throws Exception {
        var result = mvc.perform(get(path(COURIER)).session(login("dispatcher"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.date").value(DATE.toString()))
                .andExpect(jsonPath("$.route").isEmpty()).andExpect(jsonPath("$.orders").isEmpty())
                .andExpect(jsonPath("$.courier.id").value(COURIER.toString()))
                .andExpect(jsonPath("$.courier.available_actions").isEmpty())
                .andExpect(jsonPath("$.clock.revision").value("9007199254740993"))
                .andExpect(jsonPath("$.offices[0].address").value("Address")).andReturn();
        var body = json.readTree(result.getResponse().getContentAsString());
        assertThat(body.size()).isEqualTo(6);
        assertThat(body.get("courier").size()).isEqualTo(11);
        assertThat(body.get("route").isNull()).isTrue();
    }
    @Test void representativeCanReadOwnScheduleOnly() throws Exception {
        var session = login("representative");
        mvc.perform(get(path(COURIER)).session(session)).andExpect(status().isOk())
                .andExpect(jsonPath("$.courier.available_actions[0]").value("REFUSE_WORKDAY"));
        clearInvocations(scheduleReader, orderReader, scenarioReader);
        mvc.perform(get(path(UUID.randomUUID())).session(session)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        verifyNoInteractions(scheduleReader, orderReader, scenarioReader);
    }
    @Test void anonymousCannotReadAnySchedule() throws Exception {
        mvc.perform(get(path(COURIER))).andExpect(status().isUnauthorized());
        verifyNoInteractions(scheduleReader, orderReader);
    }
    @Test void missingCourierIs404ForDispatcher() throws Exception {
        mvc.perform(get(path(UUID.randomUUID())).session(login("dispatcher"))).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
        verifyNoInteractions(orderReader);
    }
    @Test void explicitDateIsPassedToAllScheduleQueries() throws Exception {
        mvc.perform(get(path(COURIER)).param("date", DATE.plusDays(1).toString()).session(login("dispatcher")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.date").value(DATE.plusDays(1).toString()));
        verify(orderReader).findSchedule(eq(COURIER), eq(DATE.plusDays(1)), anyBoolean(), any());
        verify(scheduleReader).details(any(), eq(DATE.plusDays(1)), anyList());
    }
    @Test void invalidUuidAndDatesAre400() throws Exception {
        var session = login("dispatcher");
        for (String id : List.of("invalid", "1-1-1-1-1"))
            mvc.perform(get("/api/couriers/" + id + "/schedule").session(session)).andExpect(status().isBadRequest());
        for (String date : List.of("", "2026-02-30", "2026-1-01", "2026-11-21T10:00:00Z"))
            mvc.perform(get(path(COURIER)).param("date", date).session(session)).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
        verifyNoInteractions(scheduleReader, orderReader);
    }
    @Test void missingScenarioIs409() throws Exception {
        when(scenarioReader.findCurrent()).thenReturn(Optional.empty());
        mvc.perform(get(path(COURIER)).session(login("dispatcher"))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("SCENARIO_NOT_INITIALIZED"));
        verifyNoInteractions(orderReader);
        verify(scheduleReader, never()).details(any(), any(), anyList());
    }
}

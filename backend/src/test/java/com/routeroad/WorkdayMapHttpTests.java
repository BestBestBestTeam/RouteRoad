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
class WorkdayMapHttpTests {
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
    private static final UUID COURIER = UUID.randomUUID();
    private static final UUID OFFICE = UUID.randomUUID();
    private static final UUID ORDER = UUID.randomUUID();
    private static final UUID ROUTE = UUID.randomUUID();
    private static final java.time.Instant NOW = java.time.Instant.parse("2026-11-15T07:00:00Z");
    @BeforeEach void mapSetup() {
        var stop1 = new com.routeroad.planning.api.RouteStop(ROUTE,1,com.routeroad.planning.domain.RouteStopType.OFFICE,
                null,OFFICE,NOW,NOW,0,0);
        var stop2 = new com.routeroad.planning.api.RouteStop(ROUTE,2,com.routeroad.planning.domain.RouteStopType.ORDER,
                ORDER,null,NOW.plusSeconds(600),NOW.plusSeconds(1200),500,0);
        when(mapReader.read(any())).thenReturn(new com.routeroad.workday.application.WorkdayMapReader.Result(
                List.of(new com.routeroad.workday.api.MapView.MapOrder(ORDER,"A-1","Address","Client",55.1,82.2,
                        com.routeroad.orders.domain.ServiceOrderStatus.ASSIGNED)),
                List.of(new com.routeroad.workday.api.MapView.MapCourier(COURIER,"Courier",null)),
                List.of(new com.routeroad.offices.api.OfficeView(OFFICE,"Office","Office address",55,82)),
                List.of(new com.routeroad.workday.api.MapView.MapRoute(ROUTE,COURIER,9_007_199_254_740_993L,
                        List.of(stop1,stop2),new com.routeroad.planning.api.RouteView.Geometry("LineString",
                                List.of(List.of(82.0,55.0),List.of(82.2,55.1))),"straight")),
                List.of(new com.routeroad.workday.api.MapView.MapAlert(UUID.randomUUID(),List.of(ORDER),null))));
    }
    @Test void dispatcherGetsExactMapContractWithoutCsrf() throws Exception {
        var response = mvc.perform(get("/api/workday/map").session(login("dispatcher"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.routes[0].plan_version").value("9007199254740993"))
                .andExpect(jsonPath("$.routes[0].geometry_kind").value("straight"))
                .andExpect(jsonPath("$.routes[0].geometry.type").value("LineString"))
                .andExpect(jsonPath("$.routes[0].geometry.coordinates[1][0]").value(82.2))
                .andExpect(jsonPath("$.routes[0].geometry.coordinates[1][1]").value(55.1))
                .andExpect(jsonPath("$.orders[0].client_name").value("Client"))
                .andExpect(jsonPath("$.orders[0].day_status").value("ASSIGNED"))
                .andExpect(jsonPath("$.couriers[0].position").isEmpty())
                .andExpect(jsonPath("$.alerts[0].courier_id").isEmpty())
                .andExpect(jsonPath("$.clock.revision").value("9007199254740993"))
                .andExpect(jsonPath("$.clock.now").value("2026-11-15T07:00:05Z")).andReturn();
        var root = json.readTree(response.getResponse().getContentAsString());
        assertThat(root.size()).isEqualTo(6);
        assertThat(root.get("orders").get(0).size()).isEqualTo(7);
        assertThat(root.get("couriers").get(0).size()).isEqualTo(3);
        assertThat(root.get("offices").get(0).size()).isEqualTo(5);
        assertThat(root.get("routes").get(0).size()).isEqualTo(6);
        assertThat(root.get("routes").get(0).get("stops").get(0).size()).isEqualTo(9);
        assertThat(root.get("routes").get(0).get("stops").get(0).get("order_id").isNull()).isTrue();
        assertThat(root.get("routes").get(0).get("stops").get(1).get("office_id").isNull()).isTrue();
        assertThat(root.get("alerts").get(0).size()).isEqualTo(3);
        assertThat(root.get("clock").size()).isEqualTo(4);
        assertThat(root.get("routes").get(0).get("plan_version").isString()).isTrue();
        assertThat(root.get("date")).isNull();
        assertThat(root.get("counts")).isNull();
        verify(mapReader).read(DATE);
    }
    @Test void dateIsStrictAndExplicitDateReachesReader() throws Exception {
        var session = login("dispatcher");
        for (String date : List.of("", "2026-02-30", "2026-1-01", "tomorrow", "2026-11-21T09:00:00Z"))
            mvc.perform(get("/api/workday/map").param("date",date).session(session)).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
        verifyNoInteractions(mapReader);
        mvc.perform(get("/api/workday/map").param("date","2026-11-16").session(session)).andExpect(status().isOk());
        verify(mapReader).read(DATE.plusDays(1));
    }
    @Test void representativeAndAnonymousAreDeniedBeforeReadingMap() throws Exception {
        mvc.perform(get("/api/workday/map")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/workday/map").session(login("representative"))).andExpect(status().isForbidden());
        verifyNoInteractions(mapReader,scenarioReader);
    }
    @Test void missingScenarioIsConflict() throws Exception {
        when(scenarioReader.findCurrent()).thenReturn(Optional.empty());
        mvc.perform(get("/api/workday/map").session(login("dispatcher"))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("SCENARIO_NOT_INITIALIZED"));
        verifyNoInteractions(mapReader);
    }
    @Test void initializedEmptyScenarioReturnsFiveEmptyArraysAndClock() throws Exception {
        when(mapReader.read(any())).thenReturn(new com.routeroad.workday.application.WorkdayMapReader.Result(
                List.of(),List.of(),List.of(),List.of(),List.of()));
        mvc.perform(get("/api/workday/map").session(login("dispatcher"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.orders").isEmpty()).andExpect(jsonPath("$.couriers").isEmpty())
                .andExpect(jsonPath("$.offices").isEmpty()).andExpect(jsonPath("$.routes").isEmpty())
                .andExpect(jsonPath("$.alerts").isEmpty()).andExpect(jsonPath("$.clock.scenario_id").value(SCENARIO.toString()));
    }
}

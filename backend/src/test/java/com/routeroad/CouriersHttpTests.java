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
class CouriersHttpTests {
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
                Instant.parse("2026-11-15T07:00:00Z"), Instant.parse("2026-11-15T07:00:00Z"), 0.5, 9_007_199_254_740_993L)));
        when(reader.count(any())).thenReturn(new WorkdayReader.Counts(10, 7, 2, 5, 3, true));
        when(reader.findOffices()).thenReturn(List.of(new WorkdayReader.Office(UUID.randomUUID(), "Office", "Address", 55, 82)));
        when(orderReader.find(any(), anyInt(), anyLong(), anyBoolean()))
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
    @Test void dispatcherGetsCompleteCourierContractWithoutCsrf() throws Exception {
        var response=mvc.perform(get("/api/couriers").session(login("dispatcher"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1)).andExpect(jsonPath("$.page").value(1)).andExpect(jsonPath("$.page_size").value(10))
                .andExpect(jsonPath("$.clock.revision").value("9007199254740993"))
                .andExpect(jsonPath("$.items[0].is_available").value(true)).andExpect(jsonPath("$.items[0].total_orders").value(4))
                .andExpect(jsonPath("$.items[0].completed_orders").value(1)).andExpect(jsonPath("$.items[0].open_alert_count").value(2))
                .andExpect(jsonPath("$.items[0].position.lat").value(55)).andExpect(jsonPath("$.items[0].position.lon").value(82))
                .andExpect(jsonPath("$.items[0].position.at").value("2026-11-15T07:00:00Z"))
                .andExpect(jsonPath("$.items[0].available_actions").isEmpty()).andReturn();
        var body=json.readTree(response.getResponse().getContentAsString());
        assertThat(body.size()).isEqualTo(5);
        assertThat(body.get("items").get(0).size()).isEqualTo(11);
        assertThat(body.get("items").get(0).get("position").size()).isEqualTo(3);
        assertThat(body.get("clock").get("revision").isString()).isTrue();
    }
    @Test void anonymousAndRepresentativeCannotReadCouriers() throws Exception {
        mvc.perform(get("/api/couriers")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/couriers").session(login("representative"))).andExpect(status().isForbidden());
        verifyNoInteractions(courierReader);
    }
    @Test void noScenarioUsesTheAgreedConflict() throws Exception {
        when(scenarioReader.findCurrent()).thenReturn(Optional.empty());
        mvc.perform(get("/api/couriers").session(login("dispatcher"))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("SCENARIO_NOT_INITIALIZED"));
        verifyNoInteractions(courierReader);
    }
    @Test void parametersAreStrictAndPaginationBounded() throws Exception {
        var session=login("dispatcher");
        for(var param:List.of(Map.entry("date","2026-02-30"),Map.entry("date",""),Map.entry("is_available","yes"),
                Map.entry("is_available","1"),Map.entry("has_open_alerts","TRUE"),Map.entry("has_open_alerts",""),
                Map.entry("workload","busy"),Map.entry("workload","ASSIGNED"),Map.entry("workload",""),
                Map.entry("page","0"),Map.entry("page","-1"),Map.entry("page","2147483648"),
                Map.entry("page_size","0"),Map.entry("page_size","101"),Map.entry("page_size","1.5"))) {
            mvc.perform(get("/api/couriers").param(param.getKey(),param.getValue()).session(session))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
        }
        verifyNoInteractions(courierReader);
    }
    @Test void filteringAndOffsetAreForwardedAsSqlParameters() throws Exception {
        mvc.perform(get("/api/couriers").param("date","2026-11-16").param("search"," Name_%! ")
                .param("is_available","false").param("has_open_alerts","true").param("workload","unassigned")
                .param("page","3").param("page_size","2").session(login("dispatcher"))).andExpect(status().isOk());
        verify(courierReader).find(new com.routeroad.couriers.application.CourierReader.Filter(DATE.plusDays(1),"%name!_!%!!%",false,true,false),2,4L);
    }
    @Test void dateDefaultsToPersistedScenarioDay() throws Exception {
        mvc.perform(get("/api/couriers").session(login("dispatcher"))).andExpect(status().isOk());
        verify(courierReader).find(new com.routeroad.couriers.application.CourierReader.Filter(DATE,null,null,null,null),10,0L);
    }
    @Test void missingPositionIsNullRatherThanInventedCoordinates() throws Exception {
        when(courierReader.find(any(),anyInt(),anyLong())).thenReturn(new com.routeroad.couriers.application.CourierReader.Result(List.of(sample(false)),1));
        mvc.perform(get("/api/couriers").session(login("dispatcher"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].position").isEmpty()).andExpect(jsonPath("$.items[0].position_lat").doesNotExist());
    }
    @Test void existingEmptyScenarioReturnsAnEmptyPage() throws Exception {
        when(courierReader.find(any(),anyInt(),anyLong())).thenReturn(new com.routeroad.couriers.application.CourierReader.Result(List.of(),0));
        mvc.perform(get("/api/couriers").session(login("dispatcher"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty()).andExpect(jsonPath("$.total").value(0));
    }
}

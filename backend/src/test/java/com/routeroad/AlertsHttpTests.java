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
class AlertsHttpTests {
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
    private static final UUID ALERT=UUID.randomUUID();
    private static final UUID ORDER1=UUID.randomUUID();
    private static final UUID ORDER2=UUID.randomUUID();
    @BeforeEach void alertsSetup() {
        var now=Instant.parse("2026-11-15T07:00:00Z");
        var resolution=new com.routeroad.shared.api.Resolution(now,
                new com.routeroad.shared.api.Actor(null,"Система",com.routeroad.shared.api.Actor.Role.SYSTEM),"");
        var alert=new com.routeroad.execution.api.AlertView(ALERT,UUID.randomUUID(),DATE,null,
                "refusal","courier_day","critical","open","COURIER_REFUSED","Причина",now,now.plusSeconds(600),
                null,null,null,List.of(ORDER1,ORDER2),List.of(
                    new com.routeroad.execution.api.AlertView.OrderEntry(ORDER1,"pending",null),
                    new com.routeroad.execution.api.AlertView.OrderEntry(ORDER2,"resolved",resolution)),
                "Отказ от заявок на день",List.of("Затронуто заявок: 2","Ожидают решения: 1"),null,
                List.of("REASSIGN","AUTO_REASSIGN"));
        when(alertReader.find(any(), anyInt(), anyLong(), anyBoolean(), any()))
                .thenReturn(new com.routeroad.execution.application.AlertReader.Result(List.of(alert),1));
    }
    @Test void dispatcherReceivesPageOfFullGroupsWithoutCsrf() throws Exception {
        var response=mvc.perform(get("/api/alerts").session(login("dispatcher"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1)).andExpect(jsonPath("$.page").value(1)).andExpect(jsonPath("$.page_size").value(10))
                .andExpect(jsonPath("$.items[0].id").value(ALERT.toString()))
                .andExpect(jsonPath("$.items[0].orders.length()").value(2))
                .andExpect(jsonPath("$.items[0].orders[1].resolution.actor.role").value("system"))
                .andExpect(jsonPath("$.items[0].orders[1].resolution.reason").value(""))
                .andExpect(jsonPath("$.items[0].courier_id").isEmpty())
                .andExpect(jsonPath("$.items[0].resolution_reason").isEmpty())
                .andExpect(jsonPath("$.items[0].available_actions[0]").value("REASSIGN"))
                .andExpect(jsonPath("$.clock.revision").value("9007199254740993")).andReturn();
        var root=json.readTree(response.getResponse().getContentAsString());
        assertThat(root.size()).isEqualTo(5);assertThat(root.get("items").get(0).size()).isEqualTo(21);
        assertThat(root.get("items").get(0).get("orders").get(1).size()).isEqualTo(3);
        assertThat(root.get("items").get(0).get("resolution").isNull()).isTrue();
        verify(alertReader).find(new com.routeroad.execution.application.AlertReader.Filter(DATE,null,null,null), 10, 0, true, java.time.Instant.parse("2026-11-15T07:00:05Z"));
    }
    @Test void acceptedFiltersReachTheReaderAsSqlParameters() throws Exception {
        mvc.perform(get("/api/alerts").param("date","2026-11-16").param("search"," NAME_%! ")
                .param("status","resolved").param("severity","warning").param("page","3").param("page_size","2")
                .session(login("dispatcher"))).andExpect(status().isOk());
        verify(alertReader).find(new com.routeroad.execution.application.AlertReader.Filter(DATE.plusDays(1),"%name!_!%!!%",
                com.routeroad.execution.domain.AlertStatus.RESOLVED,com.routeroad.execution.domain.AlertSeverity.WARNING), 2, 4, false, java.time.Instant.parse("2026-11-15T07:00:05Z"));
    }
    @Test void invalidFiltersAndPaginationAreBadRequests() throws Exception {
        var session=login("dispatcher");
        for(var entry:List.of(Map.entry("date",""),Map.entry("date","2026-02-30"),Map.entry("date","2026-1-01"),
                Map.entry("status","pending"),Map.entry("status","OPEN"),Map.entry("status",""),
                Map.entry("severity","urgent"),Map.entry("severity","WARNING"),Map.entry("severity",""),
                Map.entry("page","0"),Map.entry("page","-1"),Map.entry("page","1.5"),Map.entry("page","2147483648"),
                Map.entry("page_size","0"),Map.entry("page_size","101"),Map.entry("page_size","")))
            mvc.perform(get("/api/alerts").param(entry.getKey(),entry.getValue()).session(session)).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
        verifyNoInteractions(alertReader);
    }
    @Test void anonymousAndRepresentativeAreDeniedBeforeReadingAlerts() throws Exception {
        mvc.perform(get("/api/alerts")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
        mvc.perform(get("/api/alerts").session(login("representative"))).andExpect(status().isForbidden());
        verifyNoInteractions(alertReader,scenarioReader);
    }
    @Test void missingScenarioHasExistingConflictCode() throws Exception {
        when(scenarioReader.findCurrent()).thenReturn(Optional.empty());
        mvc.perform(get("/api/alerts").session(login("dispatcher"))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("SCENARIO_NOT_INITIALIZED"));
        verifyNoInteractions(alertReader);
    }
    @Test void emptyPageKeepsFilteredTotalAndRequestedPage() throws Exception {
        when(alertReader.find(any(), anyInt(), anyLong(), anyBoolean(), any()))
                .thenReturn(new com.routeroad.execution.application.AlertReader.Result(List.of(),12));
        mvc.perform(get("/api/alerts").param("page","3").session(login("dispatcher"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty()).andExpect(jsonPath("$.total").value(12)).andExpect(jsonPath("$.page").value(3));
    }
}

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
class HistoryHttpTests {
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
    @BeforeEach void historySetup() {
        var entry=new com.routeroad.execution.api.HistoryEntry(UUID.randomUUID(),"sync_conflict",Instant.parse("2026-11-15T07:00:00Z"),
            new com.routeroad.shared.api.Actor(null,"Система",com.routeroad.shared.api.Actor.Role.SYSTEM),null,null,
            "Офлайн-действие отклонено","Код причины: ORDER_CANCELLED","Отмена",List.of());
        when(historyReader.find(any(),anyInt(),anyLong())).thenReturn(new com.routeroad.execution.application.HistoryReader.Result(List.of(entry),1));
    }
    @Test void dispatcherReceivesExactHistoryContractWithoutCsrf() throws Exception {
        var response=mvc.perform(get("/api/history").session(login("dispatcher"))).andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(1)).andExpect(jsonPath("$.page").value(1)).andExpect(jsonPath("$.page_size").value(10))
            .andExpect(jsonPath("$.clock.revision").value("9007199254740993")).andExpect(jsonPath("$.items[0].type").value("sync_conflict"))
            .andExpect(jsonPath("$.items[0].actor.role").value("system")).andReturn();
        var root=json.readTree(response.getResponse().getContentAsString());
        assertThat(root.size()).isEqualTo(5);var item=root.path("items").get(0);
        assertThat(item.properties().stream().map(Map.Entry::getKey).toList()).containsExactlyInAnyOrder(
            "id","type","created_at","actor","order_id","alert_id","title","description","reason","changes");
        assertThat(item.path("order_id").isNull()).isTrue();assertThat(item.path("alert_id").isNull()).isTrue();
        verify(historyReader).find(new com.routeroad.execution.application.HistoryReader.Filter(DATE,null,null,null,null),10,0);
    }
    @Test void filtersAndLargeOffsetsArePassedToSqlReader() throws Exception {
        var id=UUID.randomUUID();
        mvc.perform(get("/api/history").session(login("dispatcher")).param("date","2026-11-16").param("search"," NAME_%! ")
            .param("type","alert_created").param("actor_role","system").param("order_id",id.toString())
            .param("page","2147483647").param("page_size","100")).andExpect(status().isOk());
        verify(historyReader).find(new com.routeroad.execution.application.HistoryReader.Filter(DATE.plusDays(1),"%name!_!%!!%",
            "alert_created","system",id),100,214748364600L);
    }
    @Test void invalidQueryIsRejectedBeforeReadingHistory() throws Exception {
        var session=login("dispatcher");
        for(var pair:List.of(Map.entry("date","2026-02-30"),Map.entry("date",""),Map.entry("date","2026-1-01"),
            Map.entry("type","PLAN_ACTIVATED"),Map.entry("type",""),Map.entry("actor_role","SYSTEM"),Map.entry("actor_role",""),
            Map.entry("order_id","bad"),Map.entry("order_id","1-1-1-1-1"),Map.entry("order_id",""),
            Map.entry("page","0"),Map.entry("page","-1"),Map.entry("page","1.5"),Map.entry("page","2147483648"),
            Map.entry("page_size","0"),Map.entry("page_size","101"),Map.entry("page_size","")))
            mvc.perform(get("/api/history").session(session).param(pair.getKey(),pair.getValue())).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
        verifyNoInteractions(historyReader);
    }
    @Test void anonymousAndRepresentativeCannotReadHistory() throws Exception {
        mvc.perform(get("/api/history")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/history").session(login("representative"))).andExpect(status().isForbidden());
        verifyNoInteractions(historyReader,scenarioReader);
    }
    @Test void missingScenarioIs409() throws Exception {
        when(scenarioReader.findCurrent()).thenReturn(Optional.empty());
        mvc.perform(get("/api/history").session(login("dispatcher"))).andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("SCENARIO_NOT_INITIALIZED"));
        verifyNoInteractions(historyReader);
    }
    @Test void emptyPagesKeepFilteredTotalAndClock() throws Exception {
        when(historyReader.find(any(),anyInt(),anyLong())).thenReturn(new com.routeroad.execution.application.HistoryReader.Result(List.of(),12));
        mvc.perform(get("/api/history").session(login("dispatcher")).param("page","20"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty()).andExpect(jsonPath("$.total").value(12));
    }
}

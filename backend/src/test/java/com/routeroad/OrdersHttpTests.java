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
class OrdersHttpTests {
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
                Instant.parse("2026-11-15T07:00:00Z"), Instant.parse("2026-11-15T07:00:00Z"), 0.5, 9_007_199_254_740_993L)));
        when(reader.count(any())).thenReturn(new WorkdayReader.Counts(10, 7, 2, 5, 3, true));
        when(reader.findOffices()).thenReturn(List.of(new WorkdayReader.Office(UUID.randomUUID(), "Office", "Address", 55, 82)));
        when(orderReader.find(any(), anyInt(), anyLong(), anyBoolean()))
                .thenReturn(new com.routeroad.orders.application.OrderReader.Result(List.of(), 0));
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
    @Test void dispatcherGetsEmptyPageWithStringClockWithoutCsrf() throws Exception {
        mvc.perform(get("/api/orders").session(login("dispatcher"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty()).andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.page").value(1)).andExpect(jsonPath("$.page_size").value(10))
                .andExpect(jsonPath("$.clock.revision").value("9007199254740993"));
    }
    @Test void representativeAndAnonymousCannotReadOrders() throws Exception {
        mvc.perform(get("/api/orders")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/orders").session(login("representative"))).andExpect(status().isForbidden());
        verifyNoInteractions(orderReader);
    }
    @Test void missingScenarioUsesSameConflictAsWorkday() throws Exception {
        when(scenarioReader.findCurrent()).thenReturn(Optional.empty());
        mvc.perform(get("/api/orders").session(login("dispatcher"))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("SCENARIO_NOT_INITIALIZED"));
        verifyNoInteractions(orderReader);
    }
    @Test void parametersAreStrictlyParsedAndPaginationIsBounded() throws Exception {
        var session=login("dispatcher");
        for(var param:List.of(Map.entry("date","bad"),Map.entry("date",""),Map.entry("assigned","yes"),Map.entry("page","0"),
                Map.entry("page_size","101"),Map.entry("courier_id","1-1-1-1-1"),Map.entry("status","unknown"))) {
            mvc.perform(get("/api/orders").param(param.getKey(),param.getValue()).session(session))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
        }
        verifyNoInteractions(orderReader);
    }
    @Test void dateFiltersAndOffsetAreForwardedWithoutLoadingAllOrders() throws Exception {
        mvc.perform(get("/api/orders").param("date","2026-11-16").param("page","3").param("page_size","2")
                .param("assigned","false").param("search"," a_%! ").session(login("dispatcher"))).andExpect(status().isOk());
        verify(orderReader).find(new com.routeroad.orders.application.OrderReader.Filter(DATE.plusDays(1),"%a!_!%!!%",null,null,false),2,4L,false);
    }
}

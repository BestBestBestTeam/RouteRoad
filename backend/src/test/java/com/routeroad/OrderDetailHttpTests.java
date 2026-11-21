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
class OrderDetailHttpTests {
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

    private static final UUID ORDER = UUID.randomUUID();
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
    private com.routeroad.orders.api.OrderView view(UUID courier, List<String> actions) {
        var actor=new com.routeroad.shared.api.Actor(null,"Система",com.routeroad.shared.api.Actor.Role.SYSTEM);
        var assignment=new com.routeroad.orders.api.AssignmentView(ORDER,courier,UUID.randomUUID(),9007199254740993L,
                9007199254740993L,com.routeroad.planning.domain.AssignmentReasonCode.NEAREST,"Reason",
                com.routeroad.planning.domain.AssignmentDecidedBy.SYSTEM,Instant.EPOCH,"Courier",actor);
        return new com.routeroad.orders.api.OrderView(ORDER,"PRIVATE-ORDER","Private client","Address",null,"Card",55,82,
                Instant.EPOCH,Instant.EPOCH.plusSeconds(3600),600,com.routeroad.orders.domain.ServiceOrderStatus.ASSIGNED,
                DATE,9007199254740993L,com.routeroad.orders.domain.ServiceOrderStatus.ASSIGNED,assignment,actions);
    }
    private UUID ownCourier() { return users.findByLogin("representative").orElseThrow().getCourierId(); }
    private void setupOrder(UUID courier) {
        when(orderReader.currentDate(ORDER)).thenReturn(Optional.of(DATE));
        when(orderReader.findOne(eq(ORDER),eq(DATE),anyBoolean())).thenAnswer(call -> Optional.of(view(courier,
                Boolean.TRUE.equals(call.getArgument(2)) ? List.of("REASSIGN") : List.of())));
        when(orderReader.refusals(ORDER,courier,DATE)).thenReturn(new com.routeroad.orders.application.OrderReader.Refusals(false,false));
        when(orderAlerts.find(any(),any(),any(),anyBoolean())).thenReturn(List.of());
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
    @Test void dispatcherGetsCardAndStringVersionsWithoutCsrf() throws Exception {
        setupOrder(ownCourier());
        mvc.perform(get("/api/orders/"+ORDER).session(login("dispatcher"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.order.id").value(ORDER.toString())).andExpect(jsonPath("$.alerts").isEmpty())
                .andExpect(jsonPath("$.order.version").value("9007199254740993"))
                .andExpect(jsonPath("$.order.assignment.plan_version").value("9007199254740993"))
                .andExpect(jsonPath("$.clock.revision").value("9007199254740993"));
        verify(orderReader,never()).find(any(),anyInt(),anyLong(),anyBoolean());
    }
    @Test void ownRepresentativeGetsOnlyRepresentativeActions() throws Exception {
        UUID courier=ownCourier();setupOrder(courier);
        mvc.perform(get("/api/orders/"+ORDER).session(login("representative"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.order.available_actions[0]").value("EN_ROUTE"))
                .andExpect(jsonPath("$.order.available_actions[1]").value("REPORT_DELAY"))
                .andExpect(jsonPath("$.order.available_actions[2]").value("REFUSE_ORDER"));
        verify(orderAlerts).find(ORDER,DATE,courier,false);
    }
    @Test void foreignOrderIsForbiddenWithoutReadingAlertsOrExposingData() throws Exception {
        setupOrder(UUID.randomUUID());
        var result=mvc.perform(get("/api/orders/"+ORDER).param("courier_id",ownCourier().toString()).session(login("representative")))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("FORBIDDEN")).andReturn();
        assertThat(result.getResponse().getContentAsString()).doesNotContain("PRIVATE-ORDER","Private client",ORDER.toString());
        verifyNoInteractions(orderAlerts);
    }
    @Test void refusedOrderCanBeReadButNoCommandsAreOffered() throws Exception {
        UUID courier=ownCourier();setupOrder(courier);
        when(orderReader.refusals(ORDER,courier,DATE)).thenReturn(new com.routeroad.orders.application.OrderReader.Refusals(true,false));
        mvc.perform(get("/api/orders/"+ORDER).session(login("representative"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.order.assignment.courier_id").value(courier.toString()))
                .andExpect(jsonPath("$.order.available_actions").isEmpty());
    }
    @Test void historicalDateCannotGrantAccess() throws Exception {
        setupOrder(ownCourier());
        mvc.perform(get("/api/orders/"+ORDER).param("date",DATE.minusDays(1).toString()).session(login("representative")))
                .andExpect(status().isForbidden());
        verify(orderReader,never()).findOne(ORDER,DATE.minusDays(1),false);
        verifyNoInteractions(orderAlerts);
    }
    @Test void accountCourierIsReadAgainOnEveryRequest() throws Exception {
        UUID courier=ownCourier();setupOrder(courier);var session=login("representative");
        when(users.findByLogin("representative")).thenReturn(Optional.of(new AppUserEntity(UUID.randomUUID(),"representative",hash,
                "New binding",AppUserRole.REPRESENTATIVE,UUID.randomUUID())));
        mvc.perform(get("/api/orders/"+ORDER).session(session)).andExpect(status().isForbidden());
        verifyNoInteractions(orderAlerts);
    }
    @Test void anonymousNotFoundBadUuidAndMissingScenarioAreJsonErrors() throws Exception {
        mvc.perform(get("/api/orders/"+ORDER)).andExpect(status().isUnauthorized());
        var session=login("dispatcher");
        mvc.perform(get("/api/orders/"+UUID.randomUUID()).session(session)).andExpect(status().isNotFound());
        mvc.perform(get("/api/orders/not-uuid").session(session)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/orders/1-1-1-1-1").session(session)).andExpect(status().isBadRequest());
        when(scenarioReader.findCurrent()).thenReturn(Optional.empty());
        mvc.perform(get("/api/orders/"+ORDER).session(session)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("SCENARIO_NOT_INITIALIZED"));
    }
}

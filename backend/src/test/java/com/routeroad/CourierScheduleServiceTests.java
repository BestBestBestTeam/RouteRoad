package com.routeroad;

import com.routeroad.auth.application.Account;
import com.routeroad.auth.application.AccountService;
import com.routeroad.couriers.application.*;
import com.routeroad.orders.api.OrderView;
import com.routeroad.orders.application.OrderReader;
import com.routeroad.orders.domain.ServiceOrderStatus;
import com.routeroad.shared.domain.AppUserRole;
import com.routeroad.simulation.application.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CourierScheduleServiceTests {
    private static final UUID COURIER = UUID.randomUUID();
    private static final LocalDate DATE = LocalDate.of(2026, 11, 21);
    private static final Instant NOW = Instant.parse("2026-11-21T09:00:00Z");
    private final CourierScheduleReader reader = mock(CourierScheduleReader.class);
    private final OrderReader orders = mock(OrderReader.class);
    private final AccountService accounts = mock(AccountService.class);
    private final ScenarioReader scenario = mock(ScenarioReader.class);
    private final CourierScheduleService service = new CourierScheduleService(reader, orders,
            new ScenarioClockQuery(scenario, java.time.Clock.fixed(NOW, ZoneOffset.UTC)), accounts);
    private final CourierScheduleReader.Courier courier = new CourierScheduleReader.Courier(COURIER, "Courier",
            UUID.randomUUID(), NOW, NOW.plusSeconds(3600), null, true);
    @BeforeEach void setup() {
        viewer(AppUserRole.DISPATCHER, null);
        when(reader.findCourier(COURIER)).thenReturn(Optional.of(courier));
        when(scenario.findCurrent()).thenReturn(Optional.of(new ScenarioReader.Scenario(UUID.randomUUID(), DATE,
                NOW, NOW, 1, 9_007_199_254_740_993L)));
        when(orders.findSchedule(eq(COURIER), any())).thenReturn(List.of());
        when(reader.details(eq(courier), any(), anyList())).thenReturn(new CourierScheduleReader.Details(null, List.of(), 0, false));
    }
    private void viewer(AppUserRole role, UUID courierId) {
        when(accounts.current("session")).thenReturn(new Account(UUID.randomUUID(), "session", "Viewer", role, courierId));
    }
    private OrderReader.ScheduledOrder order(ServiceOrderStatus status, boolean refused, boolean dayRefused) {
        return new OrderReader.ScheduledOrder(new OrderView(UUID.randomUUID(), "1", "Client", "Address", null, "Card",
                55, 82, NOW, NOW.plusSeconds(3600), 600, status, DATE, 1L, status, null, List.of("REASSIGN")),
                new OrderReader.Refusals(refused, dayRefused));
    }
    @Test void foreignCourierIsDeniedBeforeReadingAnyScheduleOrScenarioData() {
        viewer(AppUserRole.REPRESENTATIVE, UUID.randomUUID());
        assertThatThrownBy(() -> service.get(COURIER, null, "session")).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(reader, orders, scenario);
    }
    @Test void missingServerSideCourierLinkIsDenied() {
        viewer(AppUserRole.REPRESENTATIVE, null);
        assertThatThrownBy(() -> service.get(COURIER, DATE, "session")).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(reader, orders, scenario);
    }
    @Test void missingCourierIsNotFoundBeforeDetailedReads() {
        when(reader.findCourier(COURIER)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.get(COURIER, null, "session")).isInstanceOf(CourierNotFoundException.class);
        verifyNoInteractions(orders, scenario);
        verify(reader, never()).details(any(), any(), anyList());
    }
    @Test void missingScenarioDoesNotReadOrdersOrInventAClock() {
        when(scenario.findCurrent()).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.get(COURIER, null, "session")).isInstanceOf(ScenarioNotInitializedException.class);
        verifyNoInteractions(orders);
        verify(reader, never()).details(any(), any(), anyList());
    }
    @Test void emptyScheduleUsesScenarioDayAndNullableRoute() {
        var result = service.get(COURIER, null, "session");
        assertThat(result.date()).isEqualTo(DATE);
        assertThat(result.route()).isNull();
        assertThat(result.orders()).isEmpty();
        assertThat(result.clock().now()).isEqualTo(NOW);
        assertThat(result.courier().position()).isNull();
        verify(orders).findSchedule(COURIER, DATE);
    }
    @Test void explicitDateDisablesRepresentativeActionsWithoutChangingScenarioClock() {
        viewer(AppUserRole.REPRESENTATIVE, COURIER);
        when(orders.findSchedule(COURIER, DATE.plusDays(1))).thenReturn(List.of(order(ServiceOrderStatus.ASSIGNED, false, false)));
        var result = service.get(COURIER, DATE.plusDays(1), "session");
        assertThat(result.date()).isEqualTo(DATE.plusDays(1));
        assertThat(result.clock().now()).isEqualTo(NOW);
        assertThat(result.orders().getFirst().availableActions()).isEmpty();
        assertThat(result.courier().availableActions()).isEmpty();
    }
    @Test void dispatcherGetsNoOrderOrCourierActions() {
        when(orders.findSchedule(COURIER, DATE)).thenReturn(List.of(order(ServiceOrderStatus.ASSIGNED, false, false)));
        var result = service.get(COURIER, DATE, "session");
        assertThat(result.orders().getFirst().availableActions()).isEmpty();
        assertThat(result.courier().availableActions()).isEmpty();
    }
    @Test void representativeActionsRespectStatusAndBothRefusals() {
        viewer(AppUserRole.REPRESENTATIVE, COURIER);
        when(orders.findSchedule(COURIER, DATE)).thenReturn(List.of(
                order(ServiceOrderStatus.ASSIGNED, false, false), order(ServiceOrderStatus.ASSIGNED, true, false),
                order(ServiceOrderStatus.ARRIVED, false, true), order(ServiceOrderStatus.IN_PROGRESS, false, true),
                order(ServiceOrderStatus.COMPLETED, false, false)));
        var result = service.get(COURIER, null, "session");
        assertThat(result.orders().get(0).availableActions()).containsExactly("EN_ROUTE", "REPORT_DELAY", "REFUSE_ORDER");
        assertThat(result.orders().get(1).availableActions()).isEmpty();
        assertThat(result.orders().get(2).availableActions()).isEmpty();
        assertThat(result.orders().get(3).availableActions()).containsExactly("COMPLETED", "REPORT_DELAY");
        assertThat(result.orders().get(4).availableActions()).isEmpty();
        assertThat(result.courier().totalOrders()).isEqualTo(5);
        assertThat(result.courier().completedOrders()).isEqualTo(1);
    }
    @Test void workdayRefusalActionNeedsCurrentAvailableCourierWithoutDayRefusal() {
        viewer(AppUserRole.REPRESENTATIVE, COURIER);
        assertThat(service.get(COURIER, DATE, "session").courier().availableActions()).containsExactly("REFUSE_WORKDAY");
        when(reader.details(eq(courier), any(), anyList())).thenReturn(new CourierScheduleReader.Details(null, List.of(), 0, true));
        assertThat(service.get(COURIER, DATE, "session").courier().availableActions()).isEmpty();
    }
    @Test void serviceOwnsOneRepeatableReadReadOnlyTransaction() throws Exception {
        var transaction = CourierScheduleService.class.getMethod("get", UUID.class, LocalDate.class, String.class)
                .getAnnotation(Transactional.class);
        assertThat(transaction.readOnly()).isTrue();
        assertThat(transaction.isolation()).isEqualTo(Isolation.REPEATABLE_READ);
    }
}

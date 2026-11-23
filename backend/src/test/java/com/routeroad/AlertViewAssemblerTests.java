package com.routeroad;

import com.routeroad.execution.infrastructure.AlertViewAssembler;
import com.routeroad.execution.infrastructure.OrderAlertRow;
import com.routeroad.shared.api.Actor;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AlertViewAssemblerTests {
    private static final UUID ALERT=UUID.randomUUID();
    private static final Instant NOW=Instant.parse("2026-11-21T09:00:00Z");
    private final AlertViewAssembler assembler=new AlertViewAssembler();
    private OrderAlertRow row(UUID alert,UUID order,String itemStatus,boolean eligible) {
        var row=mock(OrderAlertRow.class);
        when(row.getId()).thenReturn(alert);when(row.getOrderId()).thenReturn(order);
        when(row.getSourceEventId()).thenReturn(UUID.randomUUID());
        when(row.getServiceDate()).thenReturn(LocalDate.of(2026,11,21));
        when(row.getKind()).thenReturn("refusal");when(row.getScope()).thenReturn("courier_day");
        when(row.getSeverity()).thenReturn("critical");when(row.getStatus()).thenReturn("open");
        when(row.getReasonCode()).thenReturn("COURIER_REFUSED");when(row.getReasonText()).thenReturn("Причина");
        when(row.getCreatedAt()).thenReturn(NOW);when(row.getDeadlineAt()).thenReturn(NOW.plusSeconds(600));
        when(row.getItemStatus()).thenReturn(itemStatus);when(row.getItemCanReassign()).thenReturn(eligible);
        return row;
    }
    @Test void partialGroupHasOneHeaderFullMembershipAndIndependentItemResolutions() {
        var a=row(ALERT,UUID.randomUUID(),"resolved",true);
        var b=row(ALERT,UUID.randomUUID(),"pending",true);
        var actor=UUID.randomUUID();
        when(a.getItemResolvedAt()).thenReturn(NOW);
        when(a.getItemResolvedBy()).thenReturn(actor);when(a.getItemResolverName()).thenReturn("Dispatcher");
        when(a.getItemResolverRole()).thenReturn("dispatcher");when(a.getItemResolutionReason()).thenReturn("Передано");
        var result=assembler.assemble(List.of(a,b), true, NOW);
        assertThat(result).hasSize(1);
        var view=result.getFirst();
        assertThat(view.orderIds()).containsExactly(a.getOrderId(),b.getOrderId());
        assertThat(view.orders().getFirst().resolution().actor()).isEqualTo(new Actor(actor,"Dispatcher",Actor.Role.DISPATCHER));
        assertThat(view.orders().getFirst().resolution().reason()).isEqualTo("Передано");
        assertThat(view.orders().get(1).resolution()).isNull();assertThat(view.resolution()).isNull();
        assertThat(view.impact()).containsExactly("Затронуто заявок: 2","Ожидают решения: 1");
        assertThat(view.availableActions()).containsExactly("REASSIGN","AUTO_REASSIGN");
    }
    @Test void openHeaderAloneNeverGrantsActions() {
        var resolved=row(ALERT,UUID.randomUUID(),"resolved",true);
        var ineligible=row(ALERT,UUID.randomUUID(),"pending",false);
        assertThat(assembler.assemble(List.of(resolved,ineligible), true, NOW).getFirst().availableActions()).isEmpty();
        assertThat(assembler.assemble(List.of(row(ALERT,UUID.randomUUID(),"pending",true)), false, NOW).getFirst().availableActions()).isEmpty();
    }
    @Test void closedHeaderHasNoActionsAndRetainsTheGeneralResolution() {
        var row=row(ALERT,UUID.randomUUID(),"pending",true);
        when(row.getStatus()).thenReturn("resolved");when(row.getResolvedAt()).thenReturn(NOW);
        when(row.getResolutionReason()).thenReturn("Решено системой");
        var view=assembler.assemble(List.of(row), true, NOW).getFirst();
        assertThat(view.availableActions()).isEmpty();assertThat(view.status()).isEqualTo("resolved");
        assertThat(view.orders().getFirst().status()).isEqualTo("pending");
        assertThat(view.resolution().actor()).isEqualTo(new Actor(null,"Система",Actor.Role.SYSTEM));
        assertThat(view.resolution().reason()).isEqualTo("Решено системой");
    }
    @Test void nullableSourceResolutionReasonRemainsNullWhileRequiredDisplayReasonRemainsAString() {
        var row=row(ALERT,UUID.randomUUID(),"resolved",false);
        when(row.getStatus()).thenReturn("resolved");when(row.getResolvedAt()).thenReturn(NOW);
        when(row.getItemResolvedAt()).thenReturn(NOW);when(row.getReasonText()).thenReturn("");
        var view=assembler.assemble(List.of(row), true, NOW).getFirst();
        assertThat(view.reasonText()).isEmpty();assertThat(view.resolutionReason()).isNull();
        assertThat(view.resolution().reason()).isEmpty();assertThat(view.orders().getFirst().resolution().reason()).isEmpty();
    }
    @Test void headerWithoutChildrenHasEmptyArraysAndNoInventedOrder() {
        var row=row(ALERT,null,null,false);
        var view=assembler.assemble(List.of(row), true, NOW).getFirst();
        assertThat(view.orderIds()).isEmpty();assertThat(view.orders()).isEmpty();assertThat(view.availableActions()).isEmpty();
        assertThat(view.impact()).containsExactly("Затронуто заявок: 0","Ожидают решения: 0");
    }
    @Test void emptyInputIsAnEmptyList() { assertThat(assembler.assemble(List.of(), true, NOW)).isEmpty(); }
    @Test void titleUsesExistingKindAndScopeLabels() {
        var expected=Map.of("delay/order","Задержка встречи","delay/courier_day","Задержка представителя",
                "refusal/order","Отказ от заявки","refusal/courier_day","Отказ от заявок на день",
                "conflict/order","Конфликт назначения","conflict/courier_day","Конфликт назначения");
        for(var entry:expected.entrySet()) {
            var parts=entry.getKey().split("/");var row=row(ALERT,UUID.randomUUID(),"pending",true);
            when(row.getKind()).thenReturn(parts[0]);when(row.getScope()).thenReturn(parts[1]);
            assertThat(assembler.assemble(List.of(row), true, NOW).getFirst().title()).isEqualTo(entry.getValue());
        }
    }
}

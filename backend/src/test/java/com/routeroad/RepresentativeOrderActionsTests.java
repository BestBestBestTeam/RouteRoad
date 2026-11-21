package com.routeroad;

import com.routeroad.orders.domain.RepresentativeOrderActions;
import com.routeroad.orders.domain.ServiceOrderStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class RepresentativeOrderActionsTests {
    @ParameterizedTest @EnumSource(ServiceOrderStatus.class)
    void onlyAgreedTransitionsAreExposed(ServiceOrderStatus status) {
        var actions = RepresentativeOrderActions.forOrder(status, true, false, false);
        var expected = switch (status) {
            case ASSIGNED -> List.of("EN_ROUTE", "REPORT_DELAY", "REFUSE_ORDER");
            case EN_ROUTE -> List.of("ARRIVED", "REPORT_DELAY", "REFUSE_ORDER");
            case ARRIVED -> List.of("IN_PROGRESS", "REPORT_DELAY", "REFUSE_ORDER");
            case IN_PROGRESS -> List.of("COMPLETED", "REPORT_DELAY");
            default -> List.<String>of();
        };
        assertThat(actions).isEqualTo(expected);
    }
    @Test void individualRefusalBlocksAllFurtherActions() {
        for (var status : ServiceOrderStatus.values()) {
            assertThat(RepresentativeOrderActions.forOrder(status, true, true, false)).isEmpty();
        }
    }
    @Test void dayRefusalPreservesAnInProgressMeetingOnly() {
        assertThat(RepresentativeOrderActions.forOrder(ServiceOrderStatus.IN_PROGRESS, true, false, true))
                .containsExactly("COMPLETED", "REPORT_DELAY");
        assertThat(RepresentativeOrderActions.forOrder(ServiceOrderStatus.ASSIGNED, true, false, true)).isEmpty();
    }
    @Test void otherScenarioDayDoesNotOfferCommands() {
        for (var status : ServiceOrderStatus.values()) {
            assertThat(RepresentativeOrderActions.forOrder(status, false, false, false)).isEmpty();
        }
    }
}

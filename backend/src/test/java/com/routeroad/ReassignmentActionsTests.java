package com.routeroad;

import com.routeroad.execution.domain.ReassignmentActions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.time.Instant;
import static org.assertj.core.api.Assertions.assertThat;

class ReassignmentActionsTests {
    private static final Instant DEADLINE=Instant.parse("2026-11-21T09:10:00Z");
    @ParameterizedTest @ValueSource(ints={-1,0,1})
    void manualDeadlineIsStrictAndAutomaticActionSurvives(int offset) {
        var now=DEADLINE.plusSeconds(offset);
        assertThat(ReassignmentActions.manualAllowed(now,DEADLINE)).isEqualTo(offset<0);
        assertThat(ReassignmentActions.forAlert(true,now,DEADLINE))
                .containsExactlyElementsOf(offset<0 ? java.util.List.of("REASSIGN","AUTO_REASSIGN") : java.util.List.of("AUTO_REASSIGN"));
    }
    @Test void ineligibleAlertNeverOffersManualOrAutomaticAction() {
        assertThat(ReassignmentActions.forAlert(false,DEADLINE.minusSeconds(1),DEADLINE)).isEmpty();
        assertThat(ReassignmentActions.forAlert(false,DEADLINE,DEADLINE)).isEmpty();
        assertThat(ReassignmentActions.manualAllowed(DEADLINE,null)).isFalse();
    }
}

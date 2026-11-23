package com.routeroad;

import com.routeroad.execution.application.*;
import com.routeroad.execution.domain.*;
import com.routeroad.simulation.application.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AlertQueryServiceTests {
    private static final LocalDate DATE=LocalDate.of(2026,11,21);
    private static final Instant NOW=Instant.parse("2026-11-21T09:00:00Z");
    private final AlertReader reader=mock(AlertReader.class);
    private final ScenarioReader scenarios=mock(ScenarioReader.class);
    private final AlertQueryService service=new AlertQueryService(reader,
            new ScenarioClockQuery(scenarios,java.time.Clock.fixed(NOW,ZoneOffset.UTC)));
    @BeforeEach void setup() {
        when(scenarios.findCurrent()).thenReturn(Optional.of(new ScenarioReader.Scenario(UUID.randomUUID(),DATE,NOW,NOW,1,1L, java.util.Map.of())));
        when(reader.find(any(), anyInt(), anyLong(), anyBoolean(), any())).thenReturn(new AlertReader.Result(List.of(),0));
    }
    @Test void defaultDateAndPaginationUseExistingClock() {
        var result=service.get(null,null,null,null,1,10);
        assertThat(result.items()).isEmpty();assertThat(result.total()).isZero();assertThat(result.clock().now()).isEqualTo(NOW);
        verify(reader).find(new AlertReader.Filter(DATE,null,null,null), 10, 0, true, NOW);
    }
    @Test void filtersAndLiteralSearchAreForwardedBeforePagination() {
        service.get(DATE,"  КоД_%! ",AlertStatus.OPEN,AlertSeverity.CRITICAL,3,2);
        verify(reader).find(new AlertReader.Filter(DATE,"%код!_!%!!%",AlertStatus.OPEN,AlertSeverity.CRITICAL), 2, 4, true, NOW);
    }
    @Test void historicalDayDisablesActionsAndKeepsCurrentClock() {
        var result=service.get(DATE.minusDays(1),null,null,null,1,10);
        verify(reader).find(new AlertReader.Filter(DATE.minusDays(1),null,null,null), 10, 0, false, NOW);
        assertThat(result.clock().now()).isEqualTo(NOW);
    }
    @Test void invalidPaginationDoesNotReadDatabase() {
        for(int[] pair:List.of(new int[]{0,10},new int[]{-1,10},new int[]{1,0},new int[]{1,101}))
            assertThatThrownBy(()->service.get(null,null,null,null,pair[0],pair[1])).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(reader,scenarios);
    }
    @Test void largePageOffsetDoesNotOverflowIntegerArithmetic() {
        service.get(null,null,null,null,Integer.MAX_VALUE,100);
        verify(reader).find(new AlertReader.Filter(DATE,null,null,null), 100, 214748364600L, true, NOW);
    }
    @Test void missingScenarioIsConflictWithoutReadingAlerts() {
        when(scenarios.findCurrent()).thenReturn(Optional.empty());
        assertThatThrownBy(()->service.get(null,null,null,null,1,10)).isInstanceOf(ScenarioNotInitializedException.class);
        verifyNoInteractions(reader);
    }
    @Test void oneReadOnlyRepeatableReadTransactionCoversPageAndTotal() throws Exception {
        var tx=AlertQueryService.class.getMethod("get",LocalDate.class,String.class,AlertStatus.class,AlertSeverity.class,int.class,int.class)
                .getAnnotation(Transactional.class);
        assertThat(tx.readOnly()).isTrue();assertThat(tx.isolation()).isEqualTo(Isolation.REPEATABLE_READ);
    }
}

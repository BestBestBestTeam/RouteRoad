package com.routeroad;

import com.routeroad.offices.api.OfficeView;
import com.routeroad.planning.domain.PlanningAlgorithm;
import com.routeroad.planning.domain.RouteStopType;
import com.routeroad.planning.infrastructure.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class RouteGeometryTests {
    private static final UUID ROUTE=UUID.randomUUID();
    private static final Instant NOW=Instant.parse("2026-11-21T09:00:00Z");
    private final RouteViewAssembler assembler=new RouteViewAssembler();
    private RouteEntity route() {
        var route=mock(RouteEntity.class);
        var run=mock(PlanningRunEntity.class);
        when(run.getPlanVersion()).thenReturn(9_007_199_254_740_993L);
        when(route.getId()).thenReturn(ROUTE);
        when(route.getCourierId()).thenReturn(UUID.randomUUID());
        when(route.getPlanDate()).thenReturn(LocalDate.of(2026,11,21));
        when(route.getPlanningRun()).thenReturn(run);
        when(route.getActive()).thenReturn(true);
        when(route.getAlgorithm()).thenReturn(PlanningAlgorithm.GREEDY);
        when(route.getTotalDistanceM()).thenReturn(100);
        when(route.getViolatedWindows()).thenReturn(0);
        when(route.getCreatedAt()).thenReturn(NOW);
        return route;
    }
    private RouteStopEntity stop(int sequence,UUID office) {
        var stop=mock(RouteStopEntity.class);
        when(stop.getId()).thenReturn(new RouteStopId(ROUTE,sequence));
        when(stop.getType()).thenReturn(RouteStopType.OFFICE);
        when(stop.getOfficeId()).thenReturn(office);
        when(stop.getArrivalEta()).thenReturn(NOW.plusSeconds(sequence*600L));
        when(stop.getDepartureEta()).thenReturn(NOW.plusSeconds(sequence*600L+60));
        when(stop.getDistanceFromPrevM()).thenReturn(sequence==1?0:100);
        when(stop.getWindowViolationS()).thenReturn(0);
        return stop;
    }
    @ParameterizedTest @ValueSource(ints={0,1,2})
    void geometryNeedsTwoRealPointsAndNeverRemovesTheRouteOrStops(int count) {
        var stops=new ArrayList<RouteStopEntity>();
        var offices=new LinkedHashMap<UUID,OfficeView>();
        for(int n=0;n<count;n++) {
            var id=UUID.randomUUID();
            offices.put(id,new OfficeView(id,"Office","Address",55+n,82+n));stops.add(stop(n+1,id));
        }
        var result=assembler.assemble(route(),stops,List.of(),offices);
        assertThat(result.id()).isEqualTo(ROUTE);assertThat(result.stops()).hasSize(count);
        if(count<2) assertThat(result.geometry()).isNull();
        else assertThat(result.geometry().coordinates()).containsExactly(List.of(82.0,55.0),List.of(83.0,56.0));
    }
    @Test void severalStopsWithOnlyOneValidCoordinateKeepAllStopsAndNullGeometry() {
        var a=UUID.randomUUID();var b=UUID.randomUUID();var c=UUID.randomUUID();
        var stops=List.of(stop(1,a),stop(2,b),stop(3,c));
        var offices=Map.of(a,new OfficeView(a,"A","A",55,82),
                b,new OfficeView(b,"B","B",Double.NaN,83),
                c,new OfficeView(c,"C","C",56,Double.POSITIVE_INFINITY));
        var result=assembler.assemble(route(),stops,List.of(),offices);
        assertThat(result.geometry()).isNull();
        assertThat(result.stops()).extracting(s->s.sequence()).containsExactly(1,2,3);
        assertThat(result.stops().get(1).arrivalEta()).isEqualTo(NOW.plusSeconds(1200));
    }
    @Test void validCoordinatesRetainTheirSequenceAroundAnInvalidCoordinate() {
        var a=UUID.randomUUID();var b=UUID.randomUUID();var c=UUID.randomUUID();
        var result=assembler.assemble(route(),List.of(stop(1,a),stop(2,b),stop(3,c)),List.of(),Map.of(
                a,new OfficeView(a,"A","A",55,82),b,new OfficeView(b,"B","B",91,83),c,new OfficeView(c,"C","C",56,84)));
        assertThat(result.stops()).hasSize(3);
        assertThat(result.geometry().type()).isEqualTo("LineString");
        assertThat(result.geometry().coordinates()).containsExactly(List.of(82.0,55.0),List.of(84.0,56.0));
        assertThat(result.geometryKind()).isEqualTo("straight");
    }
    @Test void allInvalidCoordinatesDoNotCreateAnEmptyLineString() {
        var a=UUID.randomUUID();var b=UUID.randomUUID();
        var result=assembler.assemble(route(),List.of(stop(1,a),stop(2,b)),List.of(),Map.of(
                a,new OfficeView(a,"A","A",Double.NEGATIVE_INFINITY,82),b,new OfficeView(b,"B","B",55,181)));
        assertThat(result.stops()).hasSize(2);assertThat(result.geometry()).isNull();
    }
    @Test void missingStopTargetIsStillADataErrorRatherThanAReplacementCoordinate() {
        assertThatThrownBy(()->assembler.assemble(route(),List.of(stop(1,UUID.randomUUID())),List.of(),Map.of()))
                .isInstanceOf(com.routeroad.couriers.application.InvalidScheduleException.class);
    }
}

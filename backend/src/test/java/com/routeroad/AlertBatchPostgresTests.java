package com.routeroad;

import com.routeroad.execution.api.AlertView;
import com.routeroad.execution.application.OrderAlerts;
import com.routeroad.execution.infrastructure.AlertRepository;
import com.routeroad.execution.infrastructure.AlertViewAssembler;
import com.routeroad.simulation.infrastructure.ScenarioStateRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@SpringBootTest
@ActiveProfiles("local")
@EnabledIfEnvironmentVariable(named="ROUTEROAD_REPOSITORY_TESTS",matches="true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Transactional
@Rollback
@Sql({"/repository-fixtures.sql","/schedule-fixtures.sql","/map-fixtures.sql","/alert-fixtures.sql"})
class AlertBatchPostgresTests {
    static final LocalDate DATE=LocalDate.of(2099,11,9);
    @Autowired AlertRepository repository;
    @Autowired AlertViewAssembler assembler;
    @Autowired OrderAlerts orderAlerts;
    @Autowired ScenarioStateRepository scenarios;
    @Autowired JdbcTemplate jdbc;
    static UUID id(int group,int number) {return UUID.fromString("a0f8d%03x-0000-4000-8000-%012x".formatted(group,number));}
    @BeforeAll void preserveScenario() {assumeTrue(scenarios.count()==0,"Existing scenario must be preserved");}
    private List<AlertView> batch(int...numbers) {
        return assembler.assemble(repository.findViewsByIds(Arrays.stream(numbers).mapToObj(n->id(0x800,n)).toList(),DATE), true, java.time.Instant.parse("2099-11-09T09:00:10Z"));
    }
    @Test void completeGroupsAndResolutionsMatchOrderDetails() {
        var result=batch(5,6,11);
        assertThat(result).hasSize(3);
        assertThat(result.get(0).orders()).hasSize(3);
        assertThat(result.get(0).orders()).extracting(AlertView.OrderEntry::status).containsExactly("pending","pending","resolved");
        assertThat(result.get(0).resolution()).isNull();
        assertThat(result.get(1).resolution().reason()).isEqualTo("Заявки переданы");
        assertThat(result.get(1).orders().get(0).resolution().actor().id()).isNull();
        assertThat(result.get(1).orders().get(1).resolution().actor().id()).isEqualTo(id(0x600,1));
        var detail=orderAlerts.find(id(0x300,1), DATE, null, true, java.time.Instant.parse("2099-11-09T09:00:10Z"));
        for(var alert:result) assertThat(detail).contains(alert);
    }
    @Test void openHeaderAndPendingElementAreBothInsufficientWithoutAnEligibleOrder() {
        var result=batch(5,7,8,9,10);
        assertThat(result.get(0).availableActions()).containsExactly("REASSIGN","AUTO_REASSIGN");
        assertThat(result.subList(1,result.size())).allSatisfy(a->assertThat(a.availableActions()).isEmpty());
        assertThat(result.get(3).reasonText()).isEmpty();
        assertThat(result.get(4).orders()).isEmpty();
    }
    @Test void nullAndEmptyReasonsPreserveTheirExistingContractMeaning() {
        var alert=batch(11).getFirst();
        assertThat(alert.resolutionReason()).isNull();assertThat(alert.resolution().reason()).isEmpty();
        assertThat(alert.orders().getFirst().resolution().reason()).isEmpty();
        assertThat(batch(9).getFirst().reasonText()).isEmpty();
    }
    @Test void reasonTextNullIsRejectedByTheExistingSchema() {
        jdbc.execute((ConnectionCallback<Void>)connection->{
            var savepoint=connection.setSavepoint();
            try(var statement=connection.createStatement()) {
                assertThatThrownBy(()->statement.executeUpdate("update alert set reason_text=null where id='a0f8d800-0000-4000-8000-000000000009'"))
                        .isInstanceOf(java.sql.SQLException.class).extracting(e->((java.sql.SQLException)e).getSQLState()).isEqualTo("23502");
            } finally {connection.rollback(savepoint);connection.releaseSavepoint(savepoint);}
            return null;
        });
        assertThat(batch(9).getFirst().reasonText()).isEmpty();
    }
    @Test void batchIsLimitedBySelectedIdsAndDate() {
        assertThat(batch(1,4,5)).extracting(AlertView::id).containsExactly(id(0x800,1),id(0x800,5));
        var result=assembler.assemble(repository.findViewsByIds(List.of(id(0x800,4)),DATE.plusDays(1)), false, java.time.Instant.parse("2099-11-09T09:00:10Z"));
        assertThat(result).hasSize(1).allSatisfy(a->assertThat(a.availableActions()).isEmpty());
    }
    @Test void representativeStillCannotReadGroupDetails() {
        var result=orderAlerts.find(id(0x300,1), DATE, id(0x200,1), false, java.time.Instant.parse("2099-11-09T09:00:10Z"));
        assertThat(result).allSatisfy(a->{assertThat(a.scope()).isEqualTo("order");assertThat(a.orderIds()).containsExactly(id(0x300,1));assertThat(a.availableActions()).isEmpty();});
        assertThat(result).extracting(AlertView::id).doesNotContain(id(0x800,5),id(0x800,6),id(0x800,9));
    }
}

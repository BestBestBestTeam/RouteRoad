package com.routeroad;

import com.routeroad.execution.domain.BusinessEventType;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import java.util.Arrays;
import java.util.regex.Pattern;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("local")
@EnabledIfEnvironmentVariable(named="ROUTEROAD_REPOSITORY_TESTS",matches="true")
class FlywayV3PostgresTests {
    @Autowired Flyway flyway;
    @Autowired JdbcTemplate jdbc;

    @Test void checkContainsExactlyThirteenJavaTypesAndRemainsValidated() {
        var definition=jdbc.queryForObject("""
                select pg_get_constraintdef(oid) from pg_constraint
                where conrelid='business_event'::regclass and conname='business_event_type_check'
                """,String.class);
        var values=Pattern.compile("'([A-Z_]+)'").matcher(definition).results().map(m->m.group(1)).toList();
        assertThat(values).containsExactlyInAnyOrderElementsOf(Arrays.stream(BusinessEventType.values()).map(Enum::name).toList());
        assertThat(values).hasSize(13);
        assertThat(jdbc.queryForObject("""
                select convalidated from pg_constraint
                where conrelid='business_event'::regclass and conname='business_event_type_check'
                """,Boolean.class)).isTrue();
    }
    @Test void flywayValidatesAndDoesNotExecuteV3Again() {
        flyway.validate();
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("3");
        assertThat(flyway.info().pending()).isEmpty();
        assertThat(jdbc.queryForObject("select count(*) from flyway_schema_history where version='3' and success",Long.class)).isEqualTo(1);
        var before=historyDigest();
        assertThat(flyway.migrate().migrationsExecuted).isZero();
        assertThat(historyDigest()).isEqualTo(before);
    }
    private String historyDigest() {
        return jdbc.queryForObject("select md5(string_agg(row_to_json(t)::text,',' order by installed_rank)) from flyway_schema_history t",String.class);
    }
}

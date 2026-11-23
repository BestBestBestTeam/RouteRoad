package com.routeroad;

import com.routeroad.simulation.infrastructure.ScenarioStateRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;
import javax.sql.DataSource;
import java.net.*;
import java.net.http.*;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.when;

/** Real TCP, embedded Tomcat, cookie sessions and PostgreSQL. Committed fixtures are removed in finally. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("local")
@EnabledIfEnvironmentVariable(named = "ROUTEROAD_REPOSITORY_TESTS", matches = "true")
class WorkdayLiveHttpPostgresTests {
    private static final List<String> TABLES = List.of("office","courier","service_order","planning_run","route",
            "route_stop","assignment","app_user","business_event","alert","alert_order","api_command","scenario_state");
    @LocalServerPort int port;
    @Autowired DataSource dataSource;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    @Autowired ScenarioStateRepository scenarios;
    @Autowired PasswordEncoder passwords;
    @Autowired JsonMapper json;
    @MockitoBean java.time.Clock realClock;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean
    com.routeroad.workday.infrastructure.JpaWorkdayReader workdayReader;
    private String origin() { return "http://localhost:" + port; }
    private HttpResponse<String> get(HttpClient client, String path, int expected) throws Exception {
        var response = client.send(HttpRequest.newBuilder(URI.create(origin()+path)).GET().build(), HttpResponse.BodyHandlers.ofString());
        System.out.printf("Live HTTP GET %s -> %d%n", path, response.statusCode());
        assertThat(response.statusCode()).as(path).isEqualTo(expected);
        return response;
    }
    private void login(HttpClient client, String login, String password) throws Exception {
        var initial = get(client, "/api/me", 401);
        var response = client.send(HttpRequest.newBuilder(URI.create(origin()+"/api/auth/login"))
                .header("Origin", origin()).header("Content-Type","application/json")
                .header("X-CSRF-Token", initial.headers().firstValue("X-CSRF-Token").orElseThrow())
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(Map.of("login",login,"password",password))))
                .build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
    }
    private HttpClient client() {
        return HttpClient.newBuilder().cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL)).build();
    }
    @Test void realHttpUsesTheFullStackAndLeavesThePersistentDatabaseUnchanged() throws Exception {
        assumeTrue(scenarios.count()==0,"Existing scenario must be preserved");
        // Cleanup must never overlap pre-existing records, even if an earlier interrupted test left fixtures.
        for (String table:TABLES) {
            if (!table.equals("planning_run")) {
                String key = switch (table) {
                    case "route_stop" -> "route_id"; case "assignment" -> "order_id";
                    case "alert_order" -> "alert_id"; case "scenario_state" -> "scenario_id"; default -> "id";
                };
                assumeTrue(jdbc.queryForObject("select count(*) from "+table+" where "+key+"::text like 'a0f8d%'",Long.class)==0,
                        "Pre-existing fixture IDs must be preserved");
            }
        }
        assumeTrue(jdbc.queryForObject("select count(*) from planning_run where plan_version between 9000000000000000001 and 9000000000000000003",Long.class)==0);
        var before = digests();
        when(realClock.instant()).thenReturn(Instant.parse("2099-11-09T09:00:10Z"));
        String password = UUID.randomUUID().toString();
        var transaction = new TransactionTemplate(transactions);
        try {
            transaction.executeWithoutResult(status -> {
                new ResourceDatabasePopulator(new ClassPathResource("repository-fixtures.sql"),
                        new ClassPathResource("workday-fixtures.sql")).execute(dataSource);
                jdbc.update("update app_user set password_hash=? where login in ('repo-dispatcher','repo-representative')",passwords.encode(password));
            });
            org.mockito.Mockito.doAnswer(invocation -> {
                assertThat(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
                assertThat(org.springframework.transaction.support.TransactionSynchronizationManager.isCurrentTransactionReadOnly()).isTrue();
                assertThat(org.springframework.transaction.support.TransactionSynchronizationManager.getCurrentTransactionIsolationLevel())
                    .isEqualTo(java.sql.Connection.TRANSACTION_REPEATABLE_READ);
                return invocation.callRealMethod();
            }).when(workdayReader).count(org.mockito.ArgumentMatchers.any());
            try (var anonymous=client(); var dispatcher=client(); var representative=client()) {
                assertThat(json.readTree(get(anonymous,"/api/health",200).body()).get("status").asString()).isEqualTo("UP");
                String path="/api/workday";
                get(anonymous,path,401);
                login(dispatcher,"repo-dispatcher",password);
                tools.jackson.databind.JsonNode baseline = null;
                for (String config : List.of("{}", "{\"algorithm\":\"greedy\"}", "{\"algorithm\":\"ortools\"}")) {
                    transaction.executeWithoutResult(status -> jdbc.update("update scenario_state set config=cast(? as jsonb)",config));
                    var beforeGet=digests();
                    var response=get(dispatcher,path,200);
                    var view=json.readTree(response.body());
                    assertThat(view.properties().stream().map(Map.Entry::getKey).toList()).containsExactlyInAnyOrder(
                            "date","counts","has_routes","algorithm","offices","clock");
                    assertThat(view.get("algorithm").asString()).isEqualTo(config.contains("ortools") ? "ortools" : "greedy");
                    assertThat(view.get("counts").get("orders_total").asInt()).isEqualTo(7);
                    assertThat(view.get("clock").get("now").asString()).isEqualTo("2099-11-09T09:00:10Z");
                    assertThat(view.get("clock").get("revision").isString()).isTrue();
                    var fields=(tools.jackson.databind.node.ObjectNode)view.deepCopy();
                    fields.remove("algorithm");
                    if (baseline==null) baseline=fields; else assertThat(fields).isEqualTo(baseline);
                    assertThat(digests()).as("GET must not persist the default or modify any business row").isEqualTo(beforeGet);
                    if (config.contains("ortools")) System.out.println("Live HTTP WorkdayView: "+response.body());
                }
                assertThat(json.readTree(get(dispatcher,path+"?date=2099-11-10",200).body()).get("algorithm").asString()).isEqualTo("ortools");
                get(dispatcher,path+"?date=2099-02-30",400);
                login(representative,"repo-representative",password);
                get(representative,path,403);
                transaction.executeWithoutResult(status -> jdbc.update("update scenario_state set config=cast(? as jsonb)",
                        "{\"algorithm\":\"private-invalid-setting\"}"));
                var beforeInvalidGet=digests();
                var invalid=get(dispatcher,path,500);
                assertThat(json.readTree(invalid.body()).get("error").get("code").asString()).isEqualTo("INTERNAL_ERROR");
                assertThat(invalid.body()).doesNotContain("private-invalid-setting","algorithm");
                get(dispatcher,"/api/orders",200);
                get(dispatcher,"/api/couriers",200);
                assertThat(digests()).isEqualTo(beforeInvalidGet);
                jdbc.update("delete from scenario_state where scenario_id='a0f8db00-0000-4000-8000-000000000001'");
                assertThat(json.readTree(get(dispatcher,path,409).body()).get("error").get("code").asString())
                        .isEqualTo("SCENARIO_NOT_INITIALIZED");
            }
        } finally {
            transaction.executeWithoutResult(status -> {
                for (String table:List.of("alert_order","alert","business_event","assignment","route_stop","route",
                        "planning_run","api_command","service_order","app_user","courier","office","scenario_state")) {
                    String where = table.equals("planning_run") ? "plan_version between 9000000000000000001 and 9000000000000000003"
                            : (switch(table) { case "route_stop" -> "route_id"; case "assignment" -> "order_id";
                                case "alert_order" -> "alert_id"; case "scenario_state" -> "scenario_id"; default -> "id"; })
                                + "::text like 'a0f8d%'";
                    jdbc.update("delete from "+table+" where "+where);
                }
            });
            assertThat(digests()).as("Permanent rows, Flyway history and planning sequence after cleanup").isEqualTo(before);
            System.out.println("Live HTTP fixtures removed; all 13 table digests, Flyway history and sequence unchanged.");
        }
    }
    private Map<String,String> digests() {
        var result=new LinkedHashMap<String,String>();
        for (String table:TABLES)
            result.put(table,jdbc.queryForObject("select md5(coalesce(string_agg(row_to_json(t)::text,',' order by row_to_json(t)::text),'')) from "+table+" t",String.class));
        result.put("flyway",jdbc.queryForObject("select md5(string_agg(row_to_json(t)::text,',' order by installed_rank)) from flyway_schema_history t",String.class));
        result.put("sequence",jdbc.queryForObject("select last_value::text||':'||is_called::text from planning_run_plan_version_seq",String.class));
        return result;
    }
}

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
class HistoryLiveHttpPostgresTests {
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
    com.routeroad.execution.infrastructure.JpaHistoryReader historyReader;
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
                        new ClassPathResource("history-fixtures.sql")).execute(dataSource);
                jdbc.update("update app_user set password_hash=? where login in ('repo-dispatcher','repo-representative')",passwords.encode(password));
                jdbc.update("""
                        insert into business_event(id,type,service_date,courier_id,actor_id,occurred_at,created_at,reason,payload)
                        values('a0f8dc00-0000-4000-8000-00000000000d','OFFLINE_ACTION_CONFLICT','2099-11-09',
                            'a0f8d200-0000-4000-8000-000000000001','a0f8d600-0000-4000-8000-000000000002',
                            '2099-11-09T01:07:00Z','2099-11-09T10:13:00Z',?,cast(? as jsonb))
                        """, "Заявка отменена до применения офлайн-действия", """
                        {"reason_code":"ORDER_CANCELLED","client_action_ids":["a0f8da00-0000-4000-8000-000000000001"],
                         "command_ids":["a0f8d900-0000-4000-8000-000000000001"],
                         "actor":{"id":"a0f8d600-0000-4000-8000-000000000002","name":"Имя на момент конфликта","role":"representative"},
                         "changes":[{"field":"attempted_status","before":"ARRIVED","after":"IN_PROGRESS"}],"secret":"must not be exposed"}
                        """);
            });
            org.mockito.Mockito.doAnswer(invocation -> {
                assertThat(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
                assertThat(org.springframework.transaction.support.TransactionSynchronizationManager.isCurrentTransactionReadOnly()).isTrue();
                assertThat(org.springframework.transaction.support.TransactionSynchronizationManager.getCurrentTransactionIsolationLevel())
                    .isEqualTo(java.sql.Connection.TRANSACTION_REPEATABLE_READ);
                return invocation.callRealMethod();
            }).when(historyReader).find(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyInt(),org.mockito.ArgumentMatchers.anyLong());
            var fixtureState = digests();
            try (var anonymous=client(); var dispatcher=client(); var representative=client()) {
                assertThat(json.readTree(get(anonymous,"/api/health",200).body()).get("status").asString()).isEqualTo("UP");
                String path="/api/history";
                get(anonymous,path,401);
                login(dispatcher,"repo-dispatcher",password);
                var page=json.readTree(get(dispatcher,path,200).body());
                assertThat(page.size()).isEqualTo(5);assertThat(page.get("total").asInt()).isEqualTo(16);
                assertThat(page.get("items").get(0).get("id").asString()).isEqualTo("a0f8dc00-0000-4000-8000-00000000000d");
                assertThat(page.get("items").get(0).size()).isEqualTo(10);
                assertThat(page.get("clock").get("revision").isString()).isTrue();
                var conflicts=json.readTree(get(dispatcher,path+"?type=sync_conflict",200).body());
                assertThat(conflicts.get("total").asInt()).isEqualTo(1);
                assertThat(conflicts.get("items").size()).isEqualTo(1);
                var conflict=conflicts.get("items").get(0);
                assertThat(conflict.size()).isEqualTo(10);
                assertThat(conflict.get("type").asString()).isEqualTo("sync_conflict");
                assertThat(conflict.get("title").asString()).isEqualTo("Офлайн-действие отклонено");
                assertThat(conflict.get("reason").asString()).isEqualTo("Заявка отменена до применения офлайн-действия");
                assertThat(conflict.get("actor").get("name").asString()).isEqualTo("Имя на момент конфликта");
                assertThat(conflict.get("actor").get("role").asString()).isEqualTo("representative");
                assertThat(conflict.get("order_id").isNull()).isTrue();assertThat(conflict.get("alert_id").isNull()).isTrue();
                assertThat(conflict.get("changes").get(0).get("field").asString()).isEqualTo("attempted_status");
                assertThat(conflict.get("changes").get(0).get("before").asString()).isEqualTo("ARRIVED");
                assertThat(conflict.get("changes").get(0).get("after").asString()).isEqualTo("IN_PROGRESS");
                assertThat(conflict.get("description").asString()).contains("ORDER_CANCELLED",
                        "a0f8da00-0000-4000-8000-000000000001","a0f8d900-0000-4000-8000-000000000001");
                assertThat(conflicts.toString()).doesNotContain("secret","must not be exposed","payload","occurred_at");
                var searched=json.readTree(get(dispatcher,path+"?type=sync_conflict&search=ORDER_CANCELLED&actor_role=representative",200).body());
                assertThat(searched.get("items")).isEqualTo(conflicts.get("items"));
                System.out.println("Live HTTP sync_conflict: "+conflict);
                var filtered=json.readTree(get(dispatcher,path+"?type=status_changed&actor_role=representative&order_id=a0f8d300-0000-4000-8000-000000000001",200).body());
                assertThat(filtered.get("total").asInt()).isEqualTo(1);
                var entry=filtered.get("items").get(0);
                assertThat(entry.get("actor").get("name").asString()).isEqualTo("Историческое имя");
                assertThat(entry.get("changes").get(0).get("before").asString()).isEqualTo("9007199254740993");
                System.out.println("Live HTTP HistoryEntry: "+entry);
                var literal=json.readTree(get(dispatcher,path+"?search="+URLEncoder.encode("_%!",java.nio.charset.StandardCharsets.UTF_8),200).body());
                assertThat(literal.get("total").asInt()).isEqualTo(1);
                var group=json.readTree(get(dispatcher,path+"?type=routes_built&order_id=a0f8d300-0000-4000-8000-000000000002",200).body());
                assertThat(group.get("total").asInt()).isEqualTo(2);
                var tomorrow=json.readTree(get(dispatcher,path+"?date=2099-11-10",200).body());
                assertThat(tomorrow.get("total").asInt()).isZero();
                var beyond=json.readTree(get(dispatcher,path+"?page=3",200).body());
                assertThat(beyond.get("items").isEmpty()).isTrue();assertThat(beyond.get("total").asInt()).isEqualTo(16);
                get(dispatcher,path+"?date=2099-02-30",400);
                get(dispatcher,path+"?type=PLAN_ACTIVATED",400);
                get(dispatcher,path+"?actor_role=SYSTEM",400);
                get(dispatcher,path+"?order_id=bad",400);
                get(dispatcher,path+"?page_size=101",400);
                login(representative,"repo-representative",password);
                get(representative,path,403);
                assertThat(digests()).as("All authenticated and anonymous GETs must leave every row unchanged").isEqualTo(fixtureState);
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

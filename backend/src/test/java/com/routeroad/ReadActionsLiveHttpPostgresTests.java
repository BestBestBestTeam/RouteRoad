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
class ReadActionsLiveHttpPostgresTests {
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
    com.routeroad.orders.infrastructure.JpaOrderReader orderReader;
    private static final String ORDER="a0f8d300-0000-4000-8000-000000000005";
    private static final String OTHER="a0f8d300-0000-4000-8000-000000000004";
    private static final String COURIER="a0f8d200-0000-4000-8000-000000000001";
    private static final String GROUP="a0f8d800-0000-4000-8000-000000000003";
    private static final Instant REAL=Instant.parse("2099-11-09T09:00:10Z");
    private static final Instant DEADLINE=Instant.parse("2099-11-09T09:13:00Z");
    private String origin() { return "http://localhost:" + port; }
    private HttpResponse<String> get(HttpClient client, String path, int expected) throws Exception {
        var response = client.send(HttpRequest.newBuilder(URI.create(origin()+path)).GET().build(), HttpResponse.BodyHandlers.ofString());
        System.out.printf("Live HTTP GET %s -> %d%n", path, response.statusCode());
        assertThat(response.statusCode()).as(path).isEqualTo(expected);
        return response;
    }
    private String login(HttpClient client, String login, String password) throws Exception {
        var initial = get(client, "/api/me", 401);
        var response = client.send(HttpRequest.newBuilder(URI.create(origin()+"/api/auth/login"))
                .header("Origin", origin()).header("Content-Type","application/json")
                .header("X-CSRF-Token", initial.headers().firstValue("X-CSRF-Token").orElseThrow())
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(Map.of("login",login,"password",password))))
                .build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        return json.readTree(response.body()).get("csrf_token").asString();
    }
    private HttpResponse<String> post(HttpClient client,String path,String token,String body,String media,int status) throws Exception {
        var request=HttpRequest.newBuilder(URI.create(origin()+path)).header("Origin",origin());
        if(token!=null) request.header("X-CSRF-Token",token);
        if(media!=null) request.header("Content-Type",media);
        var response=client.send(request.POST(HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
        System.out.printf("Live HTTP POST %s -> %d%n",path,response.statusCode());
        assertThat(response.statusCode()).as(path).isEqualTo(status);
        assertThat(response.body()).doesNotContain("password_hash","stackTrace","private-config","private-body");
        return response;
    }
    private tools.jackson.databind.JsonNode read(HttpClient client,String path,int status) throws Exception {
        var before=digests();var response=get(client,path,status);
        assertThat(digests()).as("GET must not mutate business rows").isEqualTo(before);
        return json.readTree(response.body());
    }
    private tools.jackson.databind.JsonNode orderIn(tools.jackson.databind.JsonNode array,String id) {
        for(var item:array) if(item.get("id").asString().equals(id)) return item;
        throw new AssertionError("Missing order: "+id);
    }
    private void clock(Instant now,double speed) {
        jdbc.update("update scenario_state set model_anchor_at=?,real_anchor_at=?,speed=?",now.atOffset(java.time.ZoneOffset.UTC),REAL.atOffset(java.time.ZoneOffset.UTC),speed);
        org.mockito.Mockito.when(realClock.instant()).thenReturn(REAL);
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
                        new ClassPathResource("schedule-fixtures.sql"),new ClassPathResource("map-fixtures.sql"),new ClassPathResource("alert-fixtures.sql")).execute(dataSource);
                jdbc.update("update app_user set password_hash=? where login in ('repo-dispatcher','repo-representative')",passwords.encode(password));
            });
            jdbc.update("update alert set status='resolved',resolved_at=created_at where status='open' and id<>cast(? as uuid)",GROUP);
            jdbc.update("update service_order set status='ARRIVED' where id in (cast(? as uuid),cast(? as uuid))",ORDER,OTHER);
            clock(DEADLINE.minusSeconds(1),1);
            org.mockito.stubbing.Answer<Object> readTransaction=invocation->{
                assertThat(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
                assertThat(org.springframework.transaction.support.TransactionSynchronizationManager.isCurrentTransactionReadOnly()).isTrue();
                assertThat(org.springframework.transaction.support.TransactionSynchronizationManager.getCurrentTransactionIsolationLevel())
                        .isEqualTo(java.sql.Connection.TRANSACTION_REPEATABLE_READ);
                return invocation.callRealMethod();
            };
            org.mockito.Mockito.doAnswer(readTransaction).when(orderReader).find(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyInt(),org.mockito.ArgumentMatchers.anyLong(),org.mockito.ArgumentMatchers.anyBoolean(),org.mockito.ArgumentMatchers.any());
            org.mockito.Mockito.doAnswer(readTransaction).when(orderReader).findOne(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyBoolean(),org.mockito.ArgumentMatchers.any());
            org.mockito.Mockito.doAnswer(readTransaction).when(orderReader).findSchedule(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyBoolean(),org.mockito.ArgumentMatchers.any());
            try (var anonymous=client();var dispatcher=client();var representative=client()) {
                assertThat(read(anonymous,"/api/health",200).get("status").asString()).isEqualTo("UP");
                get(anonymous,"/api/orders",401);
                var unsupported=get(anonymous,"/api/auth/login",405);
                assertThat(unsupported.headers().firstValue("Allow").orElseThrow()).contains("POST");
                var bootstrap=get(anonymous,"/api/me",401);
                post(anonymous,"/api/auth/logout",null,"",null,403);
                post(anonymous,"/api/auth/logout","invalid","",null,403);
                post(anonymous,"/api/auth/logout",bootstrap.headers().firstValue("X-CSRF-Token").orElseThrow(),"",null,401);
                bootstrap=get(anonymous,"/api/me",401);
                post(anonymous,"/api/auth/login",bootstrap.headers().firstValue("X-CSRF-Token").orElseThrow(),"private-body","text/plain",415);
                String dispatcherToken=login(dispatcher,"repo-dispatcher",password);
                String representativeToken=login(representative,"repo-representative",password);
                get(representative,"/api/alerts",403);
                get(dispatcher,"/api/orders?page_size=101",400);
                get(dispatcher,"/api/orders/00000000-0000-4000-8000-000000000000",404);
                get(dispatcher,"/api/no-such-resource",404);
                var method=post(dispatcher,"/api/orders",dispatcherToken,"",null,405);
                assertThat(method.headers().firstValue("Allow").orElseThrow()).contains("GET");
                var stale=HttpRequest.newBuilder(URI.create(origin()+"/api/auth/register"))
                        .header("Origin",origin()).header("X-CSRF-Token",dispatcherToken).header("X-Scenario-ID",UUID.randomUUID().toString())
                        .header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(
                                Map.of("login","unused-stale-registration","password",password,"name","Test name","courier_id",COURIER)))).build();
                var staleResponse=dispatcher.send(stale,HttpResponse.BodyHandlers.ofString());
                assertThat(staleResponse.statusCode()).isEqualTo(409);
                jdbc.update("update scenario_state set config=cast(? as jsonb)","{\"algorithm\":\"private-config\"}");
                var internal=read(dispatcher,"/api/workday",500);
                assertThat(internal.get("error").get("code").asString()).isEqualTo("INTERNAL_ERROR");
                assertThat(internal.toString()).doesNotContain("private-config");
                jdbc.update("update scenario_state set config='{}'");
                var own=read(representative,"/api/orders/"+ORDER,200);
                assertThat(own.get("order").get("available_actions").toString()).isEqualTo("[\"REPORT_DELAY\",\"REFUSE_ORDER\"]");
                var ownSchedule=read(representative,"/api/couriers/"+COURIER+"/schedule",200);
                assertThat(orderIn(ownSchedule.get("orders"),ORDER).get("available_actions")).isEqualTo(own.get("order").get("available_actions"));
                assertThat(orderIn(ownSchedule.get("orders"),OTHER).get("available_actions").toString()).contains("IN_PROGRESS");
                get(representative,"/api/orders/a0f8d300-0000-4000-8000-000000000002",403);
                for(int offset:new int[]{-1,0,1}) {
                    clock(DEADLINE.plusSeconds(offset),1);
                    var card=read(dispatcher,"/api/orders/"+ORDER,200);
                    var list=read(dispatcher,"/api/orders?page_size=100",200);
                    var schedule=read(dispatcher,"/api/couriers/"+COURIER+"/schedule",200);
                    var group=read(dispatcher,"/api/alerts?status=open",200).get("items").get(0);
                    String expected=offset<0 ? "[\"REASSIGN\"]" : "[]";
                    assertThat(card.get("order").get("available_actions").toString()).isEqualTo(expected);
                    assertThat(orderIn(list.get("items"),ORDER).get("available_actions")).isEqualTo(card.get("order").get("available_actions"));
                    assertThat(orderIn(schedule.get("orders"),ORDER).get("available_actions")).isEqualTo(card.get("order").get("available_actions"));
                    assertThat(group.get("id").asString()).isEqualTo(GROUP);
                    assertThat(group.get("available_actions").toString()).isEqualTo(offset<0 ? "[\"REASSIGN\",\"AUTO_REASSIGN\"]" : "[\"AUTO_REASSIGN\"]");
                    System.out.printf("Live deadline offset %d: order=%s alert=%s%n",offset,expected,group.get("available_actions"));
                }
                clock(DEADLINE.minusSeconds(1),0);
                when(realClock.instant()).thenReturn(REAL.plusSeconds(3600));
                var paused=read(dispatcher,"/api/orders/"+ORDER,200);
                assertThat(paused.get("clock").get("now").asString()).isEqualTo(DEADLINE.minusSeconds(1).toString());
                assertThat(paused.get("order").get("available_actions").toString()).contains("REASSIGN");
                jdbc.update("update service_order set status='IN_PROGRESS' where id=cast(? as uuid)",ORDER);
                var started=read(representative,"/api/orders/"+ORDER,200);
                assertThat(started.get("order").get("available_actions").toString()).isEqualTo("[\"COMPLETED\",\"REPORT_DELAY\"]");
                jdbc.update("update service_order set status='ARRIVED' where id=cast(? as uuid)",ORDER);
                jdbc.update("update alert_order set status='resolved',resolved_at=cast('2099-11-09T09:13:00Z' as timestamptz) where alert_id=cast(? as uuid) and order_id=cast(? as uuid)",GROUP,ORDER);
                var resolved=read(representative,"/api/orders/"+ORDER,200);
                assertThat(resolved.get("order").get("available_actions").toString()).contains("IN_PROGRESS");
                assertThat(read(dispatcher,"/api/alerts?status=open",200).get("items").get(0).get("available_actions").isEmpty()).isTrue();
                post(representative,"/api/auth/logout",representativeToken,"",null,204);
                get(representative,"/api/me",401);
                post(dispatcher,"/api/auth/logout",dispatcherToken,"",null,204);
                get(dispatcher,"/api/me",401);
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

package com.routeroad;

import com.routeroad.auth.application.AccountService;
import com.routeroad.shared.infrastructure.AppUserRepository;
import com.routeroad.simulation.infrastructure.ScenarioStateRepository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@EnabledIfEnvironmentVariable(named = "ROUTEROAD_REPOSITORY_TESTS", matches = "true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Transactional
@Rollback
@Sql("/repository-fixtures.sql")
class AuthenticationPostgresTests {
    private final String password = UUID.randomUUID().toString();
    private static final UUID FREE_COURIER = UUID.fromString("a0f8d200-0000-4000-8000-000000000002");
    private static final UUID SCENARIO = UUID.fromString("a0f8db00-0000-4000-8000-000000000001");
    @Autowired MockMvc mvc;
    @Autowired JsonMapper json;
    @Autowired PasswordEncoder passwords;
    @Autowired AppUserRepository users;
    @Autowired ScenarioStateRepository scenarios;
    @Autowired AccountService accounts;
    @Autowired JdbcTemplate jdbc;

    @BeforeAll void requireEmptyScenario() {
        assumeTrue(scenarios.count() == 0, "Existing scenario must be preserved; tests require empty local DB");
    }
    @BeforeEach void prepareRuntimeHashes() {
        jdbc.update("update app_user set password_hash = ? where login in ('repo-dispatcher', 'repo-representative')", passwords.encode(password));
    }
    record Client(MockHttpSession session, String token) {}
    private Client login(String login) throws Exception {
        var initial = mvc.perform(get("/api/me")).andExpect(status().isUnauthorized()).andReturn();
        var session = (MockHttpSession) initial.getRequest().getSession(false);
        var result = mvc.perform(post("/api/auth/login").session(session).header("Origin", "http://localhost")
                .header("X-CSRF-Token", initial.getResponse().getHeader("X-CSRF-Token")).contentType("application/json")
                .content(json.writeValueAsString(Map.of("login", login, "password", password))))
                .andExpect(status().isOk()).andReturn();
        return new Client(session, json.readTree(result.getResponse().getContentAsString()).get("csrf_token").asString());
    }
    private org.springframework.test.web.servlet.ResultActions register(Client client, String login, UUID courier, UUID scenario) throws Exception {
        return mvc.perform(post("/api/auth/register").session(client.session()).header("X-CSRF-Token", client.token())
                .header("X-Scenario-ID", scenario).contentType("application/json").content(json.writeValueAsString(
                        Map.of("login", login, "password", password, "name", "Тестовый представитель", "courier_id", courier))));
    }
    @Test void fullLifecycleUsesPostgresAndKeepsDispatcherSession() throws Exception {
        var client = login("repo-dispatcher");
        var created = register(client, "auth-test-new", FREE_COURIER, SCENARIO).andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("representative")).andReturn();
        assertThat(created.getResponse().getContentAsString()).doesNotContain(password, "password_hash", "password");
        var persisted = users.findByLogin("auth-test-new").orElseThrow();
        assertThat(passwords.matches(password, persisted.getPasswordHash())).isTrue();
        mvc.perform(get("/api/me").session(client.session())).andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("dispatcher"));
        var representative = login("auth-test-new");
        mvc.perform(patch("/api/me").session(representative.session()).header("X-CSRF-Token", representative.token())
                .contentType("application/json").content("{\"name\":\"Новое имя\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Новое имя"));
        users.flush();
        assertThat(jdbc.queryForObject("select name from app_user where login = 'auth-test-new'", String.class)).isEqualTo("Новое имя");
        assertThat(jdbc.queryForObject("select name from courier where id = ?", String.class, FREE_COURIER)).isEqualTo("Repo courier B");
        mvc.perform(post("/api/auth/logout").session(representative.session()).header("X-CSRF-Token", representative.token()))
                .andExpect(status().isNoContent());
        assertThat(representative.session().isInvalid()).isTrue();
        mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
    }
    @Test void databaseLookupsProtectRegistrationAndMissingScenario() throws Exception {
        var client = login("repo-dispatcher");
        register(client, "repo-dispatcher", FREE_COURIER, SCENARIO).andExpect(status().isConflict());
        register(client, "auth-new", UUID.fromString("a0f8d200-0000-4000-8000-000000000001"), SCENARIO).andExpect(status().isConflict());
        register(client, "auth-new", UUID.randomUUID(), SCENARIO).andExpect(status().isNotFound());
        register(client, "auth-new", FREE_COURIER, UUID.randomUUID()).andExpect(status().isConflict());
        register(login("repo-representative"), "auth-new", FREE_COURIER, SCENARIO).andExpect(status().isForbidden());
    }
    @Test void emptyScenarioIsRejectedWithoutCreatingState() throws Exception {
        var client = login("repo-dispatcher");
        scenarios.deleteAll(); scenarios.flush();
        register(client, "auth-new", FREE_COURIER, SCENARIO).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("SCENARIO_RESET"));
        assertThat(scenarios.count()).isZero();
    }
    @Test void explicitDispatcherInitializationIsIdempotentAndDoesNotResetPassword() {
        accounts.initializeDispatcher("auth-bootstrap", password, "Первый диспетчер");
        String original = users.findByLogin("auth-bootstrap").orElseThrow().getPasswordHash();
        accounts.initializeDispatcher("auth-bootstrap", UUID.randomUUID().toString(), "Другое имя");
        assertThat(users.findByLogin("auth-bootstrap").orElseThrow().getPasswordHash()).isEqualTo(original);
        assertThat(users.findByLogin("auth-bootstrap").orElseThrow().getName()).isEqualTo("Первый диспетчер");
        assertThat(passwords.matches(password, original)).isTrue();
        assertThat(users.findByLogin("auth-bootstrap").orElseThrow().getCourierId()).isNull();
    }
}

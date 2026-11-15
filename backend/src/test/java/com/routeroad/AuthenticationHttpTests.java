package com.routeroad;

import com.routeroad.auth.infrastructure.DispatcherBootstrap;
import com.routeroad.couriers.infrastructure.CourierRepository;
import com.routeroad.shared.domain.AppUserRole;
import com.routeroad.shared.infrastructure.AppUserEntity;
import com.routeroad.shared.infrastructure.AppUserRepository;
import com.routeroad.simulation.infrastructure.ScenarioStateEntity;
import com.routeroad.simulation.infrastructure.ScenarioStateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.json.JsonMapper;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("context-smoke")
@AutoConfigureMockMvc
class AuthenticationHttpTests {
    private static final UUID DISPATCHER = UUID.randomUUID();
    private static final UUID COURIER = UUID.randomUUID();
    private static final UUID FREE_COURIER = UUID.randomUUID();
    private static final UUID SCENARIO = UUID.randomUUID();
    private static final String PASSWORD = UUID.randomUUID().toString();
    private static String hash;
    @Autowired MockMvc mvc;
    @Autowired JsonMapper json;
    @Autowired PasswordEncoder passwords;
    @Autowired org.springframework.context.ApplicationContext context;
    @MockitoBean AppUserRepository users;
    @MockitoBean CourierRepository couriers;
    @MockitoBean ScenarioStateRepository scenarios;
    private final Map<String, AppUserEntity> accounts = new HashMap<>();

    @BeforeEach void setup() {
        if (hash == null) hash = passwords.encode(PASSWORD);
        accounts.clear();
        accounts.put("dispatcher", new AppUserEntity(DISPATCHER, "dispatcher", hash, "Диспетчер", AppUserRole.DISPATCHER, null));
        accounts.put("representative", new AppUserEntity(UUID.randomUUID(), "representative", hash,
                "Представитель", AppUserRole.REPRESENTATIVE, COURIER));
        when(users.findByLogin(anyString())).thenAnswer(call -> Optional.ofNullable(accounts.get(call.getArgument(0))));
        when(users.existsByLogin(anyString())).thenAnswer(call -> accounts.containsKey(call.getArgument(0)));
        when(users.existsByCourierId(any())).thenAnswer(call -> COURIER.equals(call.getArgument(0)));
        when(couriers.existsById(any())).thenAnswer(call -> COURIER.equals(call.getArgument(0)) || FREE_COURIER.equals(call.getArgument(0)));
        when(scenarios.findByScenarioId(any())).thenAnswer(call -> SCENARIO.equals(call.getArgument(0))
                ? Optional.of(mock(ScenarioStateEntity.class)) : Optional.empty());
        when(users.saveAndFlush(any())).thenAnswer(call -> {
            AppUserEntity user = call.getArgument(0); accounts.put(user.getLogin(), user); return user;
        });
    }

    record Client(MockHttpSession session, String token) {}
    private Client anonymous() throws Exception {
        var result = mvc.perform(get("/api/me")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED")).andReturn();
        return new Client((MockHttpSession) result.getRequest().getSession(false), result.getResponse().getHeader("X-CSRF-Token"));
    }
    private Client login(String login) throws Exception {
        var client = anonymous();
        var result = loginRequest(client, login, PASSWORD).andExpect(status().isOk()).andReturn();
        assertSafe(result);
        return new Client((MockHttpSession) result.getRequest().getSession(false), json.readTree(result.getResponse().getContentAsString()).get("csrf_token").asString());
    }
    private org.springframework.test.web.servlet.ResultActions loginRequest(Client client, String login, String password) throws Exception {
        return mvc.perform(post("/api/auth/login").session(client.session()).with(request -> {
                    request.setRequestedSessionId(client.session().getId());
                    request.setRequestedSessionIdValid(true);
                    request.setRequestedSessionIdFromCookie(true);
                    return request;
                }).header("Origin", "http://localhost")
                .header("X-CSRF-Token", client.token()).contentType("application/json")
                .content(json.writeValueAsString(Map.of("login", login, "password", password))));
    }
    private org.springframework.test.web.servlet.ResultActions register(Client client, String login, UUID courier, UUID scenario) throws Exception {
        return mvc.perform(post("/api/auth/register").session(client.session()).header("X-CSRF-Token", client.token())
                .header("X-Scenario-ID", scenario).contentType("application/json")
                .content(json.writeValueAsString(Map.of("login", login, "password", PASSWORD, "name", "Новый пользователь", "courier_id", courier))));
    }
    private void assertSafe(MvcResult result) throws Exception {
        assertThat(result.getResponse().getContentAsString()).doesNotContain(PASSWORD, hash, "password", "password_hash");
    }

    @Test void loginRotatesSessionAndCsrfAndReturnsOnlyPublicFields() throws Exception {
        var before = anonymous(); String oldId = before.session().getId();
        var result = loginRequest(before, "dispatcher", PASSWORD).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(DISPATCHER.toString())).andExpect(jsonPath("$.role").value("dispatcher"))
                .andExpect(jsonPath("$.courier_id").isEmpty()).andExpect(jsonPath("$.csrf_token").isNotEmpty()).andReturn();
        assertThat(before.session().getId()).isNotEqualTo(oldId);
        assertSafe(result);
        mvc.perform(patch("/api/me").session(before.session()).header("X-CSRF-Token", before.token())
                .contentType("application/json").content("{\"name\":\"Новое имя\"}")).andExpect(status().isForbidden());
        var authentication = ((org.springframework.security.core.context.SecurityContext) before.session().getAttribute("SPRING_SECURITY_CONTEXT")).getAuthentication();
        assertThat(authentication.getCredentials()).isNull();
        assertThat(((org.springframework.security.core.userdetails.User) authentication.getPrincipal()).getPassword()).isNull();
    }
    @Test void wrongPasswordAndUnknownLoginHaveIdenticalErrors() throws Exception {
        var client = anonymous();
        var wrong = loginRequest(client, "dispatcher", "wrong").andExpect(status().isUnauthorized()).andReturn();
        var unknown = loginRequest(client, "unknown", "wrong").andExpect(status().isUnauthorized()).andReturn();
        assertThat(wrong.getResponse().getContentAsString()).isEqualTo(unknown.getResponse().getContentAsString());
    }
    @Test void meRequiresSessionAndHealthIsPublic() throws Exception {
        anonymous();
        mvc.perform(get("/api/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
    }
    @Test void meUsesSessionAndProfileRenamesOnlyAccount() throws Exception {
        var client = login("representative");
        mvc.perform(get("/api/me").session(client.session())).andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("representative")).andExpect(jsonPath("$.courier_id").value(COURIER.toString()));
        var result = mvc.perform(patch("/api/me").session(client.session()).header("X-CSRF-Token", client.token())
                .contentType("application/json").content("{\"name\":\"Новое имя\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Новое имя")).andReturn();
        assertSafe(result); verifyNoInteractions(couriers);
    }
    @Test void profileRejectsUnknownAndProtectedFields() throws Exception {
        var client = login("dispatcher");
        for (String field : new String[]{"login", "password_hash", "role", "courier_id", "id"}) {
            mvc.perform(patch("/api/me").session(client.session()).header("X-CSRF-Token", client.token())
                    .contentType("application/json").content(json.writeValueAsString(Map.of("name", "Новое имя", field, "forbidden"))))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
        }
        assertThat(accounts.get("dispatcher").getName()).isEqualTo("Диспетчер");
    }
    @Test void profileRejectsInvalidNamesAndMalformedJson() throws Exception {
        var client = login("dispatcher");
        for (String name : new String[]{"a", " ", "a".repeat(65), " a "}) {
            mvc.perform(patch("/api/me").session(client.session()).header("X-CSRF-Token", client.token())
                    .contentType("application/json").content(json.writeValueAsString(Map.of("name", name)))).andExpect(status().isBadRequest());
        }
        mvc.perform(patch("/api/me").session(client.session()).header("X-CSRF-Token", client.token())
                .contentType("application/json").content("{")).andExpect(status().isBadRequest());
    }
    @Test void logoutInvalidatesSession() throws Exception {
        var client = login("dispatcher");
        mvc.perform(post("/api/auth/logout").session(client.session()).header("X-CSRF-Token", client.token()))
                .andExpect(status().isNoContent()).andExpect(content().string(""));
        assertThat(client.session().isInvalid()).isTrue();
        mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
    }
    @Test void representativeCannotRegister() throws Exception {
        register(login("representative"), "new", FREE_COURIER, SCENARIO).andExpect(status().isForbidden());
        verify(users, never()).saveAndFlush(any());
    }
    @Test void dispatcherRegistersRepresentativeWithoutSwitchingSession() throws Exception {
        var client = login("dispatcher");
        var result = register(client, "new", FREE_COURIER, SCENARIO).andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("representative")).andExpect(jsonPath("$.login").value("new")).andReturn();
        assertSafe(result);
        assertThat(passwords.matches(PASSWORD, accounts.get("new").getPasswordHash())).isTrue();
        assertThat(accounts.get("new").getPasswordHash()).startsWith("{pbkdf2@SpringSecurity_v5_8}").isNotEqualTo(PASSWORD);
        mvc.perform(get("/api/me").session(client.session())).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(DISPATCHER.toString())).andExpect(jsonPath("$.role").value("dispatcher"));
    }
    @Test void duplicateLoginAndCourierConflict() throws Exception {
        var client = login("dispatcher");
        register(client, "dispatcher", FREE_COURIER, SCENARIO).andExpect(status().isConflict());
        register(client, "new", COURIER, SCENARIO).andExpect(status().isConflict());
    }
    @Test void missingCourierReturnsNotFound() throws Exception {
        register(login("dispatcher"), "new", UUID.randomUUID(), SCENARIO).andExpect(status().isNotFound());
    }
    @Test void staleOrAbsentScenarioDoesNotCreateState() throws Exception {
        var client = login("dispatcher");
        register(client, "new", FREE_COURIER, UUID.randomUUID()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("SCENARIO_RESET"));
        when(scenarios.findByScenarioId(SCENARIO)).thenReturn(Optional.empty());
        register(client, "new", FREE_COURIER, SCENARIO).andExpect(status().isConflict());
        verify(scenarios, never()).save(any()); verify(users, never()).saveAndFlush(any());
    }
    @Test void scenarioHeaderIsRequiredAndMustBeUuid() throws Exception {
        var client = login("dispatcher");
        for (String header : new String[]{"", "not-a-uuid"}) {
            var request = post("/api/auth/register").session(client.session()).header("X-CSRF-Token", client.token())
                    .contentType("application/json").content(json.writeValueAsString(Map.of("login", "new", "password", PASSWORD,
                            "name", "Новое имя", "courier_id", FREE_COURIER)));
            if (!header.isEmpty()) request.header("X-Scenario-ID", header);
            mvc.perform(request).andExpect(status().isBadRequest());
        }
    }
    @Test void allModificationsRequireCsrf() throws Exception {
        var client = login("dispatcher");
        for (String token : new String[]{"", "invalid"}) {
            for (String path : new String[]{"/api/auth/login", "/api/auth/logout", "/api/auth/register"}) {
                var request = post(path).session(client.session()).header("Origin", "http://localhost");
                if (!token.isEmpty()) request.header("X-CSRF-Token", token);
                mvc.perform(request).andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("INVALID_CSRF"));
            }
            var patch = patch("/api/me").session(client.session()).contentType("application/json").content("{\"name\":\"New name\"}");
            if (!token.isEmpty()) patch.header("X-CSRF-Token", token);
            mvc.perform(patch).andExpect(status().isForbidden());
        }
    }
    @Test void loginRejectsMissingForeignAndMalformedOrigin() throws Exception {
        var client = anonymous();
        for (String origin : new String[]{"", "https://evil.example", "null", "http://localhost/path", "http://localhost@evil.example"}) {
            var request = post("/api/auth/login").session(client.session()).header("X-CSRF-Token", client.token());
            if (!origin.isEmpty()) request.header("Origin", origin);
            mvc.perform(request).andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("INVALID_ORIGIN"));
        }
    }
    @Test void loginChecksOriginForDecodedServletPathToo() throws Exception {
        var client = anonymous();
        mvc.perform(post("/api/auth/login").servletPath("/api/auth/login").with(request -> {
                    request.setRequestURI("/api/auth/%6cogin");
                    return request;
                }).session(client.session()).header("X-CSRF-Token", client.token())
                .header("Origin", "https://foreign.example"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("INVALID_ORIGIN"));
    }
    @Test void registrationRejectsClientRoleAndAnonymousCaller() throws Exception {
        var client = login("dispatcher");
        mvc.perform(post("/api/auth/register").session(client.session()).header("X-CSRF-Token", client.token())
                .header("X-Scenario-ID", SCENARIO).contentType("application/json")
                .content(json.writeValueAsString(Map.of("login", "new", "password", PASSWORD, "name", "New name",
                        "courier_id", FREE_COURIER, "role", "dispatcher"))))
                .andExpect(status().isBadRequest());
        register(anonymous(), "new", FREE_COURIER, SCENARIO).andExpect(status().isUnauthorized());
        verify(users, never()).saveAndFlush(any());
    }
    @Test void loginRejectsInvalidBodyWithoutLeakingSubmittedPassword() throws Exception {
        var client = anonymous();
        var result = mvc.perform(post("/api/auth/login").session(client.session()).with(request -> {
                    request.setRequestedSessionId(client.session().getId());
                    request.setRequestedSessionIdValid(true);
                    request.setRequestedSessionIdFromCookie(true);
                    return request;
                }).header("Origin", "http://localhost")
                .header("X-CSRF-Token", client.token()).contentType("application/json")
                .content(json.writeValueAsString(Map.of("password", PASSWORD))))
                .andExpect(status().isBadRequest()).andReturn();
        assertSafe(result);
    }
    @Test void bootstrapIsOffByDefaultAndUnexpectedErrorsAreSafe() throws Exception {
        assertThat(context.getBeansOfType(DispatcherBootstrap.class)).isEmpty();
        var client = login("dispatcher");
        when(users.findByLogin("dispatcher")).thenThrow(new IllegalStateException("secret database text"));
        mvc.perform(get("/api/me").session(client.session())).andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("INTERNAL_ERROR"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret database text"))));
    }
}

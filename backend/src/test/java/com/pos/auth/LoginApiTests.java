package com.pos.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pos.AbstractIntegrationTest;
import com.pos.auth.dto.LoginRequest;
import com.pos.auth.service.AuthService;
import com.pos.users.domain.User;
import com.pos.users.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LoginApiTests extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    private ObjectMapper objectMapper;

    private User activeUser;
    private User inactiveUser;

    @BeforeEach
    void setUp() {
        // Do not delete audit logs or users
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushDb();

        String suffix = String.valueOf(System.nanoTime());
        activeUser = new User("activeuser" + suffix, passwordEncoder.encode("Password123456!"), "Active", "User");
        activeUser.requirePasswordChange();
        userRepository.save(activeUser);

        inactiveUser = new User("inactiveuser" + suffix, passwordEncoder.encode("Password123456!"), "Inactive", "User");
        inactiveUser.setActive(false);
        userRepository.save(inactiveUser);
    }

    @AfterEach
    void tearDown() {
        // Do not delete audit logs or users
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushDb();
    }

    @Test
    void aValidLoginReturnsTokenAndPasswordChangeRequiredFlag() throws Exception {
        LoginRequest req = new LoginRequest(activeUser.getUsername(), "Password123456!");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.passwordChangeRequired").value(true));

        List<Map<String, Object>> logs = jdbcTemplate.queryForList("SELECT * FROM audit_logs WHERE actor_user_id = ?", activeUser.getId());
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).get("action")).isEqualTo(AuthService.LOGIN_SUCCESS);
        assertThat(logs.get(0).get("actor_user_id")).isEqualTo(activeUser.getId());
    }

    @Test
    void anInvalidPasswordFailsAndIsAudited() throws Exception {
        LoginRequest req = new LoginRequest(activeUser.getUsername(), "WrongPassword123!");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTHENTICATION_REQUIRED"));

        List<Map<String, Object>> logs = jdbcTemplate.queryForList("SELECT * FROM audit_logs WHERE actor_user_id = ?", activeUser.getId());
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).get("action")).isEqualTo(AuthService.LOGIN_FAILED);
    }

    @Test
    void anUnknownUserFailsWithoutAuditingSinceThereIsNoActor() throws Exception {
        Long initialCount = jdbcTemplate.queryForObject("SELECT count(*) FROM audit_logs", Long.class);
        
        LoginRequest req = new LoginRequest("unknownuser" + System.nanoTime(), "Password123456!");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());

        Long newCount = jdbcTemplate.queryForObject("SELECT count(*) FROM audit_logs", Long.class);
        assertThat(newCount).isEqualTo(initialCount);
    }

    @Test
    void anInactiveUserFails() throws Exception {
        LoginRequest req = new LoginRequest(inactiveUser.getUsername(), "Password123456!");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rateLimitingTriggersAfterFiveAttempts() throws Exception {
        LoginRequest req = new LoginRequest(activeUser.getUsername(), "WrongPassword123!");
        String json = objectMapper.writeValueAsString(req);

        // 5 failures
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isUnauthorized());
        }

        // 6th fails with 429
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error.code").value("RATE_LIMITED"));
    }
}

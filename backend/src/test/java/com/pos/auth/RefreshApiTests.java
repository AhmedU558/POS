package com.pos.auth;

import com.pos.AbstractIntegrationTest;
import com.pos.auth.dto.LoginRequest;
import com.pos.auth.dto.RefreshTokenRequest;
import com.pos.auth.service.RefreshTokenService;
import com.pos.users.domain.User;
import com.pos.users.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pos.auth.dto.LoginResponse;
import com.pos.common.response.ApiResponse;
import com.fasterxml.jackson.core.type.TypeReference;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class RefreshApiTests extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    @Test
    void testSuccessfulRefresh() throws Exception {
        String username = "refuser" + System.nanoTime();
        User user = new User(username, passwordEncoder.encode("password"), "F", "L");
        userRepository.saveAndFlush(user);

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LoginRequest(username, "password"))))
                .andExpect(status().isOk())
                .andReturn();

        ApiResponse<LoginResponse> response = objectMapper.readValue(
                loginResult.getResponse().getContentAsString(),
                new TypeReference<>() {}
        );
        String refreshToken = response.data().refreshToken();

        mockMvc.perform(post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new RefreshTokenRequest(refreshToken))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").exists())
                .andExpect(jsonPath("$.data.refreshToken").exists());
    }

    @Test
    void testInvalidRefreshToken() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new RefreshTokenRequest("invalid.token.here"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testRevokedRefreshFamily() throws Exception {
        String username = "revokedf" + System.nanoTime();
        User user = new User(username, passwordEncoder.encode("password"), "F", "L");
        userRepository.saveAndFlush(user);

        String rt = refreshTokenService.createRefreshToken(username);
        refreshTokenService.revokeFamily(refreshTokenService.getFamilyIdFromToken(rt));

        mockMvc.perform(post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new RefreshTokenRequest(rt))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testRefreshReplay() throws Exception {
        String username = "replay" + System.nanoTime();
        User user = new User(username, passwordEncoder.encode("password"), "F", "L");
        userRepository.saveAndFlush(user);

        String rt1 = refreshTokenService.createRefreshToken(username);
        
        MvcResult res = mockMvc.perform(post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new RefreshTokenRequest(rt1))))
                .andExpect(status().isOk())
                .andReturn();
        ApiResponse<LoginResponse> response = objectMapper.readValue(
                res.getResponse().getContentAsString(),
                new TypeReference<>() {}
        );
        String rt2 = response.data().refreshToken();

        // Replay rt1
        mockMvc.perform(post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new RefreshTokenRequest(rt1))))
                .andExpect(status().isUnauthorized());

        // Confirm rt2 is now also revoked
        mockMvc.perform(post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new RefreshTokenRequest(rt2))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testConcurrentRefresh() throws Exception {
        String username = "concurrent" + System.nanoTime();
        User user = new User(username, passwordEncoder.encode("password"), "F", "L");
        userRepository.saveAndFlush(user);

        String rt = refreshTokenService.createRefreshToken(username);

        int threadCount = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger();

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    latch.await();
                    MvcResult res = mockMvc.perform(post("/api/v1/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new RefreshTokenRequest(rt))))
                            .andReturn();
                    if (res.getResponse().getStatus() == 200) {
                        successCount.incrementAndGet();
                    }
                } catch (Exception e) {} finally {
                    done.countDown();
                }
            });
        }
        latch.countDown();
        done.await();

        // Only ONE thread should succeed
        org.junit.jupiter.api.Assertions.assertEquals(1, successCount.get());
    }

    @Test
    void testLogout() throws Exception {
        String username = "logout" + System.nanoTime();
        User user = new User(username, passwordEncoder.encode("password"), "F", "L");
        userRepository.saveAndFlush(user);

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LoginRequest(username, "password"))))
                .andExpect(status().isOk())
                .andReturn();
        ApiResponse<LoginResponse> loginResponse = objectMapper.readValue(
                loginResult.getResponse().getContentAsString(),
                new TypeReference<>() {}
        );

        mockMvc.perform(post("/api/v1/auth/logout")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + loginResponse.data().accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new RefreshTokenRequest(loginResponse.data().refreshToken()))))
                .andExpect(status().isNoContent());

        // Now refresh should fail
        mockMvc.perform(post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new RefreshTokenRequest(loginResponse.data().refreshToken()))))
                .andExpect(status().isUnauthorized());
    }
}

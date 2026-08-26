package com.pos.auth;

import com.pos.AbstractIntegrationTest;
import com.pos.auth.security.JwtTokenProvider;
import com.pos.users.domain.User;
import com.pos.users.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class MeApiTests extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private UserRepository userRepository;

    @Test
    void returnsCurrentUserProfileAndOmitsPasswordHash() throws Exception {
        String username = "meuser" + System.nanoTime();
        User user = new User(username, "hash", "First", "Last");
        userRepository.saveAndFlush(user);

        String token = jwtTokenProvider.generateToken(username);

        mockMvc.perform(get("/api/v1/auth/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value(username))
                .andExpect(jsonPath("$.data.firstName").value("First"))
                .andExpect(jsonPath("$.data.lastName").value("Last"))
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist());
    }

    @Test
    void returnsOkIfPasswordChangeRequired() throws Exception {
        String username = "mepending" + System.nanoTime();
        User user = new User(username, "hash", "F", "L");
        user.requirePasswordChange();
        userRepository.saveAndFlush(user);

        String token = jwtTokenProvider.generateToken(username);

        mockMvc.perform(get("/api/v1/auth/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.passwordChangeRequired").value(true));
    }

    @Test
    void rejectsDeactivatedUser() throws Exception {
        String username = "meinactive" + System.nanoTime();
        User user = new User(username, "hash", "F", "L");
        userRepository.saveAndFlush(user);
        String token = jwtTokenProvider.generateToken(username);

        user.setActive(false);
        userRepository.saveAndFlush(user);

        mockMvc.perform(get("/api/v1/auth/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsMissingToken() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsRevokedToken() throws Exception {
        String username = "merevoked" + System.nanoTime();
        User user = new User(username, "hash", "F", "L");
        userRepository.saveAndFlush(user);
        String token = jwtTokenProvider.generateToken(username);

        Thread.sleep(1000); // ensure iat is before change

        user.changePassword("newhash");
        userRepository.saveAndFlush(user);

        mockMvc.perform(get("/api/v1/auth/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testForgedIdentity() throws Exception {
        String token = jwtTokenProvider.generateToken("nonexistentuser");
        mockMvc.perform(get("/api/v1/auth/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }
}
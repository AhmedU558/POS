package com.pos.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pos.AbstractIntegrationTest;
import com.pos.auth.dto.ChangePasswordRequest;
import com.pos.users.domain.User;
import com.pos.users.repository.UserRepository;
import com.pos.auth.security.JwtTokenProvider;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.SecretKey;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class JwtAuthenticationIntegrationTests extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private JwtTokenProvider jwtTokenProvider;
    @Autowired
    private ObjectMapper objectMapper;

    @Value("${jwt.secret}")
    private String jwtSecret;

    private User user;
    private String validToken;

    @BeforeEach
    void setUp() {
        String uniqueUser = "jwtuser" + System.nanoTime();
        user = new User(uniqueUser, passwordEncoder.encode("Password123456!"), "JWT", "User");
        userRepository.save(user);
        validToken = jwtTokenProvider.generateToken(user.getUsername());
    }

    @AfterEach
    void tearDown() {
        // Do not delete users, as audit logs tie them to the DB permanently.
    }

    // Assume /api/v1/auth/me exists or some protected endpoint exists. 
    // Since /api/v1/auth/change-password requires authentication and has no other side effects if it fails validation, we can use it,
    // or just rely on a 401 response when hitting a non-existent protected route, since the filter fires before 404.
    // Actually, anyRequest().authenticated() will return 401 for /api/v1/some-random-route if token is invalid,
    // and 404 if token is valid. This is a robust way to test just the security filter!

    @Test
    void validTokenIsAccepted() throws Exception {
        mockMvc.perform(get("/api/v1/non-existent")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken))
                .andExpect(status().isNotFound()); // Means it passed authentication
    }

    @Test
    void missingTokenIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/non-existent"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidSignatureIsRejected() throws Exception {
        String tampered = validToken + "tamper";
        mockMvc.perform(get("/api/v1/non-existent")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tampered))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void expiredTokenIsRejected() throws Exception {
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        String expired = Jwts.builder()
                .subject(user.getUsername())
                .issuedAt(new Date(System.currentTimeMillis() - 100000))
                .expiration(new Date(System.currentTimeMillis() - 10000))
                .signWith(key)
                .compact();

        mockMvc.perform(get("/api/v1/non-existent")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + expired))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void passwordChangeInstantlyInvalidatesOldToken() throws Exception {
        // 1. Token issued before password change
        String oldToken = jwtTokenProvider.generateToken(user.getUsername());
        
        // Wait 1 second to ensure iat is strictly before credentialsChangedAt
        Thread.sleep(1000);

        // 2. Change password
        ChangePasswordRequest req = new ChangePasswordRequest("Password123456!", "NewPassword123!");
        mockMvc.perform(post("/api/v1/auth/change-password")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + oldToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNoContent());

        // 3. Old token should now be rejected
        mockMvc.perform(get("/api/v1/non-existent")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + oldToken))
                .andExpect(status().isUnauthorized());

        // 4. New token works
        String newToken = jwtTokenProvider.generateToken(user.getUsername());
        mockMvc.perform(get("/api/v1/non-existent")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + newToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void deactivationInstantlyInvalidatesToken() throws Exception {
        user.setActive(false);
        userRepository.save(user);

        mockMvc.perform(get("/api/v1/non-existent")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken))
                .andExpect(status().isUnauthorized());
    }
}

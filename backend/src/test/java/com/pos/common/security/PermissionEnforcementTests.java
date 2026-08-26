package com.pos.common.security;

import com.pos.AbstractIntegrationTest;
import com.pos.users.domain.Role;
import com.pos.users.domain.User;
import com.pos.users.domain.Permission;
import com.pos.users.domain.PermissionCode;
import com.pos.users.repository.RoleRepository;
import com.pos.users.repository.UserRepository;
import com.pos.users.repository.PermissionRepository;
import com.pos.auth.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
@Import(PermissionEnforcementTests.TestControllerConfig.class)
class PermissionEnforcementTests extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PermissionRepository permissionRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtTokenProvider jwtTokenProvider;

    private User testUser;
    private Role testRole;
    private Permission readPermission;
    private Permission writePermission;

    @BeforeEach
    void setUp() {
        readPermission = permissionRepository.findByCode(PermissionCode.USER_READ).orElseThrow();
        writePermission = permissionRepository.findByCode(PermissionCode.USER_WRITE).orElseThrow();

        testRole = new Role("Test Role " + UUID.randomUUID(), "Test Role");
        testRole.grant(readPermission);
        roleRepository.saveAndFlush(testRole);

        testUser = new User("permuser", passwordEncoder.encode("password"), "Perm", "User");
        testUser.assignRole(testRole);
        userRepository.saveAndFlush(testUser);
    }

    private String getToken(User user) {
        return "Bearer " + jwtTokenProvider.generateToken(user.getUsername());
    }

    @Test
    void authenticatedUserWithRequiredPermission_Allowed() throws Exception {
        mockMvc.perform(get("/api/v1/test/read-only").header(HttpHeaders.AUTHORIZATION, getToken(testUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Success"));
    }

    @Test
    void authenticatedUserWithoutPermission_Denied() throws Exception {
        mockMvc.perform(get("/api/v1/test/write-only").header(HttpHeaders.AUTHORIZATION, getToken(testUser)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ACCESS_DENIED"));
    }

    @Test
    void unauthenticatedRequest_AuthenticationFailure() throws Exception {
        mockMvc.perform(get("/api/v1/test/read-only"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void multipleRolesCombinePermissionsCorrectly() throws Exception {
        Role anotherRole = new Role("Another Role " + UUID.randomUUID(), "Another Role");
        anotherRole.grant(writePermission);
        roleRepository.saveAndFlush(anotherRole);

        testUser.assignRole(anotherRole);
        userRepository.saveAndFlush(testUser);

        mockMvc.perform(get("/api/v1/test/read-only").header(HttpHeaders.AUTHORIZATION, getToken(testUser)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/test/write-only").header(HttpHeaders.AUTHORIZATION, getToken(testUser)))
                .andExpect(status().isOk());
    }

    @Test
    void superAdministratorPermissions() throws Exception {
        User admin = new User("superadmin" + UUID.randomUUID(), passwordEncoder.encode("password"), "Super", "Admin");
        Role superAdminRole = roleRepository.findByName("Super Administrator").orElseThrow();
        admin.assignRole(superAdminRole);
        userRepository.saveAndFlush(admin);

        mockMvc.perform(get("/api/v1/test/read-only").header(HttpHeaders.AUTHORIZATION, getToken(admin)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/test/write-only").header(HttpHeaders.AUTHORIZATION, getToken(admin)))
                .andExpect(status().isOk());
    }

    @Test
    void inactiveUser_Denied() throws Exception {
        testUser.setActive(false);
        userRepository.saveAndFlush(testUser);

        mockMvc.perform(get("/api/v1/test/read-only").header(HttpHeaders.AUTHORIZATION, getToken(testUser)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void passwordRotationRequiredUser_Denied() throws Exception {
        testUser.requirePasswordChange();
        userRepository.saveAndFlush(testUser);

        mockMvc.perform(get("/api/v1/test/read-only").header(HttpHeaders.AUTHORIZATION, getToken(testUser)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("PASSWORD_CHANGE_REQUIRED"));
    }

    @Test
    void permissionRemovedAfterAuthentication_Denied() throws Exception {
        String token = getToken(testUser);

        testRole.revoke(readPermission);
        roleRepository.saveAndFlush(testRole);
        
        mockMvc.perform(get("/api/v1/test/read-only").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ACCESS_DENIED"));
    }

    @Test
    void forgedClientRoleOrPermission_Ignored() throws Exception {
        mockMvc.perform(get("/api/v1/test/write-only").header(HttpHeaders.AUTHORIZATION, getToken(testUser)))
                .andExpect(status().isForbidden());
    }

    @Test
    void noSensitiveDataLeakage() throws Exception {
        String response = mockMvc.perform(get("/api/v1/test/write-only").header(HttpHeaders.AUTHORIZATION, getToken(testUser)))
                .andExpect(status().isForbidden())
                .andReturn().getResponse().getContentAsString();

        assertThat(response).doesNotContain("Exception").doesNotContain("stackTrace");
    }

    @TestConfiguration
    static class TestControllerConfig {

        @RestController
        @RequestMapping("/api/v1/test")
        static class PermissionTestController {

            @PreAuthorize("hasAuthority('" + PermissionCode.USER_READ + "')")
            @GetMapping("/read-only")
            public String readOnly() {
                return "{\"message\":\"Success\"}";
            }

            @PreAuthorize("hasAuthority('" + PermissionCode.USER_WRITE + "')")
            @GetMapping("/write-only")
            public String writeOnly() {
                return "{\"message\":\"Success\"}";
            }
        }
    }
}

package com.pos.users;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pos.AbstractIntegrationTest;
import com.pos.auth.security.JwtTokenProvider;
import com.pos.users.domain.Permission;
import com.pos.users.domain.Role;
import com.pos.users.domain.User;
import com.pos.users.dto.RoleCreateRequest;
import com.pos.users.repository.PermissionRepository;
import com.pos.users.repository.RoleRepository;
import com.pos.users.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class RoleApiIntegrationTests extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PermissionRepository permissionRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    private User testAdmin;

    @BeforeEach
    void setUp() {
        if (userRepository.findByUsername("admin").isEmpty()) {
            testAdmin = new User("admin", passwordEncoder.encode("Admin123!"), "Admin", "User");
            testAdmin.setEmail("admin@test.com");
            Role superAdmin = roleRepository.findByName("Super Administrator").orElseThrow();
            testAdmin.assignRole(superAdmin);
            testAdmin = userRepository.saveAndFlush(testAdmin);
        } else {
            testAdmin = userRepository.findByUsername("admin").get();
        }
    }

    private String authenticate(String username) {
        return "Bearer " + jwtTokenProvider.generateToken(username);
    }

    @Test
    void createRole_withRoleWrite_success() throws Exception {
        String token = authenticate("admin");

        Permission readPerm = permissionRepository.findByCode("USER_READ").orElseThrow();
        RoleCreateRequest req = new RoleCreateRequest("Test Role " + UUID.randomUUID(), "Desc", Set.of(readPerm.getId()));

        mockMvc.perform(post("/api/v1/roles")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value(req.name()))
                .andExpect(jsonPath("$.data.permissions[0]").value("USER_READ"));

        Role role = roleRepository.findByName(req.name()).orElseThrow();
        assertThat(role.permissionCodes()).containsExactly("USER_READ");
    }

    @Test
    void createRole_withoutRoleWrite_forbidden() throws Exception {
        User user = new User("user" + UUID.randomUUID(), passwordEncoder.encode("Pass123!"), "Test", "User");
        user.setEmail("user" + UUID.randomUUID() + "@test.com");
        user = userRepository.saveAndFlush(user);

        String token = authenticate(user.getUsername());

        Permission readPerm = permissionRepository.findByCode("USER_READ").orElseThrow();
        RoleCreateRequest req = new RoleCreateRequest("Another Role " + UUID.randomUUID(), "Desc", Set.of(readPerm.getId()));

        mockMvc.perform(post("/api/v1/roles")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }
}

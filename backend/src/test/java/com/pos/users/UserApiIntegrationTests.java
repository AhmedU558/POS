package com.pos.users;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pos.AbstractIntegrationTest;
import com.pos.audit.repository.AuditLogRepository;
import com.pos.auth.security.JwtTokenProvider;
import com.pos.users.domain.Permission;
import com.pos.users.domain.Role;
import com.pos.users.domain.User;
import com.pos.users.dto.UserCreateRequest;
import com.pos.users.dto.UserStatusRequest;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class UserApiIntegrationTests extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PermissionRepository permissionRepository;
    
    @Autowired
    private com.pos.organization.repository.StoreRepository storeRepository;
    
    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

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
    void createUser_success() throws Exception {
        String token = authenticate("admin");
        Role cashier = roleRepository.findByName("Cashier").orElseThrow();

        String unique = UUID.randomUUID().toString().substring(0, 8);
        UserCreateRequest req = new UserCreateRequest("cashier" + unique, "Pass123!", "cashier" + unique + "@test.com", "John", "Doe", Set.of(cashier.getId()), Set.of());

        mockMvc.perform(post("/api/v1/users")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andDo(org.springframework.test.web.servlet.result.MockMvcResultHandlers.print())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.username").value("cashier" + unique))
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.data.passwordChangeRequired").value(true));

        User user = userRepository.findByUsername("cashier" + unique).orElseThrow();
        assertThat(user.getRoles()).contains(cashier);
        
        var audits = auditLogRepository.findByEntityTypeAndEntityIdOrderByCreatedAtDesc("User", user.getId());
        assertThat(audits).anyMatch(a -> a.getAction().equals("USER_CREATED"));
    }

    @Test
    void createUser_privilegeEscalation_cannotAssignSuperAdminIfNotSuperAdmin() throws Exception {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        User writer = new User("writer" + unique, passwordEncoder.encode("Pass123!"), "Writer", "User");
        writer.setEmail("writer" + unique + "@test.com");
        writer.requirePasswordChange(); // Need it, but we can set it via DB directly if needed.
        // Actually, we need them to be able to authenticate.
        
        Role customRole = new Role("Writer Role " + unique, "desc");
        customRole.grant(permissionRepository.findByCode("USER_WRITE").orElseThrow());
        roleRepository.saveAndFlush(customRole);
        writer.assignRole(customRole);
        
        // We must also change password so they can use token? Wait, JWT filter might reject them if passwordChangeRequired!
        // The token is generated, but `isPasswordChangeRequired` might block access. Let's clear it.
        writer.changePassword(passwordEncoder.encode("Pass123!"));
        userRepository.saveAndFlush(writer);

        String writerToken = authenticate(writer.getUsername());

        Role superAdmin = roleRepository.findByName("Super Administrator").orElseThrow();
        UserCreateRequest req = new UserCreateRequest("hacker" + unique, "Pass123!", "hacker" + unique + "@test.com", "Hack", "Er", Set.of(superAdmin.getId()), Set.of());

        mockMvc.perform(post("/api/v1/users")
                        .header("Authorization", writerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    void createUser_cannotAssignStoreWithoutAccess() throws Exception {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        User writer = new User("writer" + unique, passwordEncoder.encode("Pass123!"), "Writer", "User");
        writer.setEmail("writer" + unique + "@test.com");
        Role userAdmin = roleRepository.findByName("Super Administrator").orElseThrow();
        writer.assignRole(userAdmin);
        writer = userRepository.saveAndFlush(writer);

        // create a store that the writer does not have access to
        com.pos.organization.domain.Store alienStore = new com.pos.organization.domain.Store("ALN", "Store " + unique, "USD", "UTC");
        alienStore = storeRepository.saveAndFlush(alienStore);

        String token = authenticate(writer.getUsername());
        Role cashier = roleRepository.findByName("Cashier").orElseThrow();

        UserCreateRequest req = new UserCreateRequest("hacker" + unique, "Pass123!", "hacker" + unique + "@test.com", "Hack", "Er", Set.of(cashier.getId()), Set.of(alienStore.getId()));

        mockMvc.perform(post("/api/v1/users")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    void createUser_duplicateIdentity_returnsConflict() throws Exception {
        String token = authenticate("admin");
        Role cashier = roleRepository.findByName("Cashier").orElseThrow();

        UserCreateRequest req = new UserCreateRequest("admin", "Pass123!", "admin@test.com", "Admin", "User", Set.of(cashier.getId()), Set.of());

        mockMvc.perform(post("/api/v1/users")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict());
    }
    
    @Test
    void getUser_omitsPasswordHash() throws Exception {
        String token = authenticate("admin");
        
        mockMvc.perform(get("/api/v1/users/" + testAdmin.getId())
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("admin"))
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist());
    }

    @Test
    void updateStatus_deactivate_enforcedImmediately() throws Exception {
        String adminToken = authenticate("admin");
        
        String unique = UUID.randomUUID().toString().substring(0, 8);
        User target = new User("target" + unique, passwordEncoder.encode("Pass123!"), "Target", "User");
        target.setEmail("target" + unique + "@test.com");
        target.changePassword(passwordEncoder.encode("Pass123!"));
        Role cashier = roleRepository.findByName("Cashier").orElseThrow();
        target.assignRole(cashier);
        target = userRepository.saveAndFlush(target);
        
        String targetToken = authenticate(target.getUsername());
        
        UserStatusRequest statusReq = new UserStatusRequest(false);
        mockMvc.perform(patch("/api/v1/users/" + target.getId() + "/status")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(statusReq)))
                .andExpect(status().isOk());
                
        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", targetToken))
                .andExpect(status().isUnauthorized());
                
        var audits = auditLogRepository.findByEntityTypeAndEntityIdOrderByCreatedAtDesc("User", target.getId());
        assertThat(audits).anyMatch(a -> a.getAction().equals("USER_DEACTIVATED"));
    }
    
    @Test
    void staleJwt_cannotAccessNewlyGrantedPermissions() throws Exception {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        User target = new User("stale" + unique, passwordEncoder.encode("Pass123!"), "Stale", "User");
        target.setEmail("stale" + unique + "@test.com");
        target.changePassword(passwordEncoder.encode("Pass123!"));
        target = userRepository.saveAndFlush(target);
        
        String targetToken = authenticate(target.getUsername());
        
        mockMvc.perform(get("/api/v1/users")
                        .header("Authorization", targetToken))
                .andExpect(status().isForbidden());
                
        Role readRole = new Role("Reader " + unique, "Reader");
        readRole.grant(permissionRepository.findByCode("USER_READ").orElseThrow());
        roleRepository.saveAndFlush(readRole);
        
        target.assignRole(readRole);
        userRepository.saveAndFlush(target);
        
        mockMvc.perform(get("/api/v1/users")
                        .header("Authorization", targetToken))
                .andExpect(status().isOk());
    }
}

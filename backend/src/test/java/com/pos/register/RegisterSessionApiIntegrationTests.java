package com.pos.register;

import com.pos.AbstractIntegrationTest;
import com.pos.auth.security.JwtTokenProvider;
import com.pos.organization.domain.Register;
import com.pos.organization.domain.Store;
import com.pos.organization.domain.Terminal;
import com.pos.organization.repository.RegisterRepository;
import com.pos.organization.repository.StoreRepository;
import com.pos.organization.repository.TerminalRepository;
import com.pos.users.domain.Role;
import com.pos.users.domain.RoleName;
import com.pos.users.domain.User;
import com.pos.users.repository.RoleRepository;
import com.pos.users.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class RegisterSessionApiIntegrationTests extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private StoreRepository storeRepository;
    @Autowired private TerminalRepository terminalRepository;
    @Autowired private RegisterRepository registerRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private EntityManager entityManager;

    private String cashierToken;
    private String accountantToken;
    private Register register;

    @BeforeEach
    void setUp() {
        Store store = storeRepository.save(new Store("REG-" + idSuffix(), "Reg Store", "USD", "UTC"));
        Terminal terminal = terminalRepository.save(new Terminal(store, "T1", "Terminal 1", "ACTIVE"));
        register = registerRepository.save(new Register(store, terminal, "R1", "Register 1", "ACTIVE"));
        User cashier = user("reg.cash", RoleName.CASHIER);
        cashier.assignStore(store);
        cashier = userRepository.save(cashier);
        cashierToken = "Bearer " + jwtTokenProvider.generateToken(cashier.getUsername());
        accountantToken = bearer("reg.acct", RoleName.ACCOUNTANT);
        entityManager.flush();
    }

    @Test
    void openAndGetSession() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/registers/" + register.getId() + "/sessions/open")
                        .header("Authorization", cashierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"openingCash\": 150.0000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("OPEN"))
                .andExpect(jsonPath("$.data.openingCash").value(150.0000))
                .andExpect(jsonPath("$.data.registerId").value(register.getId().toString()))
                .andReturn();
        String sessionId = created.getResponse().getContentAsString().split("\"id\":\"")[1].split("\"")[0];

        mockMvc.perform(get("/api/v1/register-sessions/" + sessionId).header("Authorization", cashierToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(sessionId))
                .andExpect(jsonPath("$.data.openingCash").value(150.0000));

        mockMvc.perform(post("/api/v1/registers/" + register.getId() + "/sessions/open")
                        .header("Authorization", cashierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"openingCash\": 10.0000}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("BUSINESS_RULE_VIOLATION"));

        entityManager.flush();
        Map<String, Object> audit = jdbcTemplate.queryForMap(
                "SELECT action, entity_type FROM audit_logs WHERE action = 'REGISTER_SESSION_OPENED'");
        assertThat(audit.get("entity_type")).isEqualTo("RegisterSession");
    }

    @Test
    void accountantCannotOpen() throws Exception {
        mockMvc.perform(post("/api/v1/registers/" + register.getId() + "/sessions/open")
                        .header("Authorization", accountantToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"openingCash\": 10.0000}"))
                .andExpect(status().isForbidden());
    }

    private User user(String username, String roleName) {
        User created = new User(username, passwordEncoder.encode("Sale123456!"), "Sale", "User");
        created.setEmail(username + "@test.com");
        Role role = roleRepository.findByName(roleName).orElseThrow();
        created.assignRole(role);
        return created;
    }

    private String bearer(String username, String roleName) {
        if (userRepository.findByUsername(username).isEmpty()) {
            userRepository.save(user(username, roleName));
        }
        return "Bearer " + jwtTokenProvider.generateToken(username);
    }

    private static String idSuffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}

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
class RegisterSessionCloseApiIntegrationTests extends AbstractIntegrationTest {

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
    private Register register;

    @BeforeEach
    void setUp() {
        Store store = storeRepository.save(new Store("CL-" + idSuffix(), "Close Store", "USD", "UTC"));
        Terminal terminal = terminalRepository.save(new Terminal(store, "T1", "Terminal 1", "ACTIVE"));
        register = registerRepository.save(new Register(store, terminal, "R1", "Register 1", "ACTIVE"));
        User cashier = user("rclose.op", RoleName.CASHIER);
        cashier.assignStore(store);
        cashier = userRepository.save(cashier);
        cashierToken = "Bearer " + jwtTokenProvider.generateToken(cashier.getUsername());
        entityManager.flush();
    }

    @Test
    void closeComputesVarianceLocksSessionAndReturnsZReport() throws Exception {
        String sessionId = openSession();
        mockMvc.perform(post("/api/v1/register-sessions/" + sessionId + "/cash-in")
                        .header("Authorization", cashierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 10.0000}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/register-sessions/" + sessionId + "/close")
                        .header("Authorization", cashierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actualCash\": 55.0000, \"notes\": \"Counted\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CLOSED"))
                .andExpect(jsonPath("$.data.expectedCash").value(60.0000))
                .andExpect(jsonPath("$.data.actualCash").value(55.0000))
                .andExpect(jsonPath("$.data.variance").value(-5.0000))
                .andExpect(jsonPath("$.data.zReportNumber").exists());

        mockMvc.perform(get("/api/v1/register-sessions/" + sessionId + "/closing-report")
                        .header("Authorization", cashierToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.variance").value(-5.0000));

        mockMvc.perform(post("/api/v1/register-sessions/" + sessionId + "/cash-in")
                        .header("Authorization", cashierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 1.0000}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("BUSINESS_RULE_VIOLATION"));

        mockMvc.perform(post("/api/v1/register-sessions/" + sessionId + "/close")
                        .header("Authorization", cashierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actualCash\": 55.0000}"))
                .andExpect(status().isUnprocessableEntity());

        entityManager.flush();
        Map<String, Object> audit = jdbcTemplate.queryForMap(
                "SELECT action, entity_type FROM audit_logs WHERE action = 'REGISTER_SESSION_CLOSED'");
        assertThat(audit.get("entity_type")).isEqualTo("RegisterSession");
    }

    private String openSession() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/registers/" + register.getId() + "/sessions/open")
                        .header("Authorization", cashierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"openingCash\": 50.0000}"))
                .andExpect(status().isOk())
                .andReturn();
        return created.getResponse().getContentAsString().split("\"id\":\"")[1].split("\"")[0];
    }

    private User user(String username, String roleName) {
        User created = new User(username, passwordEncoder.encode("Sale123456!"), "Sale", "User");
        created.setEmail(username + "@test.com");
        Role role = roleRepository.findByName(roleName).orElseThrow();
        created.assignRole(role);
        return created;
    }

    private static String idSuffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}

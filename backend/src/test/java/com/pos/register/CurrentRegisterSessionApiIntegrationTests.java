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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Covers {@code GET /api/v1/register-sessions/current}.
 *
 * <p>Without this lookup the session identifier exists only in the response to the call that
 * opened it, so a cashier who reloads the till is locked out of a drawer that is still open.
 */
@Transactional
class CurrentRegisterSessionApiIntegrationTests extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private StoreRepository storeRepository;
    @Autowired private TerminalRepository terminalRepository;
    @Autowired private RegisterRepository registerRepository;
    @Autowired private EntityManager entityManager;

    private String cashierToken;
    private String otherCashierToken;
    private Store store;
    private Register register;

    @BeforeEach
    void setUp() {
        store = storeRepository.save(new Store("CUR-" + idSuffix(), "Current Store", "USD", "UTC"));
        Terminal terminal = terminalRepository.save(new Terminal(store, "T1", "Terminal 1", "ACTIVE"));
        register = registerRepository.save(new Register(store, terminal, "R1", "Register 1", "ACTIVE"));
        cashierToken = "Bearer " + jwtTokenProvider.generateToken(cashierWithStore("cur.cash").getUsername());
        otherCashierToken = "Bearer " + jwtTokenProvider.generateToken(cashierWithStore("cur.other").getUsername());
        entityManager.flush();
    }

    @Test
    void reportsNoOpenSessionAsNullRatherThanAnError() throws Exception {
        mockMvc.perform(get("/api/v1/register-sessions/current").header("Authorization", cashierToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void returnsTheSessionTheCallerOpened() throws Exception {
        String sessionId = openSession();

        mockMvc.perform(get("/api/v1/register-sessions/current").header("Authorization", cashierToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(sessionId))
                .andExpect(jsonPath("$.data.status").value("OPEN"))
                .andExpect(jsonPath("$.data.registerId").value(register.getId().toString()))
                .andExpect(jsonPath("$.data.storeId").value(store.getId().toString()));
    }

    @Test
    void doesNotReturnASessionOpenedByAnotherCashier() throws Exception {
        openSession();

        mockMvc.perform(get("/api/v1/register-sessions/current").header("Authorization", otherCashierToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void stopsReportingTheSessionOnceItIsClosed() throws Exception {
        String sessionId = openSession();

        mockMvc.perform(post("/api/v1/register-sessions/" + sessionId + "/close")
                        .header("Authorization", cashierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actualCash\": 25.0000}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/register-sessions/current").header("Authorization", cashierToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    private String openSession() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/registers/" + register.getId() + "/sessions/open")
                        .header("Authorization", cashierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"openingCash\": 25.0000}"))
                .andExpect(status().isOk())
                .andReturn();
        return created.getResponse().getContentAsString().split("\"id\":\"")[1].split("\"")[0];
    }

    private User cashierWithStore(String username) {
        User created = new User(username, passwordEncoder.encode("Sale123456!"), "Cur", "User");
        created.setEmail(username + "@test.com");
        Role role = roleRepository.findByName(RoleName.CASHIER).orElseThrow();
        created.assignRole(role);
        created.assignStore(store);
        return userRepository.save(created);
    }

    private static String idSuffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}

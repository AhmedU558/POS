package com.pos.organization;

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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Covers who may enumerate the registers in a store.
 *
 * <p>A Cashier holds REGISTER_OPEN, REGISTER_CASH and REGISTER_CLOSE but not REGISTER_READ. Before
 * this, listing returned 403, so the person the till workflow exists for could not find out which
 * register to open.
 */
@Transactional
class RegisterAccessApiIntegrationTests extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private StoreRepository storeRepository;
    @Autowired private TerminalRepository terminalRepository;
    @Autowired private RegisterRepository registerRepository;
    @Autowired private EntityManager entityManager;

    private Store store;
    private Register register;
    private String cashierToken;
    private String outsiderToken;
    private String accountantToken;

    @BeforeEach
    void setUp() {
        store = storeRepository.save(new Store("RACC-" + suffix(), "Register Access Store", "USD", "UTC"));
        Terminal terminal = terminalRepository.save(new Terminal(store, "T1", "Terminal 1", "ACTIVE"));
        register = registerRepository.save(new Register(store, terminal, "R1", "Register 1", "ACTIVE"));

        cashierToken = token(userWithStore("racc.cashier", RoleName.CASHIER, store));
        // A cashier assigned to a different store: permission is not the only gate.
        Store otherStore = storeRepository.save(new Store("ROTH-" + suffix(), "Other Store", "USD", "UTC"));
        outsiderToken = token(userWithStore("racc.outsider", RoleName.CASHIER, otherStore));
        accountantToken = token(userWithStore("racc.accountant", RoleName.ACCOUNTANT, store));
        entityManager.flush();
    }

    @Test
    void aCashierCanListTheRegistersTheyAreAllowedToOpen() throws Exception {
        mockMvc.perform(get("/api/v1/stores/" + store.getId() + "/registers")
                        .header("Authorization", cashierToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(register.getId().toString()))
                .andExpect(jsonPath("$.data[0].status").value("ACTIVE"));
    }

    @Test
    void aCashierCanReadASingleRegisterInTheirStore() throws Exception {
        mockMvc.perform(get("/api/v1/stores/" + store.getId() + "/registers/" + register.getId())
                        .header("Authorization", cashierToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.code").value("R1"));
    }

    @Test
    void storeScopeStillApplies() throws Exception {
        mockMvc.perform(get("/api/v1/stores/" + store.getId() + "/registers")
                        .header("Authorization", outsiderToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void aRoleWithNeitherPermissionIsStillRefused() throws Exception {
        // An Accountant holds no REGISTER_* permission at all.
        mockMvc.perform(get("/api/v1/stores/" + store.getId() + "/registers")
                        .header("Authorization", accountantToken))
                .andExpect(status().isForbidden());
    }

    private User userWithStore(String username, String roleName, Store assigned) {
        User user = new User(username, passwordEncoder.encode("Register123456!"), "Reg", "User");
        user.setEmail(username + "@test.com");
        Role role = roleRepository.findByName(roleName).orElseThrow();
        user.assignRole(role);
        user.assignStore(assigned);
        return userRepository.save(user);
    }

    private String token(User user) {
        return "Bearer " + jwtTokenProvider.generateToken(user.getUsername());
    }

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}

package com.pos.organization;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pos.AbstractIntegrationTest;
import com.pos.organization.domain.Store;
import com.pos.organization.domain.Terminal;
import com.pos.organization.domain.Register;
import com.pos.organization.dto.StoreRequest;
import com.pos.organization.dto.TerminalRequest;
import com.pos.organization.dto.RegisterRequest;
import com.pos.organization.repository.StoreRepository;
import com.pos.organization.repository.TerminalRepository;
import com.pos.organization.repository.RegisterRepository;
import com.pos.users.domain.Role;
import com.pos.users.domain.User;
import com.pos.users.repository.RoleRepository;
import com.pos.users.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@org.springframework.transaction.annotation.Transactional class StoreApiIntegrationTests extends AbstractIntegrationTest {

    @Autowired private org.springframework.test.web.servlet.MockMvc mockMvc;
    @Autowired private com.pos.auth.security.JwtTokenProvider jwtTokenProvider;

    private String authenticate(String username) {
        return "Bearer " + jwtTokenProvider.generateToken(username);
    }

    @Autowired private StoreRepository storeRepository;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;
    @Autowired private TerminalRepository terminalRepository;
    @Autowired private RegisterRepository registerRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() { if (userRepository.findByUsername("admin").isEmpty()) { User testAdmin = new User("admin", passwordEncoder.encode("Admin123!"), "Admin", "User"); testAdmin.setEmail("admin@test.com"); Role superAdmin = roleRepository.findByName("Super Administrator").orElseThrow(); testAdmin.assignRole(superAdmin); userRepository.save(testAdmin); }
        jdbcTemplate.execute("ALTER TABLE inventory_transactions DISABLE TRIGGER ALL");
        jdbcTemplate.execute("DELETE FROM inventory_transactions");
        jdbcTemplate.execute("ALTER TABLE inventory_transactions ENABLE TRIGGER ALL");
        jdbcTemplate.execute("DELETE FROM inventory_balances");
        jdbcTemplate.execute("DELETE FROM user_stores");
        registerRepository.deleteAllInBatch();
        terminalRepository.deleteAllInBatch();
        storeRepository.deleteAllInBatch();
    }

    @Test
    void testCreateAndGetStore_WithScopeIsolation() throws Exception {
        String token = authenticate("admin");

        StoreRequest req = new StoreRequest("NYC-01", "New York Flagship", "USD", "America/New_York");

        String storeResponseJson = mockMvc.perform(post("/api/v1/stores")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath(".data.code").value("NYC-01"))
                .andReturn().getResponse().getContentAsString();
                
        UUID storeId = UUID.fromString(objectMapper.readTree(storeResponseJson).get("data").get("id").asText());

        mockMvc.perform(get("/api/v1/stores/" + storeId)
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath(".data.name").value("New York Flagship"));

        Role superAdmin = roleRepository.findByName("Super Administrator").orElseThrow();
        String password = passwordEncoder.encode("Pass123!");
        User hacker = new User("hacker9", password, "Hack", "Er");
        hacker.setEmail("hacker9@test.com");
        hacker.assignRole(superAdmin);
        userRepository.save(hacker);

        String hackerToken = authenticate("hacker9");

        mockMvc.perform(get("/api/v1/stores/" + storeId)
                        .header("Authorization", hackerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void testCreateTerminalAndRegister_Validation() throws Exception {
        String token = authenticate("admin");

        StoreRequest reqA = new StoreRequest("STORE-A", "Store A", "USD", "UTC");
        String resA = mockMvc.perform(post("/api/v1/stores")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqA)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        UUID storeAId = UUID.fromString(objectMapper.readTree(resA).get("data").get("id").asText());

        StoreRequest reqB = new StoreRequest("STORE-B", "Store B", "USD", "UTC");
        String resB = mockMvc.perform(post("/api/v1/stores")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqB)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        UUID storeBId = UUID.fromString(objectMapper.readTree(resB).get("data").get("id").asText());

        TerminalRequest termReq = new TerminalRequest("TERM-1", "Front Desk", "ONLINE");
        String termRes = mockMvc.perform(post("/api/v1/stores/" + storeAId + "/terminals")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(termReq)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        UUID terminalId = UUID.fromString(objectMapper.readTree(termRes).get("data").get("id").asText());

        RegisterRequest regReq = new RegisterRequest(terminalId, "REG-1", "Main Register", "OPEN");
        mockMvc.perform(post("/api/v1/stores/" + storeBId + "/registers")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(regReq)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath(".error.message").value("Terminal must belong to the same store as the register"));

        mockMvc.perform(post("/api/v1/stores/" + storeAId + "/registers")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(regReq)))
                .andExpect(status().isCreated());
    }
}
package com.tutuchacha.ai.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthFlowTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String username;

    @BeforeEach
    void setUp() {
        username = "user_" + UUID.randomUUID().toString().substring(0, 8);
    }

    @Test
    void registerThenLoginThenMe() throws Exception {
        register(username, "passw0rd")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.username").value(username));

        String token = login(username, "passw0rd");

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value(username))
                .andExpect(jsonPath("$.data.role").value("USER"));
    }

    @Test
    void wrongPasswordReturns401() throws Exception {
        register(username, "passw0rd");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(username, "wrongpass1")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void duplicateUsernameReturns409() throws Exception {
        register(username, "passw0rd").andExpect(status().isOk());

        register(username, "passw0rd").andExpect(status().isConflict());
    }

    @Test
    void meWithoutTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void weakPasswordReturns400() throws Exception {
        register(username, "short").andExpect(status().isBadRequest());
    }

    private org.springframework.test.web.servlet.ResultActions register(String name, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(name, password)));
    }

    private String login(String name, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(name, password)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return json.path("data").path("token").asText();
    }

    private String body(String name, String password) throws Exception {
        return objectMapper.writeValueAsString(new Credentials(name, password));
    }

    private record Credentials(String username, String password) {
    }
}

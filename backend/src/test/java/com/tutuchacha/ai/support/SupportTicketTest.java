package com.tutuchacha.ai.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * M4-04：工单提交 / 列表 / 关闭，以及越权和校验。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SupportTicketTest {

    private static final String DEMO = "tkdemo";
    private static final String OTHER = "tkother";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        seedUser(DEMO);
        seedUser(OTHER);
    }

    private void seedUser(String username) {
        Integer count = jdbc.queryForObject(
                "select count(*) from sys_user where username = ?", Integer.class, username);
        if (count == null || count == 0) {
            jdbc.update("insert into sys_user(username, password_hash, nickname, role) values(?, 'x', ?, 'USER')",
                    username, username);
        }
    }

    private JsonNode data(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    }

    @Test
    void createThenListReturnsOpenTicket() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/support/tickets")
                        .with(user(DEMO).roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"退款多久到账\",\"conversationUid\":\"c_tk1\","
                                + "\"kbId\":3,\"bestScore\":0.51}"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode ticket = data(created);
        assertThat(ticket.path("status").asText()).isEqualTo("OPEN");
        assertThat(ticket.path("question").asText()).isEqualTo("退款多久到账");
        assertThat(ticket.path("conversationUid").asText()).isEqualTo("c_tk1");
        assertThat(ticket.path("bestScore").asDouble()).isEqualTo(0.51);

        MvcResult list = mockMvc.perform(get("/api/support/tickets").with(user(DEMO).roles("USER")))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(list.getResponse().getContentAsString()).contains("退款多久到账");
    }

    @Test
    void closeIsIdempotentAndOwnerScoped() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/support/tickets")
                        .with(user(DEMO).roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"怎么改地址\",\"bestScore\":0.4}"))
                .andExpect(status().isOk())
                .andReturn();
        long id = data(created).path("id").asLong();

        mockMvc.perform(post("/api/support/tickets/" + id + "/close").with(user(OTHER).roles("USER")))
                .andExpect(status().isForbidden());

        MvcResult closed = mockMvc.perform(post("/api/support/tickets/" + id + "/close")
                        .with(user(DEMO).roles("USER")))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(data(closed).path("status").asText()).isEqualTo("CLOSED");

        MvcResult again = mockMvc.perform(post("/api/support/tickets/" + id + "/close")
                        .with(user(DEMO).roles("USER")))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(data(again).path("status").asText()).isEqualTo("CLOSED");
    }

    @Test
    void blankQuestionIsRejected() throws Exception {
        mockMvc.perform(post("/api/support/tickets")
                        .with(user(DEMO).roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"   \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void closingMissingTicketIs404() throws Exception {
        mockMvc.perform(post("/api/support/tickets/99999999/close").with(user(DEMO).roles("USER")))
                .andExpect(status().isNotFound());
    }
}

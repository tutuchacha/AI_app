package com.tutuchacha.ai.chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ConversationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void seedUsers() {
        seedUser("demo");
        seedUser("other");
    }

    private void seedUser(String username) {
        Integer count = jdbc.queryForObject(
                "select count(*) from sys_user where username = ?", Integer.class, username);
        if (count == null || count == 0) {
            jdbc.update("insert into sys_user(username, password_hash, nickname, role) values(?, 'x', ?, 'USER')",
                    username, username);
        }
    }

    private long userId(String username) {
        Long id = jdbc.queryForObject("select id from sys_user where username = ?", Long.class, username);
        return id == null ? 0L : id;
    }

    private String newUid(String tag) {
        String uid = "c_t_" + tag + "_" + System.nanoTime();
        return uid.length() <= 32 ? uid : uid.substring(0, 32);
    }

    private long insertConversation(String uid, String username, String title, LocalDateTime lastAt) {
        jdbc.update("insert into conversation(uid, user_id, module, title, last_message_at) values(?, ?, 'chat', ?, ?)",
                uid, userId(username), title, Timestamp.valueOf(lastAt));
        Long id = jdbc.queryForObject("select id from conversation where uid = ?", Long.class, uid);
        return id == null ? 0L : id;
    }

    private void insertMessage(long conversationId, String role, String content, String status) {
        jdbc.update("insert into message(conversation_id, role, content, status, tokens_in, tokens_out, latency_ms, model)"
                        + " values(?, ?, ?, ?, 5, 9, 320, 'qwen-plus')",
                conversationId, role, content, status);
    }

    @Test
    void listsOwnConversationsNewestFirst() throws Exception {
        String uidNew = newUid("new");
        String uidOld = newUid("old");
        String uidOther = newUid("other");
        insertConversation(uidNew, "demo", "新会话", LocalDateTime.now());
        insertConversation(uidOld, "demo", "旧会话", LocalDateTime.now().minusHours(2));
        insertConversation(uidOther, "other", "别人的会话", LocalDateTime.now());

        MvcResult result = mockMvc.perform(get("/api/conversations").with(user("demo").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();

        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
        List<String> uids = new ArrayList<>();
        data.forEach(node -> uids.add(node.path("uid").asText()));

        assertThat(uids).contains(uidNew, uidOld);
        assertThat(uids).doesNotContain(uidOther);
        assertThat(uids.indexOf(uidNew)).isLessThan(uids.indexOf(uidOld));
        assertThat(data.get(uids.indexOf(uidNew)).path("title").asText()).isEqualTo("新会话");
        assertThat(data.get(uids.indexOf(uidNew)).path("lastMessageAt").asText()).isNotBlank();
    }

    @Test
    void detailReturnsHistoryInOrder() throws Exception {
        String uid = newUid("detail");
        long id = insertConversation(uid, "demo", "历史会话", LocalDateTime.now());
        insertMessage(id, "user", "第一问", "DONE");
        insertMessage(id, "assistant", "第一答", "STOPPED");
        insertMessage(id, "user", "第二问", "DONE");
        insertMessage(id, "assistant", "", "STREAMING");

        mockMvc.perform(get("/api/conversations/" + uid).with(user("demo").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.uid").value(uid))
                .andExpect(jsonPath("$.data.title").value("历史会话"))
                .andExpect(jsonPath("$.data.messages.length()").value(3))
                .andExpect(jsonPath("$.data.messages[0].role").value("user"))
                .andExpect(jsonPath("$.data.messages[0].content").value("第一问"))
                .andExpect(jsonPath("$.data.messages[1].status").value("STOPPED"))
                .andExpect(jsonPath("$.data.messages[2].content").value("第二问"))
                .andExpect(jsonPath("$.data.messages[2].tokensOut").value(9));
    }

    @Test
    void detailOfOtherUserIsForbidden() throws Exception {
        String uid = newUid("forbidden");
        insertConversation(uid, "other", "别人的会话", LocalDateTime.now());

        mockMvc.perform(get("/api/conversations/" + uid).with(user("demo").roles("USER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void detailOfMissingConversationIsNotFound() throws Exception {
        mockMvc.perform(get("/api/conversations/c_not_exists").with(user("demo").roles("USER")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    void requiresLogin() throws Exception {
        mockMvc.perform(get("/api/conversations"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }
}

package com.tutuchacha.ai.date;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * D-01：画像 upsert、对象档案 CRUD、阶段校验与越权。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DateAssistantTest {

    private static final String DEMO = "datedemo";
    private static final String OTHER = "dateother";

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
    void profileIsUpsertedAndScoped() throws Exception {
        MvcResult empty = mockMvc.perform(get("/api/date/profile").with(user(DEMO).roles("USER")))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(data(empty).isNull()).isTrue();

        mockMvc.perform(put("/api/date/profile")
                        .with(user(DEMO).roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"小林\",\"age\":28,\"city\":\"杭州\","
                                + "\"hobbies\":\"跑步、咖啡\",\"goal\":\"找长期关系\"}"))
                .andExpect(status().isOk());

        MvcResult second = mockMvc.perform(put("/api/date/profile")
                        .with(user(DEMO).roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"小林\",\"age\":29,\"city\":\"杭州\","
                                + "\"hobbies\":\"跑步\",\"goal\":\"找长期关系\"}"))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(data(second).path("age").asInt()).isEqualTo(29);

        MvcResult mine = mockMvc.perform(get("/api/date/profile").with(user(DEMO).roles("USER")))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(data(mine).path("nickname").asText()).isEqualTo("小林");
        assertThat(data(mine).path("city").asText()).isEqualTo("杭州");

        MvcResult others = mockMvc.perform(get("/api/date/profile").with(user(OTHER).roles("USER")))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(data(others).isNull()).isTrue();
    }

    @Test
    void targetCrudKeepsStageAndOwnership() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/date/targets")
                        .with(user(DEMO).roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"小周\",\"channel\":\"朋友介绍\",\"stage\":\"CHATTING\","
                                + "\"city\":\"杭州\",\"interests\":\"露营、猫\",\"lastContactAt\":\"2026-10-26\"}"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode target = data(created);
        long id = target.path("id").asLong();
        assertThat(target.path("stage").asText()).isEqualTo("CHATTING");
        assertThat(target.path("stageLabel").asText()).isEqualTo("聊天中");
        assertThat(target.path("lastContactAt").asText()).isEqualTo("2026-10-26");

        mockMvc.perform(put("/api/date/targets/" + id)
                        .with(user(DEMO).roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"小周\",\"stage\":\"DATING\",\"city\":\"杭州\"}"))
                .andExpect(status().isOk());

        MvcResult list = mockMvc.perform(get("/api/date/targets").with(user(DEMO).roles("USER")))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(data(list).get(0).path("stageLabel").asText()).isEqualTo("约会中");

        mockMvc.perform(put("/api/date/targets/" + id)
                        .with(user(OTHER).roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"小周\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/date/targets/" + id).with(user(OTHER).roles("USER")))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/date/targets/" + id).with(user(DEMO).roles("USER")))
                .andExpect(status().isOk());
        MvcResult after = mockMvc.perform(get("/api/date/targets").with(user(DEMO).roles("USER")))
                .andReturn();
        assertThat(data(after).isEmpty()).isTrue();
    }

    @Test
    void invalidStageIsRejected() throws Exception {
        mockMvc.perform(post("/api/date/targets")
                        .with(user(DEMO).roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"小周\",\"stage\":\"MARRIED\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void blankNameIsRejected() throws Exception {
        mockMvc.perform(post("/api/date/targets")
                        .with(user(DEMO).roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"  \"}"))
                .andExpect(status().isBadRequest());
    }
}

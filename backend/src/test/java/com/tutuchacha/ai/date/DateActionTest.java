package com.tutuchacha.ai.date;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tutuchacha.ai.gateway.AiChatResponse;
import com.tutuchacha.ai.gateway.AiGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * D-04：执行记录 CRUD、越权与校验，复盘判定与阶段建议（阶段只允许一步一跳）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DateActionTest {

    private static final String DEMO = "actdemo";
    private static final String OTHER = "actother";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AiGateway aiGateway;

    @BeforeEach
    void setUp() {
        seedUser(DEMO);
        seedUser(OTHER);
        when(aiGateway.complete(any())).thenReturn(new AiChatResponse(
                "{\"verdict\":\"CONTINUE\",\"reason\":\"她主动补充了细节，方向没问题。\","
                        + "\"nextStep\":\"两天后约具体时间，给两个选项。\",\"stage\":\"CHATTING\"}",
                "qwen-plus", 90, 70, 1200));
    }

    private void seedUser(String username) {
        Integer count = jdbc.queryForObject(
                "select count(*) from sys_user where username = ?", Integer.class, username);
        if (count == null || count == 0) {
            jdbc.update("insert into sys_user(username, password_hash, nickname, role) values(?, 'x', ?, 'USER')",
                    username, username);
        }
    }

    private long createTarget(String who, String stage) throws Exception {
        MvcResult created = mockMvc.perform(post("/api/date/targets")
                        .with(user(who).roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"小周\",\"stage\":\"" + stage + "\",\"city\":\"杭州\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return data(created).path("id").asLong();
    }

    private JsonNode data(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    }

    private JsonNode createAction(String who, long targetId, String title) throws Exception {
        MvcResult created = mockMvc.perform(post("/api/date/actions")
                        .with(user(who).roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetId\":" + targetId + ",\"title\":\"" + title + "\","
                                + "\"detail\":\"提到她上次说的营地\",\"plannedAt\":\"2026-10-30\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return data(created);
    }

    @Test
    void createUpdateAndListActions() throws Exception {
        long targetId = createTarget(DEMO, "MET");
        JsonNode action = createAction(DEMO, targetId, "用露营兴趣开场");
        assertThat(action.path("status").asText()).isEqualTo("PLANNED");
        assertThat(action.path("statusLabel").asText()).isEqualTo("计划中");
        assertThat(action.path("resultLabel").asText()).isEqualTo("未回填");
        assertThat(action.path("plannedAt").asText()).isEqualTo("2026-10-30");
        long actionId = action.path("id").asLong();

        MvcResult updated = mockMvc.perform(put("/api/date/actions/" + actionId)
                        .with(user(DEMO).roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DONE\",\"result\":\"GOOD\",\"note\":\"她主动补了营地名字\"}"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode done = data(updated);
        assertThat(done.path("status").asText()).isEqualTo("DONE");
        assertThat(done.path("statusLabel").asText()).isEqualTo("已完成");
        assertThat(done.path("result").asText()).isEqualTo("GOOD");
        assertThat(done.path("resultLabel").asText()).isEqualTo("反应不错");
        assertThat(done.path("note").asText()).contains("营地");
        assertThat(done.path("doneAt").asText()).isNotEmpty();

        MvcResult list = mockMvc.perform(get("/api/date/actions")
                        .param("targetId", String.valueOf(targetId))
                        .with(user(DEMO).roles("USER")))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(data(list)).hasSize(1);

        mockMvc.perform(delete("/api/date/actions/" + actionId)
                        .with(user(DEMO).roles("USER")))
                .andExpect(status().isOk());
        MvcResult afterDelete = mockMvc.perform(get("/api/date/actions")
                        .param("targetId", String.valueOf(targetId))
                        .with(user(DEMO).roles("USER")))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(data(afterDelete)).isEmpty();
    }

    @Test
    void actionsAreScopedToOwner() throws Exception {
        long targetId = createTarget(DEMO, "MET");
        long actionId = createAction(DEMO, targetId, "发一条日常").path("id").asLong();

        mockMvc.perform(post("/api/date/actions")
                        .with(user(OTHER).roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetId\":" + targetId + ",\"title\":\"越权\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/date/actions")
                        .param("targetId", String.valueOf(targetId))
                        .with(user(OTHER).roles("USER")))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/date/actions/" + actionId)
                        .with(user(OTHER).roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DONE\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/date/actions")
                        .with(user(DEMO).roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetId\":99999999,\"title\":\"不存在\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void invalidTitleAndEnumAreRejected() throws Exception {
        long targetId = createTarget(DEMO, "MET");

        mockMvc.perform(post("/api/date/actions")
                        .with(user(DEMO).roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetId\":" + targetId + ",\"title\":\"  \"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/date/actions")
                        .with(user(DEMO).roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetId\":" + targetId + "}"))
                .andExpect(status().isBadRequest());

        long actionId = createAction(DEMO, targetId, "发一条日常").path("id").asLong();
        mockMvc.perform(put("/api/date/actions/" + actionId)
                        .with(user(DEMO).roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"WHATEVER\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/date/actions/" + actionId)
                        .with(user(DEMO).roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"result\":\"MAYBE\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void reviewSuggestsNextStage() throws Exception {
        long targetId = createTarget(DEMO, "MET");
        long actionId = createAction(DEMO, targetId, "用露营兴趣开场").path("id").asLong();
        mockMvc.perform(put("/api/date/actions/" + actionId)
                        .with(user(DEMO).roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DONE\",\"result\":\"GOOD\"}"))
                .andExpect(status().isOk());

        MvcResult reviewed = mockMvc.perform(post("/api/date/review")
                        .with(user(DEMO).roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetId\":" + targetId + "}"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode review = data(reviewed);
        assertThat(review.path("verdict").asText()).isEqualTo("CONTINUE");
        assertThat(review.path("verdictLabel").asText()).isEqualTo("继续推进");
        assertThat(review.path("reason").asText()).contains("细节");
        assertThat(review.path("nextStep").asText()).contains("约具体时间");
        assertThat(review.path("suggestedStage").asText()).isEqualTo("CHATTING");
        assertThat(review.path("suggestedStageLabel").asText()).isEqualTo("聊天中");
        assertThat(review.path("stageChanged").asBoolean()).isTrue();
        assertThat(review.path("completionTokens").asInt()).isEqualTo(70);
    }

    @Test
    void stageJumpIsIgnored() throws Exception {
        long targetId = createTarget(DEMO, "MET");
        createAction(DEMO, targetId, "发一条日常");
        when(aiGateway.complete(any())).thenReturn(new AiChatResponse(
                "{\"verdict\":\"SLOW_DOWN\",\"reason\":\"回复变短了。\",\"nextStep\":\"隔天再联系。\",\"stage\":\"TOGETHER\"}",
                "qwen-plus", 60, 40, 800));

        MvcResult reviewed = mockMvc.perform(post("/api/date/review")
                        .with(user(DEMO).roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetId\":" + targetId + "}"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode review = data(reviewed);
        assertThat(review.path("verdict").asText()).isEqualTo("SLOW_DOWN");
        assertThat(review.path("verdictLabel").asText()).isEqualTo("先降速");
        assertThat(review.path("suggestedStage").asText()).isEqualTo("MET");
        assertThat(review.path("stageChanged").asBoolean()).isFalse();
    }

    @Test
    void reviewRequiresActionAndValidJson() throws Exception {
        long targetId = createTarget(DEMO, "MET");

        mockMvc.perform(post("/api/date/review")
                        .with(user(DEMO).roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetId\":" + targetId + "}"))
                .andExpect(status().isBadRequest());

        createAction(DEMO, targetId, "发一条日常");
        when(aiGateway.complete(any())).thenReturn(new AiChatResponse("先聊聊看", "qwen-plus", 10, 10, 100));

        mockMvc.perform(post("/api/date/review")
                        .with(user(DEMO).roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetId\":" + targetId + "}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value(502));
    }
}

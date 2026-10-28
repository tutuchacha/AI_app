package com.tutuchacha.ai.date;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tutuchacha.ai.date.dto.DateSpot;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * D-02：建议生成、结构化字段、地点补全、历史与越权；模型与高德都被替换。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DateAdviceTest {

    private static final String DEMO = "advdemo";
    private static final String OTHER = "advother";

    private static final String AI_JSON = """
            ```json
            {"goal":"把寒暄推进到具体话题",
             "actions":[
               {"title":"用露营兴趣开场","detail":"提到她上次说的营地","timing":"今晚 8 点前"},
               {"title":"给两个周末选项","detail":"周六下午咖啡或展览二选一","timing":"周三晚"},
               {"title":"留出退出空间","detail":"说忙就改天，不追问","timing":"每次邀约"}],
             "scripts":[
               {"scene":"开场","text":"上次说的营地我查了下，周末人不多，你去的是哪家？"},
               {"scene":"邀约","text":"周六下午我正好在那附近，要不要一起喝杯咖啡？"}],
             "avoid":["别连发三条","别追问前任","别替她做决定"],
             "success":"她主动补充细节并反问你的安排",
             "spotKeywords":["咖啡","展览"]}
            ```
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AiGateway aiGateway;

    @MockitoBean
    private AmapClient amapClient;

    @BeforeEach
    void setUp() {
        seedUser(DEMO);
        seedUser(OTHER);
        when(aiGateway.complete(any()))
                .thenReturn(new AiChatResponse(AI_JSON, "qwen-plus", 120, 180, 900));
        when(amapClient.search(anyString(), anyString(), anyInt()))
                .thenReturn(List.of(new DateSpot("某某咖啡", "文三路 1 号", "咖啡厅", "120.1,30.2", "4.6", "")));
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

    private long createTarget(String who) throws Exception {
        MvcResult created = mockMvc.perform(post("/api/date/targets")
                        .with(user(who).roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"小周\",\"channel\":\"朋友介绍\",\"stage\":\"CHATTING\","
                                + "\"city\":\"杭州\",\"interests\":\"露营、猫\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return data(created).path("id").asLong();
    }

    private JsonNode createAdvice(String who, long targetId) throws Exception {
        MvcResult created = mockMvc.perform(post("/api/date/advice")
                        .with(user(who).roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetId\":" + targetId + "}"))
                .andExpect(status().isOk())
                .andReturn();
        return data(created);
    }

    @Test
    void adviceIsStructuredAndFilledWithSpots() throws Exception {
        long targetId = createTarget(DEMO);
        JsonNode advice = createAdvice(DEMO, targetId);

        assertThat(advice.path("goal").asText()).isEqualTo("把寒暄推进到具体话题");
        assertThat(advice.path("stage").asText()).isEqualTo("CHATTING");
        assertThat(advice.path("stageLabel").asText()).isEqualTo("聊天中");
        assertThat(advice.path("city").asText()).isEqualTo("杭州");
        assertThat(advice.path("actions")).hasSize(3);
        assertThat(advice.path("actions").get(0).path("title").asText()).isEqualTo("用露营兴趣开场");
        assertThat(advice.path("scripts").get(0).path("scene").asText()).isEqualTo("开场");
        assertThat(advice.path("avoid")).hasSize(3);
        assertThat(advice.path("success").asText()).contains("主动补充细节");
        assertThat(advice.path("spots")).hasSize(1);
        assertThat(advice.path("spots").get(0).path("name").asText()).isEqualTo("某某咖啡");
        assertThat(advice.path("model").asText()).isEqualTo("qwen-plus");
        assertThat(advice.path("promptTokens").asInt()).isEqualTo(120);

        MvcResult history = mockMvc.perform(get("/api/date/advice")
                        .param("targetId", String.valueOf(targetId))
                        .with(user(DEMO).roles("USER")))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(data(history)).hasSize(1);
        assertThat(data(history).get(0).path("goal").asText()).isEqualTo("把寒暄推进到具体话题");
    }

    @Test
    void adviceIsScopedToOwner() throws Exception {
        long targetId = createTarget(DEMO);

        mockMvc.perform(post("/api/date/advice")
                        .with(user(OTHER).roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetId\":" + targetId + "}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/date/advice")
                        .param("targetId", String.valueOf(targetId))
                        .with(user(OTHER).roles("USER")))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/date/advice")
                        .with(user(DEMO).roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetId\":99999999}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void missingTargetIdIsRejected() throws Exception {
        mockMvc.perform(post("/api/date/advice")
                        .with(user(DEMO).roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void malformedModelOutputIsBadGateway() throws Exception {
        long targetId = createTarget(DEMO);
        when(aiGateway.complete(any()))
                .thenReturn(new AiChatResponse("抱歉，我只能聊天", "qwen-plus", 10, 10, 100));

        mockMvc.perform(post("/api/date/advice")
                        .with(user(DEMO).roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetId\":" + targetId + "}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value(502));
    }
}

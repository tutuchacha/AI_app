package com.tutuchacha.ai.date;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * D-05：阶段变更只在真的变了时落一条流水（区分手动 / 复盘应用），
 * 时间线把建档、执行、阶段变更拼成倒序流，越权 403。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DateTimelineTest {

    private static final String DEMO = "tldemo";
    private static final String OTHER = "tlother";

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

    private void updateStage(String who, long targetId, String stage, String source) throws Exception {
        String body = "{\"name\":\"小周\",\"stage\":\"" + stage + "\",\"city\":\"杭州\""
                + (source == null ? "" : ",\"stageSource\":\"" + source + "\"") + "}";
        mockMvc.perform(put("/api/date/targets/" + targetId)
                        .with(user(who).roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }

    private JsonNode data(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    }

    private List<String> types(JsonNode timeline) {
        List<String> types = new ArrayList<>();
        timeline.forEach(node -> types.add(node.path("type").asText()));
        return types;
    }

    @Test
    void stageLogOnlyWhenStageChanges() throws Exception {
        long targetId = createTarget(DEMO, "MET");
        assertThat(stageLogs(targetId)).isZero();

        updateStage(DEMO, targetId, "CHATTING", "REVIEW");
        assertThat(stageLogs(targetId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select source from date_stage_log where target_id = ?",
                String.class, targetId)).isEqualTo("REVIEW");

        // 同阶段再保存一次不该产生新流水
        updateStage(DEMO, targetId, "CHATTING", "REVIEW");
        assertThat(stageLogs(targetId)).isEqualTo(1);

        updateStage(DEMO, targetId, "MET", null);
        assertThat(stageLogs(targetId)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select source from date_stage_log where target_id = ? and to_stage = 'MET'",
                String.class, targetId)).isEqualTo("MANUAL");
    }

    private int stageLogs(long targetId) {
        Integer count = jdbc.queryForObject("select count(*) from date_stage_log where target_id = ?",
                Integer.class, targetId);
        return count == null ? 0 : count;
    }

    @Test
    void timelineMergesEventsNewestFirst() throws Exception {
        long targetId = createTarget(DEMO, "MET");
        mockMvc.perform(post("/api/date/actions")
                        .with(user(DEMO).roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetId\":" + targetId + ",\"title\":\"用露营兴趣开场\"}"))
                .andExpect(status().isOk());
        updateStage(DEMO, targetId, "CHATTING", "REVIEW");

        MvcResult result = mockMvc.perform(get("/api/date/timeline")
                        .param("targetId", String.valueOf(targetId))
                        .with(user(DEMO).roles("USER")))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode timeline = data(result);
        assertThat(types(timeline)).contains("TARGET", "ACTION", "STAGE").doesNotContain("ADVICE");
        assertThat(timeline).allSatisfy(node -> {
            assertThat(node.path("typeLabel").asText()).isNotBlank();
            assertThat(node.path("title").asText()).isNotBlank();
            assertThat(node.path("at").asText()).isNotBlank();
        });

        List<String> times = new ArrayList<>();
        timeline.forEach(node -> times.add(node.path("at").asText()));
        assertThat(times).isSortedAccordingTo((a, b) -> b.compareTo(a));

        JsonNode stage = null;
        for (JsonNode node : timeline) {
            if ("STAGE".equals(node.path("type").asText())) {
                stage = node;
            }
        }
        assertThat(stage).isNotNull();
        assertThat(stage.path("title").asText()).isEqualTo("刚认识 → 聊天中");
        assertThat(stage.path("detail").asText()).isEqualTo("复盘后应用");
    }

    @Test
    void timelineIsScopedToOwner() throws Exception {
        long targetId = createTarget(DEMO, "MET");
        mockMvc.perform(get("/api/date/timeline")
                        .param("targetId", String.valueOf(targetId))
                        .with(user(OTHER).roles("USER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/date/timeline")
                        .param("targetId", "99999999")
                        .with(user(DEMO).roles("USER")))
                .andExpect(status().isNotFound());
    }
}

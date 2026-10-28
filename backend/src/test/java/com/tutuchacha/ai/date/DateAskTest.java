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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * D-03：约会问答——区域优先走周边搜索，无区域走城市关键词；越权与格式异常。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DateAskTest {

    private static final String DEMO = "askdemo";
    private static final String OTHER = "askother";

    @MockitoBean
    private AiGateway aiGateway;

    @MockitoBean
    private AmapClient amapClient;

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
        when(aiGateway.complete(any())).thenReturn(new AiChatResponse(
                "{\"reply\":\"下午去西湖边走走，晚上在湖滨吃本帮菜，人均 120 左右。\","
                        + "\"area\":\"西湖\",\"keywords\":[\"本帮菜\",\"咖啡馆\"]}",
                "qwen-plus", 80, 60, 1500));
        when(amapClient.geocode("杭州", "西湖")).thenReturn("120.15,30.25");
        when(amapClient.around(eq("120.15,30.25"), anyString(), anyInt(), anyInt()))
                .thenReturn(List.of(
                        new DateSpot("楼外楼", "孤山路 30 号", "本帮菜", "120.14,30.25", "4.5", "800"),
                        new DateSpot("湖边咖啡", "北山街 1 号", "咖啡厅", "120.15,30.26", "4.4", "1200")));
        when(amapClient.search(eq("杭州"), anyString(), anyInt()))
                .thenReturn(List.of(new DateSpot("弄堂餐厅", "文三路 2 号", "餐厅", "120.1,30.2", "4.3", "")));
    }

    private void seedUser(String username) {
        Integer count = jdbc.queryForObject(
                "select count(*) from sys_user where username = ?", Integer.class, username);
        if (count == null || count == 0) {
            jdbc.update("insert into sys_user(username, password_hash, nickname, role) values(?, 'x', ?, 'USER')",
                    username, username);
        }
    }

    private long createTarget(String who) throws Exception {
        MvcResult created = mockMvc.perform(post("/api/date/targets")
                        .with(user(who).roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"小周\",\"stage\":\"CHATTING\",\"city\":\"杭州\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(created.getResponse().getContentAsString()).path("data").path("id").asLong();
    }

    private JsonNode ask(String who, long targetId, String question, String area) throws Exception {
        String body = "{\"question\":\"" + question + "\",\"targetId\":" + targetId
                + ",\"area\":\"" + area + "\"}";
        MvcResult result = mockMvc.perform(post("/api/date/ask")
                        .with(user(who).roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    }

    @Test
    void areaUsesAroundSearchAndReturnsSpots() throws Exception {
        long targetId = createTarget(DEMO);

        JsonNode data = ask(DEMO, targetId, "今天去哪里玩，晚上吃个饭", "西湖");

        assertThat(data.path("reply").asText()).contains("湖滨");
        assertThat(data.path("area").asText()).isEqualTo("西湖");
        assertThat(data.path("city").asText()).isEqualTo("杭州");
        assertThat(data.path("keywords")).hasSize(2);
        assertThat(data.path("spots")).hasSize(2);
        assertThat(data.path("spots").get(0).path("name").asText()).isEqualTo("楼外楼");
        assertThat(data.path("spots").get(0).path("distance").asText()).isEqualTo("800");
        verify(amapClient).geocode("杭州", "西湖");
        verify(amapClient, never()).search(anyString(), anyString(), anyInt());
    }

    @Test
    void noAreaFallsBackToCitySearch() throws Exception {
        long targetId = createTarget(DEMO);
        when(aiGateway.complete(any())).thenReturn(new AiChatResponse(
                "{\"reply\":\"晚上找家安静的餐厅，别太吵。\",\"area\":\"\",\"keywords\":[\"餐厅\"]}",
                "qwen-plus", 70, 40, 900));

        JsonNode data = ask(DEMO, targetId, "晚上吃个饭", "");

        assertThat(data.path("area").asText()).isEmpty();
        assertThat(data.path("spots")).hasSize(1);
        assertThat(data.path("spots").get(0).path("name").asText()).isEqualTo("弄堂餐厅");
        verify(amapClient, never()).around(anyString(), anyString(), anyInt(), anyInt());
    }

    @Test
    void blankQuestionIsRejected() throws Exception {
        mockMvc.perform(post("/api/date/ask")
                        .with(user(DEMO).roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"  \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void otherUsersTargetIsForbidden() throws Exception {
        long targetId = createTarget(DEMO);

        mockMvc.perform(post("/api/date/ask")
                        .with(user(OTHER).roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"去哪玩\",\"targetId\":" + targetId + "}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void malformedModelOutputFallsBackToPlainText() throws Exception {
        long targetId = createTarget(DEMO);
        when(aiGateway.complete(any())).thenReturn(new AiChatResponse("今天适合去西湖边走走，晚上找家安静的餐厅。",
                "qwen-plus", 10, 10, 100));

        MvcResult result = mockMvc.perform(post("/api/date/ask")
                        .with(user(DEMO).roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"去哪玩\",\"targetId\":" + targetId + "}"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
        assertThat(data.path("reply").asText()).contains("西湖");
        assertThat(data.path("keywords")).isNotEmpty();
        assertThat(data.path("spots")).isNotEmpty();
    }
}

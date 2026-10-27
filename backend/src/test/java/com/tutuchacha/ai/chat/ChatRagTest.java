package com.tutuchacha.ai.chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tutuchacha.ai.common.BusinessException;
import com.tutuchacha.ai.common.ErrorCode;
import com.tutuchacha.ai.gateway.AiChatRequest;
import com.tutuchacha.ai.gateway.AiChatResponse;
import com.tutuchacha.ai.gateway.AiGateway;
import com.tutuchacha.ai.gateway.AiStreamChunk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import reactor.core.publisher.Flux;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * M2-05：聊天接 RAG。切片向量固定为 [1,0,0]、查询向量同值，命中分数恒为 1，便于断言引用。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ChatRagTest {

    private static final String DEMO = "ragdemo";
    private static final String OTHER = "ragother";
    private static final String MATERIAL = "员工年假规定：入职满一年享有 5 天带薪年假，满三年 10 天。";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AiGateway aiGateway;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        seedUser(DEMO);
        seedUser(OTHER);
        when(aiGateway.defaultModel()).thenReturn("qwen-plus");
        when(aiGateway.embed(anyList())).thenReturn(List.of(new float[]{1f, 0f, 0f}));
        when(aiGateway.stream(any(AiChatRequest.class))).thenReturn(Flux.just(
                new AiStreamChunk("满一年 5 天", "qwen-plus", 12, 6, "stop")));
        when(aiGateway.complete(any(AiChatRequest.class))).thenReturn(
                new AiChatResponse("满一年 5 天", "qwen-plus", 12, 6, 300));
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

    private record Library(long kbId, long chunkId) {
    }

    private Library seedLibrary(String username) {
        jdbc.update("insert into knowledge_base(user_id, name, chunk_size, chunk_overlap) values(?, ?, 200, 20)",
                userId(username), "RAG-" + System.nanoTime());
        Long kbId = jdbc.queryForObject(
                "select max(id) from knowledge_base where user_id = ?", Long.class, userId(username));
        jdbc.update("insert into document(kb_id, filename, ext, size, sha256, status, chunk_count) "
                        + "values(?, '员工手册.txt', 'txt', 100, ?, 'READY', 1)",
                kbId, "sha-" + System.nanoTime());
        Long documentId = jdbc.queryForObject(
                "select max(id) from document where kb_id = ?", Long.class, kbId);
        jdbc.update("insert into document_chunk(document_id, kb_id, chunk_index, content, embedding, "
                        + "token_count, metadata_json) values(?, ?, 0, ?, '[1.0,0.0,0.0]', 20, '{}')",
                documentId, kbId, MATERIAL);
        Long chunkId = jdbc.queryForObject(
                "select max(id) from document_chunk where kb_id = ?", Long.class, kbId);
        return new Library(kbId == null ? 0L : kbId, chunkId == null ? 0L : chunkId);
    }

    @Test
    void streamEmitsCitationBeforeDeltaAndInjectsMaterial() throws Exception {
        Library library = seedLibrary(DEMO);

        String body = streamBody("{\"content\":\"年假有几天\",\"kbId\":" + library.kbId() + "}");

        assertThat(body).contains("\"type\":\"meta\"", "\"type\":\"citation\"", "\"type\":\"delta\"");
        assertThat(body).contains("\"citation\":{\"id\":\"" + library.chunkId() + "\"");
        assertThat(body).contains("\"title\":\"员工手册.txt\"", "\"score\":1.0");
        assertThat(body).contains("满一年 5 天");
        assertThat(body.indexOf("\"type\":\"meta\"")).isLessThan(body.indexOf("\"type\":\"citation\""));
        assertThat(body.indexOf("\"type\":\"citation\"")).isLessThan(body.indexOf("\"type\":\"delta\""));
        assertThat(body.lastIndexOf("\"type\":\"usage\"")).isLessThan(body.indexOf("\"type\":\"done\""));

        ArgumentCaptor<AiChatRequest> captor = ArgumentCaptor.forClass(AiChatRequest.class);
        verify(aiGateway).stream(captor.capture());
        String system = captor.getValue().messages().get(0).content();
        assertThat(captor.getValue().messages().get(0).role()).isEqualTo("system");
        assertThat(system).contains("参考资料", MATERIAL, "不要编造");
    }

    @Test
    void citationsArePersistedAndRestoredFromHistory() throws Exception {
        Library library = seedLibrary(DEMO);
        String body = streamBody("{\"content\":\"年假有几天\",\"kbId\":" + library.kbId() + "}");
        String uid = conversationIdOf(body);

        MvcResult result = mockMvc.perform(get("/api/conversations/" + uid)
                        .with(user(DEMO).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.kbId").value(library.kbId()))
                .andExpect(jsonPath("$.data.messages[1].role").value("assistant"))
                .andExpect(jsonPath("$.data.messages[1].citations[0].id").value(String.valueOf(library.chunkId())))
                .andExpect(jsonPath("$.data.messages[1].citations[0].title").value("员工手册.txt"))
                .andExpect(jsonPath("$.data.messages[1].citations[0].score").value(1.0))
                .andReturn();

        JsonNode assistant = objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("messages").get(1);
        assertThat(assistant.path("citations").get(0).path("snippet").asText()).contains("年假");
        assertThat(assistant.path("content").asText()).isEqualTo("满一年 5 天");
    }

    @Test
    void laterTurnsReuseConversationKnowledgeBase() throws Exception {
        Library library = seedLibrary(DEMO);
        String body = streamBody("{\"content\":\"年假有几天\",\"kbId\":" + library.kbId() + "}");
        String uid = conversationIdOf(body);

        String second = streamBody("{\"content\":\"再说一次\",\"conversationId\":\"" + uid + "\"}");

        assertThat(second).contains("\"type\":\"citation\"").contains("\"id\":\"" + library.chunkId() + "\"");
        ArgumentCaptor<AiChatRequest> captor = ArgumentCaptor.forClass(AiChatRequest.class);
        verify(aiGateway, org.mockito.Mockito.times(2)).stream(captor.capture());
        assertThat(captor.getAllValues().get(1).messages().get(0).content()).contains(MATERIAL);
    }

    @Test
    void nonStreamChatReturnsCitations() throws Exception {
        Library library = seedLibrary(DEMO);

        mockMvc.perform(post("/api/chat")
                        .with(user(DEMO).roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"年假有几天\",\"kbId\":" + library.kbId() + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.citations[0].id").value(String.valueOf(library.chunkId())))
                .andExpect(jsonPath("$.data.citations[0].title").value("员工手册.txt"));
    }

    @Test
    void otherUsersKnowledgeBaseIsRejectedWithoutPersistingMessages() throws Exception {
        Library library = seedLibrary(OTHER);
        int before = messageCount(DEMO);

        String body = streamBody("{\"content\":\"偷看别人的库\",\"kbId\":" + library.kbId() + "}");

        assertThat(body).contains("\"type\":\"error\"", "FORBIDDEN");
        assertThat(body).doesNotContain("\"type\":\"delta\"", "\"type\":\"done\"");
        assertThat(messageCount(DEMO)).isEqualTo(before);
    }

    @Test
    void missingKnowledgeBaseBecomesErrorEvent() throws Exception {
        String body = streamBody("{\"content\":\"不存在的库\",\"kbId\":99999999}");

        assertThat(body).contains("\"type\":\"error\"", "KB_NOT_FOUND");
        assertThat(body).doesNotContain("\"type\":\"done\"");
    }

    @Test
    void retrievalFailureDegradesToPlainAnswer() throws Exception {
        Library library = seedLibrary(DEMO);
        when(aiGateway.embed(anyList()))
                .thenThrow(new BusinessException(ErrorCode.BAD_GATEWAY, "向量服务不可用"));

        String body = streamBody("{\"content\":\"年假有几天\",\"kbId\":" + library.kbId() + "}");

        assertThat(body).contains("\"type\":\"delta\"", "满一年 5 天", "\"type\":\"done\"");
        assertThat(body).doesNotContain("\"type\":\"citation\"");
        ArgumentCaptor<AiChatRequest> captor = ArgumentCaptor.forClass(AiChatRequest.class);
        verify(aiGateway).stream(captor.capture());
        assertThat(captor.getValue().messages().get(0).content()).doesNotContain("参考资料");
    }

    @Test
    void deletingKnowledgeBaseUnbindsConversation() throws Exception {
        Library library = seedLibrary(DEMO);
        String uid = conversationIdOf(streamBody("{\"content\":\"年假有几天\",\"kbId\":" + library.kbId() + "}"));

        mockMvc.perform(delete("/api/knowledge-bases/" + library.kbId())
                        .with(user(DEMO).roles("USER")))
                .andExpect(status().isOk());

        MvcResult detail = mockMvc.perform(get("/api/conversations/" + uid)
                        .with(user(DEMO).roles("USER")))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode kbId = objectMapper.readTree(detail.getResponse().getContentAsString())
                .path("data").path("kbId");
        // 全局序列化忽略 null，所以这里要么缺失、要么显式 null
        assertThat(kbId.isMissingNode() || kbId.isNull()).as("删库后会话应解绑").isTrue();
    }

    private int messageCount(String username) {
        Integer count = jdbc.queryForObject(
                "select count(*) from message m join conversation c on c.id = m.conversation_id "
                        + "where c.user_id = ?", Integer.class, userId(username));
        return count == null ? 0 : count;
    }

    private String streamBody(String json) throws Exception {
        MvcResult pending = mockMvc.perform(post("/api/chat/stream")
                        .with(user(DEMO).roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(request().asyncStarted())
                .andReturn();
        return mockMvc.perform(asyncDispatch(pending))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private String conversationIdOf(String body) {
        Matcher matcher = Pattern.compile("\"conversationId\":\"(c_[a-z0-9]+)\"").matcher(body);
        assertThat(matcher.find()).as("meta 事件应包含会话 ID").isTrue();
        return matcher.group(1);
    }
}

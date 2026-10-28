package com.tutuchacha.ai.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tutuchacha.ai.gateway.AiChatRequest;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * M4-01~03：客服接真实知识库、引用可溯源、低置信走客服专用提示词。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SupportFlowTest {

    private static final String DEMO = "supdemo";
    private static final String OTHER = "supother";
    private static final String MATERIAL = "退款规则：签收后 7 天内可全额退款，1~3 个工作日到账。";

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
                new AiStreamChunk("7 天内可退", "qwen-plus", 10, 4, "stop")));
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
                userId(username), "SUP-" + System.nanoTime());
        Long kbId = jdbc.queryForObject(
                "select max(id) from knowledge_base where user_id = ?", Long.class, userId(username));
        jdbc.update("insert into document(kb_id, filename, ext, size, sha256, status, chunk_count) "
                        + "values(?, '售后手册.md', 'md', 100, ?, 'READY', 1)",
                kbId, "sha-" + System.nanoTime());
        Long documentId = jdbc.queryForObject(
                "select max(id) from document where kb_id = ?", Long.class, kbId);
        jdbc.update("insert into document_chunk(document_id, kb_id, chunk_index, content, embedding, "
                        + "token_count, metadata_json) values(?, ?, 0, ?, '[1.0,0.0,0.0]', 18, '{}')",
                documentId, kbId, MATERIAL);
        Long chunkId = jdbc.queryForObject(
                "select max(id) from document_chunk where kb_id = ?", Long.class, kbId);
        return new Library(kbId == null ? 0L : kbId, chunkId == null ? 0L : chunkId);
    }

    @Test
    void policyExposesDefaults() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/support/policy").with(user(DEMO).roles("USER")))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
        assertThat(data.path("minScore").asDouble()).isEqualTo(0.6);
        assertThat(data.path("topK").asInt()).isEqualTo(5);
        assertThat(data.path("handoffEnabled").asBoolean()).isTrue();
    }

    @Test
    void chunkDetailIsOwnerScoped() throws Exception {
        Library library = seedLibrary(DEMO);

        MvcResult owned = mockMvc.perform(get("/api/chunks/" + library.chunkId()).with(user(DEMO).roles("USER")))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode data = objectMapper.readTree(owned.getResponse().getContentAsString()).path("data");
        assertThat(data.path("content").asText()).isEqualTo(MATERIAL);
        assertThat(data.path("filename").asText()).isEqualTo("售后手册.md");
        assertThat(data.path("chunkIndex").asInt()).isZero();

        mockMvc.perform(get("/api/chunks/" + library.chunkId()).with(user(OTHER).roles("USER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/chunks/99999999").with(user(DEMO).roles("USER")))
                .andExpect(status().isNotFound());
    }

    @Test
    void supportModeWithMaterialUsesStrictPrompt() throws Exception {
        Library library = seedLibrary(DEMO);
        String body = streamBody("{\"content\":\"退款多久到账\",\"module\":\"support\",\"kbId\":"
                + library.kbId() + "}");

        assertThat(body).contains("\"type\":\"citation\"", "\"type\":\"delta\"");
        ArgumentCaptor<AiChatRequest> captor = ArgumentCaptor.forClass(AiChatRequest.class);
        verify(aiGateway).stream(captor.capture());
        String system = captor.getValue().messages().get(0).content();
        assertThat(system).contains("在线客服", "转人工", MATERIAL);
        assertThat(system).doesNotContain("你是「星屿 AI」的助手");
    }

    @Test
    void supportModeWithoutMaterialTellsToHandoff() throws Exception {
        String body = streamBody("{\"content\":\"能开发票吗\",\"module\":\"support\"}");

        assertThat(body).doesNotContain("\"type\":\"citation\"");
        ArgumentCaptor<AiChatRequest> captor = ArgumentCaptor.forClass(AiChatRequest.class);
        verify(aiGateway).stream(captor.capture());
        String system = captor.getValue().messages().get(0).content();
        assertThat(system).contains("没有检索到", "转人工");
        assertThat(system).doesNotContain("参考资料");
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
}

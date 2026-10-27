package com.tutuchacha.ai.analysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tutuchacha.ai.common.BusinessException;
import com.tutuchacha.ai.common.ErrorCode;
import com.tutuchacha.ai.gateway.AiChatRequest;
import com.tutuchacha.ai.gateway.AiChatResponse;
import com.tutuchacha.ai.gateway.AiGateway;
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

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * M3-01：文档分析。测试走同步执行（`app.analysis.async=false`），直接断言 READY / FAILED。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AnalysisTest {

    private static final String DEMO = "analysisdemo";
    private static final String OTHER = "analysisother";
    private static final String CONTENT = "这是一份一年期服务合同。";
    private static final String CHUNK_A = "本合同由星屿科技与乙方签订，服务期为一年。";
    private static final String CHUNK_B = "付款方式为分三期，验收后 15 日内支付。";

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
        when(aiGateway.complete(any(AiChatRequest.class))).thenReturn(
                new AiChatResponse(CONTENT, "qwen-plus", 120, 40, 880));
    }

    @Test
    void summaryRunsAndPersistsMarkdown() throws Exception {
        Fixture fixture = seedDocument(DEMO, "READY", CHUNK_A, CHUNK_B);

        JsonNode data = create(fixture.documentId(), "SUMMARY").path("data");

        assertThat(data.path("status").asText()).isEqualTo("READY");
        assertThat(data.path("type").asText()).isEqualTo("SUMMARY");
        assertThat(data.path("documentId").asLong()).isEqualTo(fixture.documentId());
        assertThat(data.path("kbId").asLong()).isEqualTo(fixture.kbId());
        assertThat(data.path("content").asText()).isEqualTo(CONTENT);
        assertThat(data.path("model").asText()).isEqualTo("qwen-plus");
        assertThat(data.path("promptTokens").asInt()).isEqualTo(120);
        assertThat(data.path("completionTokens").asInt()).isEqualTo(40);
        assertThat(data.path("latencyMs").asInt()).isEqualTo(880);
        assertThat(data.path("errorMsg").asText()).isEmpty();

        ArgumentCaptor<AiChatRequest> captor = ArgumentCaptor.forClass(AiChatRequest.class);
        verify(aiGateway).complete(captor.capture());
        assertThat(captor.getValue().messages().get(0).role()).isEqualTo("system");
        assertThat(captor.getValue().messages().get(0).content()).contains("摘要", "只依据");
        assertThat(captor.getValue().messages().get(1).content())
                .contains("服务合同.txt", CHUNK_A, CHUNK_B);
    }

    @Test
    void eachTypeUsesItsOwnInstructionAndListIsNewestFirst() throws Exception {
        Fixture fixture = seedDocument(DEMO, "READY", CHUNK_A);

        create(fixture.documentId(), "OUTLINE");
        create(fixture.documentId(), "QUESTIONS");
        create(fixture.documentId(), "FIELDS");

        ArgumentCaptor<AiChatRequest> captor = ArgumentCaptor.forClass(AiChatRequest.class);
        verify(aiGateway, org.mockito.Mockito.times(3)).complete(captor.capture());
        assertThat(captor.getAllValues().get(0).messages().get(0).content()).contains("章节结构", "Markdown");
        assertThat(captor.getAllValues().get(1).messages().get(0).content()).contains("追问", "考察点");
        assertThat(captor.getAllValues().get(2).messages().get(0).content()).contains("表格", "原文依据", "未提及");

        mockMvc.perform(get("/api/documents/" + fixture.documentId() + "/analysis")
                        .with(user(DEMO).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].type").value("FIELDS"))
                .andExpect(jsonPath("$.data[1].type").value("QUESTIONS"))
                .andExpect(jsonPath("$.data[2].type").value("OUTLINE"));
    }

    @Test
    void nonReadyDocumentIsRejectedBeforeCreatingTask() throws Exception {
        Fixture fixture = seedDocument(DEMO, "PENDING", CHUNK_A);

        mockMvc.perform(post("/api/documents/" + fixture.documentId() + "/analysis")
                        .with(user(DEMO).roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"SUMMARY\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("READY")));

        assertThat(analysisCount(fixture.documentId())).isZero();
    }

    @Test
    void unknownTypeIsRejectedBeforeCreatingTask() throws Exception {
        Fixture fixture = seedDocument(DEMO, "READY", CHUNK_A);

        mockMvc.perform(post("/api/documents/" + fixture.documentId() + "/analysis")
                        .with(user(DEMO).roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"UNKNOWN\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("SUMMARY")));

        assertThat(analysisCount(fixture.documentId())).isZero();
    }

    @Test
    void otherUsersDocumentIsForbidden() throws Exception {
        Fixture fixture = seedDocument(OTHER, "READY", CHUNK_A);

        mockMvc.perform(post("/api/documents/" + fixture.documentId() + "/analysis")
                        .with(user(DEMO).roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"SUMMARY\"}"))
                .andExpect(status().isForbidden());

        assertThat(analysisCount(fixture.documentId())).isZero();
    }

    @Test
    void missingDocumentIsNotFound() throws Exception {
        mockMvc.perform(post("/api/documents/99999999/analysis")
                        .with(user(DEMO).roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"SUMMARY\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void modelFailureMarksAnalysisFailed() throws Exception {
        Fixture fixture = seedDocument(DEMO, "READY", CHUNK_A);
        when(aiGateway.complete(any(AiChatRequest.class)))
                .thenThrow(new BusinessException(ErrorCode.BAD_GATEWAY, "上游模型调用失败"));

        JsonNode data = create(fixture.documentId(), "SUMMARY").path("data");

        assertThat(data.path("status").asText()).isEqualTo("FAILED");
        assertThat(data.path("content").asText()).isEmpty();
        assertThat(data.path("errorMsg").asText()).contains("上游模型调用失败");
    }

    @Test
    void documentWithoutChunksFailsWithHint() throws Exception {
        Fixture fixture = seedDocument(DEMO, "READY");

        JsonNode data = create(fixture.documentId(), "OUTLINE").path("data");

        assertThat(data.path("status").asText()).isEqualTo("FAILED");
        assertThat(data.path("errorMsg").asText()).contains("没有可分析的文本");
    }

    @Test
    void analysisCanBeFetchedAndDeleted() throws Exception {
        Fixture fixture = seedDocument(DEMO, "READY", CHUNK_A);
        long id = create(fixture.documentId(), "SUMMARY").path("data").path("id").asLong();

        mockMvc.perform(get("/api/analysis/" + id).with(user(DEMO).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("READY"))
                .andExpect(jsonPath("$.data.content").value(CONTENT));

        mockMvc.perform(delete("/api/analysis/" + id).with(user(DEMO).roles("USER")))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/analysis/" + id).with(user(DEMO).roles("USER")))
                .andExpect(status().isNotFound());
        assertThat(analysisCount(fixture.documentId())).isZero();
    }

    @Test
    void otherUsersAnalysisIsForbidden() throws Exception {
        Fixture fixture = seedDocument(OTHER, "READY", CHUNK_A);
        long id = createAs(OTHER, fixture.documentId(), "SUMMARY").path("data").path("id").asLong();

        mockMvc.perform(get("/api/analysis/" + id).with(user(DEMO).roles("USER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/analysis/" + id).with(user(DEMO).roles("USER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    private record Fixture(long kbId, long documentId) {
    }

    private JsonNode create(long documentId, String type) throws Exception {
        return createAs(DEMO, documentId, type);
    }

    private JsonNode createAs(String username, long documentId, String type) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/documents/" + documentId + "/analysis")
                        .with(user(username).roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"" + type + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
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

    private Fixture seedDocument(String username, String status, String... chunks) {
        jdbc.update("insert into knowledge_base(user_id, name, chunk_size, chunk_overlap) values(?, ?, 200, 20)",
                userId(username), "AN-" + System.nanoTime());
        Long kbId = jdbc.queryForObject(
                "select max(id) from knowledge_base where user_id = ?", Long.class, userId(username));
        jdbc.update("insert into document(kb_id, filename, ext, size, sha256, status, chunk_count) "
                        + "values(?, '服务合同.txt', 'txt', 100, ?, ?, ?)",
                kbId, "sha-" + System.nanoTime(), status, chunks.length);
        Long documentId = jdbc.queryForObject(
                "select max(id) from document where kb_id = ?", Long.class, kbId);
        for (int i = 0; i < chunks.length; i += 1) {
            jdbc.update("insert into document_chunk(document_id, kb_id, chunk_index, content, token_count) "
                            + "values(?, ?, ?, ?, ?)",
                    documentId, kbId, i, chunks[i], chunks[i].length());
        }
        return new Fixture(kbId == null ? 0L : kbId, documentId == null ? 0L : documentId);
    }

    private int analysisCount(long documentId) {
        Integer count = jdbc.queryForObject(
                "select count(*) from document_analysis where document_id = ?", Integer.class, documentId);
        return count == null ? 0 : count;
    }
}

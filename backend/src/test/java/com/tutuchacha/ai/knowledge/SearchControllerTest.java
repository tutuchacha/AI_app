package com.tutuchacha.ai.knowledge;

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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 检索：查询向量由 AiGateway 桩固定为 [1,0,0]，切片向量直接写库，便于精确断言排序与阈值。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SearchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AiGateway aiGateway;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void seedUsers() {
        seedUser("demo");
        seedUser("other");
        when(aiGateway.embed(anyList())).thenReturn(List.of(new float[]{1f, 0f, 0f}));
    }

    private void seedUser(String username) {
        Integer count = jdbc.queryForObject(
                "select count(*) from sys_user where username = ?", Integer.class, username);
        if (count == null || count == 0) {
            jdbc.update("insert into sys_user(username, password_hash, nickname, role) values(?, 'x', ?, 'USER')",
                    username, username);
        }
    }

    private long createKnowledgeBase(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/knowledge-bases")
                        .with(user(username).roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"检索测试-" + System.nanoTime() + "\",\"chunkSize\":200,\"chunkOverlap\":10}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("id").asLong();
    }

    private long createDocument(long kbId, String filename) {
        jdbc.update("insert into document(kb_id, filename, ext, size, sha256, status, chunk_count) "
                + "values(?, ?, 'txt', 100, ?, 'READY', 0)", kbId, filename, "sha-" + System.nanoTime());
        Long id = jdbc.queryForObject("select max(id) from document where kb_id = ?", Long.class, kbId);
        return id == null ? 0L : id;
    }

    private void seedChunk(long kbId, long documentId, int index, String content, String embeddingJson) {
        jdbc.update("insert into document_chunk(document_id, kb_id, chunk_index, content, embedding, "
                        + "token_count, metadata_json) values(?, ?, ?, ?, ?, ?, '{}')",
                documentId, kbId, index, content, embeddingJson, content.length());
    }

    private JsonNode search(String username, long kbId, String body) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/knowledge-bases/" + kbId + "/search")
                        .with(user(username).roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    @Test
    void searchOrdersByScoreAndCapsTopK() throws Exception {
        long kbId = createKnowledgeBase("demo");
        long documentId = createDocument(kbId, "手册.txt");
        seedChunk(kbId, documentId, 0, "最相关", "[1.0,0.0,0.0]");
        seedChunk(kbId, documentId, 1, "次相关", "[0.9,0.1,0.0]");
        seedChunk(kbId, documentId, 2, "无关", "[0.0,1.0,0.0]");

        JsonNode data = search("demo", kbId, "{\"query\":\"怎么请假\",\"topK\":2}").path("data");

        assertThat(data).hasSize(2);
        assertThat(data.get(0).path("content").asText()).isEqualTo("最相关");
        assertThat(data.get(0).path("score").asDouble()).isCloseTo(1d, within(1e-6));
        assertThat(data.get(0).path("filename").asText()).isEqualTo("手册.txt");
        assertThat(data.get(0).path("documentId").asLong()).isEqualTo(documentId);
        assertThat(data.get(0).path("chunkIndex").asInt()).isZero();
        assertThat(data.get(1).path("content").asText()).isEqualTo("次相关");
        assertThat(data.get(1).path("score").asDouble()).isGreaterThan(0.99d);
    }

    @Test
    void minScoreFiltersWeakMatches() throws Exception {
        long kbId = createKnowledgeBase("demo");
        long documentId = createDocument(kbId, "手册.txt");
        seedChunk(kbId, documentId, 0, "高度相关", "[1.0,0.0,0.0]");
        seedChunk(kbId, documentId, 1, "勉强相关", "[0.6,0.8,0.0]");

        JsonNode data = search("demo", kbId, "{\"query\":\"年假\",\"minScore\":0.9}").path("data");

        assertThat(data).hasSize(1);
        assertThat(data.get(0).path("content").asText()).isEqualTo("高度相关");
    }

    @Test
    void emptyLibraryReturnsEmptyArray() throws Exception {
        long kbId = createKnowledgeBase("demo");

        mockMvc.perform(post("/api/knowledge-bases/" + kbId + "/search")
                        .with(user("demo").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"没有资料\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void ignoresNullAndMismatchedEmbeddings() throws Exception {
        long kbId = createKnowledgeBase("demo");
        long documentId = createDocument(kbId, "手册.txt");
        seedChunk(kbId, documentId, 0, "无向量", null);
        seedChunk(kbId, documentId, 1, "维度不符", "[1.0,0.0]");
        seedChunk(kbId, documentId, 2, "正常", "[1.0,0.0,0.0]");

        JsonNode data = search("demo", kbId, "{\"query\":\"测试\"}").path("data");

        assertThat(data).hasSize(1);
        assertThat(data.get(0).path("content").asText()).isEqualTo("正常");
    }

    @Test
    void defaultTopKIsSix() throws Exception {
        long kbId = createKnowledgeBase("demo");
        long documentId = createDocument(kbId, "手册.txt");
        for (int i = 0; i < 8; i += 1) {
            seedChunk(kbId, documentId, i, "第 " + i + " 片", "[1.0,0.0,0.0]");
        }

        JsonNode data = search("demo", kbId, "{\"query\":\"测试\"}").path("data");

        assertThat(data).hasSize(6);
    }

    @Test
    void doesNotLeakChunksFromAnotherKnowledgeBase() throws Exception {
        long kbA = createKnowledgeBase("demo");
        long kbB = createKnowledgeBase("demo");
        long documentId = createDocument(kbB, "别的库.txt");
        seedChunk(kbB, documentId, 0, "别的库内容", "[1.0,0.0,0.0]");

        JsonNode data = search("demo", kbA, "{\"query\":\"测试\"}").path("data");

        assertThat(data).isEmpty();
    }

    @Test
    void blankQueryIsRejected() throws Exception {
        long kbId = createKnowledgeBase("demo");

        mockMvc.perform(post("/api/knowledge-bases/" + kbId + "/search")
                        .with(user("demo").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void topKOutOfRangeIsRejected() throws Exception {
        long kbId = createKnowledgeBase("demo");

        mockMvc.perform(post("/api/knowledge-bases/" + kbId + "/search")
                        .with(user("demo").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"测试\",\"topK\":21}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void otherUsersKnowledgeBaseIsForbidden() throws Exception {
        long kbId = createKnowledgeBase("other");

        mockMvc.perform(post("/api/knowledge-bases/" + kbId + "/search")
                        .with(user("demo").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"测试\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void missingKnowledgeBaseReturns404() throws Exception {
        mockMvc.perform(post("/api/knowledge-bases/99999999/search")
                        .with(user("demo").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"测试\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    void requiresLogin() throws Exception {
        mockMvc.perform(post("/api/knowledge-bases/1/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"测试\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }
}

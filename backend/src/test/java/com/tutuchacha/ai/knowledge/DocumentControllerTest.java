package com.tutuchacha.ai.knowledge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 上传与解析：测试配置关掉异步（app.rag.async=false），上传返回时已是最终状态。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DocumentControllerTest {

    private static final String LONG_TEXT = "聘聊是一款招聘产品。".repeat(60);

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

    private long createKnowledgeBase(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/knowledge-bases")
                        .with(user(username).roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"上传测试-" + System.nanoTime()
                                + "\",\"chunkSize\":100,\"chunkOverlap\":10}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("id").asLong();
    }

    private MvcResult upload(String username, long kbId, String filename, byte[] content) throws Exception {
        return mockMvc.perform(multipart("/api/knowledge-bases/{kbId}/documents", kbId)
                        .file(new MockMultipartFile("file", filename, MediaType.APPLICATION_OCTET_STREAM_VALUE, content))
                        .with(user(username).roles("USER")))
                .andReturn();
    }

    private long documentId(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("id").asLong();
    }

    private int chunkRows(long documentId) {
        Integer count = jdbc.queryForObject(
                "select count(*) from document_chunk where document_id = ?", Integer.class, documentId);
        return count == null ? 0 : count;
    }

    @Test
    void uploadTxtParsesAndChunks() throws Exception {
        long kbId = createKnowledgeBase("demo");

        MvcResult result = upload("demo", kbId, "手册.txt", LONG_TEXT.getBytes(StandardCharsets.UTF_8));

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
        assertThat(data.path("status").asText()).isEqualTo("READY");
        assertThat(data.path("chunkCount").asInt()).isGreaterThan(1);
        assertThat(data.path("filename").asText()).isEqualTo("手册.txt");
        assertThat(data.path("ext").asText()).isEqualTo("txt");

        long documentId = data.path("id").asLong();
        assertThat(chunkRows(documentId)).isEqualTo(data.path("chunkCount").asInt());
        String firstChunk = jdbc.queryForObject(
                "select content from document_chunk where document_id = ? order by chunk_index limit 1",
                String.class, documentId);
        assertThat(firstChunk).contains("聘聊");

        mockMvc.perform(get("/api/knowledge-bases/" + kbId).with(user("demo").roles("USER")))
                .andExpect(jsonPath("$.data.documentCount").value(1));
    }

    @Test
    void duplicateContentIsReusedAndNotChunkedTwice() throws Exception {
        long kbId = createKnowledgeBase("demo");
        byte[] content = LONG_TEXT.getBytes(StandardCharsets.UTF_8);

        long first = documentId(upload("demo", kbId, "a.txt", content));
        long second = documentId(upload("demo", kbId, "b.txt", content));

        assertThat(second).isEqualTo(first);
        assertThat(chunkRows(first)).isGreaterThan(1);
        mockMvc.perform(get("/api/knowledge-bases/" + kbId).with(user("demo").roles("USER")))
                .andExpect(jsonPath("$.data.documentCount").value(1));
    }

    @Test
    void rejectsUnsupportedExtension() throws Exception {
        long kbId = createKnowledgeBase("demo");
        MvcResult result = upload("demo", kbId, "data.csv", "a,b".getBytes(StandardCharsets.UTF_8));

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(objectMapper.readTree(result.getResponse().getContentAsString()).path("code").asInt())
                .isEqualTo(400);
    }

    @Test
    void rejectsOversizeFile() throws Exception {
        long kbId = createKnowledgeBase("demo");
        byte[] huge = new byte[1200 * 1024];
        java.util.Arrays.fill(huge, (byte) 'a');

        MvcResult result = upload("demo", kbId, "big.txt", huge);

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(objectMapper.readTree(result.getResponse().getContentAsString()).path("message").asText())
                .contains("MB");
        mockMvc.perform(get("/api/knowledge-bases/" + kbId).with(user("demo").roles("USER")))
                .andExpect(jsonPath("$.data.documentCount").value(0));
    }

    @Test
    void uploadToOtherUsersKnowledgeBaseIsForbidden() throws Exception {
        long kbId = createKnowledgeBase("other");
        MvcResult result = upload("demo", kbId, "x.txt", "hello".getBytes(StandardCharsets.UTF_8));

        assertThat(result.getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    void brokenFileEndsUpFailedWithMessage() throws Exception {
        long kbId = createKnowledgeBase("demo");
        MvcResult result = upload("demo", kbId, "broken.pdf", new byte[]{1, 2, 3, 4, 5, 6, 7, 8});

        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
        assertThat(data.path("status").asText()).isEqualTo("FAILED");
        assertThat(data.path("errorMsg").asText()).isNotBlank();
    }

    @Test
    void deleteRemovesDocumentChunksAndCount() throws Exception {
        long kbId = createKnowledgeBase("demo");
        long documentId = documentId(upload("demo", kbId, "d.txt", LONG_TEXT.getBytes(StandardCharsets.UTF_8)));

        mockMvc.perform(delete("/api/documents/" + documentId).with(user("demo").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(get("/api/documents/" + documentId).with(user("demo").roles("USER")))
                .andExpect(status().isNotFound());
        assertThat(chunkRows(documentId)).isZero();
        mockMvc.perform(get("/api/knowledge-bases/" + kbId).with(user("demo").roles("USER")))
                .andExpect(jsonPath("$.data.documentCount").value(0));
    }

    @Test
    void reindexRebuildsChunksWithoutDuplicates() throws Exception {
        long kbId = createKnowledgeBase("demo");
        long documentId = documentId(upload("demo", kbId, "r.txt", LONG_TEXT.getBytes(StandardCharsets.UTF_8)));
        int before = chunkRows(documentId);

        mockMvc.perform(post("/api/documents/" + documentId + "/reindex").with(user("demo").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("READY"));

        assertThat(before).isGreaterThan(1);
        assertThat(chunkRows(documentId)).isEqualTo(before);
    }

    @Test
    void listReturnsOwnDocuments() throws Exception {
        long kbId = createKnowledgeBase("demo");
        upload("demo", kbId, "list.txt", LONG_TEXT.getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(get("/api/knowledge-bases/" + kbId + "/documents").with(user("demo").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].status").value("READY"));
    }

    @Test
    void requiresLogin() throws Exception {
        mockMvc.perform(multipart("/api/knowledge-bases/{kbId}/documents", 1L)
                        .file(new MockMultipartFile("file", "a.txt", MediaType.TEXT_PLAIN_VALUE, "x".getBytes())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }
}

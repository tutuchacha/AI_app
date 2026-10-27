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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class KnowledgeBaseControllerTest {

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

    private long userId(String username) {
        Long id = jdbc.queryForObject("select id from sys_user where username = ?", Long.class, username);
        return id == null ? 0L : id;
    }

    private String uniqueName(String tag) {
        return tag + "-" + System.nanoTime();
    }

    private long createKb(String username, String body) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/knowledge-bases")
                        .with(user(username).roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("id").asLong();
    }

    private long insertKb(String username, String name) {
        jdbc.update("insert into knowledge_base(user_id, name, description) values(?, ?, '')",
                userId(username), name);
        Long id = jdbc.queryForObject("select id from knowledge_base where name = ?", Long.class, name);
        return id == null ? 0L : id;
    }

    @Test
    void createsWithDefaultsAndListsOwnKnowledgeBases() throws Exception {
        String name = uniqueName("手册");
        MvcResult created = mockMvc.perform(post("/api/knowledge-bases")
                        .with(user("demo").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.name").value(name))
                .andExpect(jsonPath("$.data.chunkSize").value(800))
                .andExpect(jsonPath("$.data.chunkOverlap").value(120))
                .andExpect(jsonPath("$.data.embeddingModel").value("text-embedding-v3"))
                .andExpect(jsonPath("$.data.documentCount").value(0))
                .andReturn();

        long id = objectMapper.readTree(created.getResponse().getContentAsString()).path("data").path("id").asLong();
        assertThat(id).isPositive();

        MvcResult list = mockMvc.perform(get("/api/knowledge-bases").with(user("demo").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        JsonNode data = objectMapper.readTree(list.getResponse().getContentAsString()).path("data");
        assertThat(data.findValuesAsText("name")).contains(name);
    }

    @Test
    void listExcludesOtherUsersKnowledgeBases() throws Exception {
        String mine = uniqueName("我的");
        String others = uniqueName("别人的");
        insertKb("demo", mine);
        insertKb("other", others);

        MvcResult result = mockMvc.perform(get("/api/knowledge-bases").with(user("demo").roles("USER")))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
        assertThat(data.findValuesAsText("name")).contains(mine).doesNotContain(others);
    }

    @Test
    void rejectsBlankName() throws Exception {
        mockMvc.perform(post("/api/knowledge-bases")
                        .with(user("demo").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void rejectsOverlapNotSmallerThanChunkSize() throws Exception {
        mockMvc.perform(post("/api/knowledge-bases")
                        .with(user("demo").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"bad\",\"chunkSize\":100,\"chunkOverlap\":100}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void detailOfOtherUserIsForbidden() throws Exception {
        long id = insertKb("other", uniqueName("别人的"));
        mockMvc.perform(get("/api/knowledge-bases/" + id).with(user("demo").roles("USER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void detailOfMissingKnowledgeBaseIsNotFound() throws Exception {
        mockMvc.perform(get("/api/knowledge-bases/999999").with(user("demo").roles("USER")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    void updateOverwritesFields() throws Exception {
        long id = createKb("demo", "{\"name\":\"" + uniqueName("旧名") + "\"}");
        String newName = uniqueName("新名");

        mockMvc.perform(put("/api/knowledge-bases/" + id)
                        .with(user("demo").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + newName + "\",\"description\":\"客服话术\","
                                + "\"chunkSize\":500,\"chunkOverlap\":50}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value(newName))
                .andExpect(jsonPath("$.data.description").value("客服话术"))
                .andExpect(jsonPath("$.data.chunkSize").value(500))
                .andExpect(jsonPath("$.data.chunkOverlap").value(50));
    }

    @Test
    void deleteRemovesKnowledgeBase() throws Exception {
        long id = insertKb("demo", uniqueName("待删"));

        mockMvc.perform(delete("/api/knowledge-bases/" + id).with(user("demo").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(get("/api/knowledge-bases/" + id).with(user("demo").roles("USER")))
                .andExpect(status().isNotFound());
    }

    @Test
    void deletingOtherUsersKnowledgeBaseIsForbidden() throws Exception {
        long id = insertKb("other", uniqueName("别人的"));
        mockMvc.perform(delete("/api/knowledge-bases/" + id).with(user("demo").roles("USER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void requiresLogin() throws Exception {
        mockMvc.perform(get("/api/knowledge-bases"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }
}

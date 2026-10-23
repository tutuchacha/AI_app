package com.tutuchacha.ai.common;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ApiErrorResponseTest.ValidationEndpoint.class)
class ApiErrorResponseTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void unknownApiReturnsUnifiedJson404() throws Exception {
        mockMvc.perform(get("/api/not-exists"))
                .andExpect(status().isNotFound())
                .andExpect(header().exists(TraceIdFilter.HEADER))
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void invalidBodyReturns400WithFieldMessage() throws Exception {
        mockMvc.perform(post("/api/_test/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("name")));
    }

    @Test
    void unauthenticatedNonApiPathReturnsJson401() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void incomingTraceIdIsEchoed() throws Exception {
        mockMvc.perform(get("/api/not-exists").header(TraceIdFilter.HEADER, "trace-from-client"))
                .andExpect(header().string(TraceIdFilter.HEADER, "trace-from-client"))
                .andExpect(jsonPath("$.traceId").value("trace-from-client"));
    }

    @RestController
    static class ValidationEndpoint {

        @PostMapping("/api/_test/validate")
        Result<String> validate(@Valid @RequestBody DemoRequest request) {
            return Result.ok(request.name());
        }
    }

    record DemoRequest(@NotBlank(message = "不能为空") String name) {
    }
}

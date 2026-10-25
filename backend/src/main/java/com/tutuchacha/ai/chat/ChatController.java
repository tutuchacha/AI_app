package com.tutuchacha.ai.chat;

import com.tutuchacha.ai.chat.dto.ChatEvent;
import com.tutuchacha.ai.chat.dto.ChatRequest;
import com.tutuchacha.ai.chat.dto.ChatResult;
import com.tutuchacha.ai.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@Tag(name = "聊天", description = "非流式 /api/chat 与 SSE 流式 /api/chat/stream")
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatOrchestrator orchestrator;

    public ChatController(ChatOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @Operation(summary = "非流式对话", security = @SecurityRequirement(name = "bearerAuth"))
    @PostMapping
    public Result<ChatResult> chat(@Valid @RequestBody ChatRequest request, Authentication authentication) {
        return Result.ok(orchestrator.chat(authentication.getName(), request));
    }

    @Operation(summary = "流式对话（SSE）",
            description = "事件顺序：meta → delta* → usage → done；失败时为 meta → error。断开连接即停止生成。",
            security = @SecurityRequirement(name = "bearerAuth"))
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ChatEvent> stream(@Valid @RequestBody ChatRequest request,
                                  Authentication authentication,
                                  HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-cache");
        response.setHeader("X-Accel-Buffering", "no");
        return orchestrator.stream(authentication.getName(), request);
    }
}

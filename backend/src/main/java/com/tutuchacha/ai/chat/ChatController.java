package com.tutuchacha.ai.chat;

import com.tutuchacha.ai.chat.dto.ChatRequest;
import com.tutuchacha.ai.chat.dto.ChatResult;
import com.tutuchacha.ai.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "聊天", description = "M1：非流式对话；SSE 流式见 /api/chat/stream")
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
}

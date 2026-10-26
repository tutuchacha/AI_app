package com.tutuchacha.ai.chat;

import com.tutuchacha.ai.chat.dto.ConversationDetail;
import com.tutuchacha.ai.chat.dto.ConversationSummary;
import com.tutuchacha.ai.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 会话列表与历史消息：刷新页面、点左侧列表时恢复聊天。 */
@Tag(name = "会话", description = "会话列表与历史消息")
@RestController
@RequestMapping("/api/conversations")
public class ConversationController {

    private final ConversationService conversationService;

    public ConversationController(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    @Operation(summary = "会话列表", description = "按最后消息时间倒序，最多 50 条",
            security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping
    public Result<List<ConversationSummary>> list(@RequestParam(required = false) String module,
                                                  Authentication authentication) {
        return Result.ok(conversationService.listSummaries(authentication.getName(), module));
    }

    @Operation(summary = "会话详情", description = "按 id 正序返回 DONE/STOPPED 的消息",
            security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/{uid}")
    public Result<ConversationDetail> detail(@PathVariable String uid, Authentication authentication) {
        return Result.ok(conversationService.detail(authentication.getName(), uid));
    }
}

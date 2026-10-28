package com.tutuchacha.ai.support;

import com.tutuchacha.ai.common.Result;
import com.tutuchacha.ai.support.dto.SupportTicketRequest;
import com.tutuchacha.ai.support.dto.SupportTicketView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** M4-04 转人工工单：兜底卡片点「转人工」时提交，列表展示最近 20 条。 */
@Tag(name = "客服工单", description = "转人工工单提交、列表与关闭")
@RestController
@RequestMapping("/api/support/tickets")
public class SupportTicketController {

    private final SupportTicketService ticketService;

    public SupportTicketController(SupportTicketService ticketService) {
        this.ticketService = ticketService;
    }

    @Operation(summary = "提交转人工工单", description = "带上触发兜底时的最高引用分与会话 UID",
            security = @SecurityRequirement(name = "bearerAuth"))
    @PostMapping
    public Result<SupportTicketView> create(@Valid @RequestBody SupportTicketRequest request,
                                           Authentication authentication) {
        return Result.ok(ticketService.create(authentication.getName(), request));
    }

    @Operation(summary = "我的工单", description = "按创建时间倒序，最多 20 条",
            security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping
    public Result<List<SupportTicketView>> list(Authentication authentication) {
        return Result.ok(ticketService.list(authentication.getName()));
    }

    @Operation(summary = "关闭工单", description = "OPEN → CLOSED，幂等",
            security = @SecurityRequirement(name = "bearerAuth"))
    @PostMapping("/{id}/close")
    public Result<SupportTicketView> close(@PathVariable Long id, Authentication authentication) {
        return Result.ok(ticketService.close(authentication.getName(), id));
    }
}

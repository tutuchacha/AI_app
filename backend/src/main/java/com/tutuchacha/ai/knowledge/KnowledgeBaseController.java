package com.tutuchacha.ai.knowledge;

import com.tutuchacha.ai.common.Result;
import com.tutuchacha.ai.knowledge.dto.KnowledgeBaseRequest;
import com.tutuchacha.ai.knowledge.dto.KnowledgeBaseView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 知识库管理：M2 文档上传与检索都以库为入口。 */
@Tag(name = "知识库", description = "知识库增删改查")
@RestController
@RequestMapping("/api/knowledge-bases")
public class KnowledgeBaseController {

    private final KnowledgeBaseService knowledgeBaseService;

    public KnowledgeBaseController(KnowledgeBaseService knowledgeBaseService) {
        this.knowledgeBaseService = knowledgeBaseService;
    }

    @Operation(summary = "知识库列表", description = "只返回当前用户的库，按 id 倒序，最多 50 个",
            security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping
    public Result<List<KnowledgeBaseView>> list(Authentication authentication) {
        return Result.ok(knowledgeBaseService.list(authentication.getName()));
    }

    @Operation(summary = "知识库详情", security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/{id}")
    public Result<KnowledgeBaseView> detail(@PathVariable Long id, Authentication authentication) {
        return Result.ok(knowledgeBaseService.detail(authentication.getName(), id));
    }

    @Operation(summary = "新建知识库", description = "name 必填；切片参数留空用默认 800/120",
            security = @SecurityRequirement(name = "bearerAuth"))
    @PostMapping
    public Result<KnowledgeBaseView> create(@Valid @RequestBody KnowledgeBaseRequest request,
                                            Authentication authentication) {
        return Result.ok(knowledgeBaseService.create(authentication.getName(), request));
    }

    @Operation(summary = "更新知识库", description = "全量覆盖，未传字段回落默认值",
            security = @SecurityRequirement(name = "bearerAuth"))
    @PutMapping("/{id}")
    public Result<KnowledgeBaseView> update(@PathVariable Long id,
                                            @Valid @RequestBody KnowledgeBaseRequest request,
                                            Authentication authentication) {
        return Result.ok(knowledgeBaseService.update(authentication.getName(), id, request));
    }

    @Operation(summary = "删除知识库", description = "级联删除库下文档与切片",
            security = @SecurityRequirement(name = "bearerAuth"))
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id, Authentication authentication) {
        knowledgeBaseService.delete(authentication.getName(), id);
        return Result.ok();
    }
}

package com.tutuchacha.ai.analysis;

import com.tutuchacha.ai.analysis.dto.AnalysisRequest;
import com.tutuchacha.ai.analysis.dto.AnalysisView;
import com.tutuchacha.ai.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 文档分析：创建任务、轮询结果、删除记录。 */
@Tag(name = "文档分析", description = "摘要 / 结构抽取 / 追问")
@RestController
public class AnalysisController {

    private final AnalysisService analysisService;

    public AnalysisController(AnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    @Operation(summary = "创建分析任务", description = "文档必须 READY；立即返回 PENDING，后台调用模型",
            security = @SecurityRequirement(name = "bearerAuth"))
    @PostMapping("/api/documents/{documentId}/analysis")
    public Result<AnalysisView> create(@PathVariable Long documentId,
                                       @Valid @RequestBody AnalysisRequest request,
                                       Authentication authentication) {
        return Result.ok(analysisService.create(authentication.getName(), documentId, request));
    }

    @Operation(summary = "文档分析记录", description = "最新在前，最多 20 条",
            security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/api/documents/{documentId}/analysis")
    public Result<List<AnalysisView>> list(@PathVariable Long documentId, Authentication authentication) {
        return Result.ok(analysisService.list(authentication.getName(), documentId));
    }

    @Operation(summary = "分析任务详情", description = "前端按 status 轮询到 READY / FAILED",
            security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/api/analysis/{id}")
    public Result<AnalysisView> detail(@PathVariable Long id, Authentication authentication) {
        return Result.ok(analysisService.detail(authentication.getName(), id));
    }

    @Operation(summary = "删除分析记录", security = @SecurityRequirement(name = "bearerAuth"))
    @DeleteMapping("/api/analysis/{id}")
    public Result<Void> delete(@PathVariable Long id, Authentication authentication) {
        analysisService.delete(authentication.getName(), id);
        return Result.ok();
    }
}

package com.tutuchacha.ai.knowledge;

import com.tutuchacha.ai.common.Result;
import com.tutuchacha.ai.knowledge.dto.DocumentView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/** 文档上传、状态查询、删除与重建索引。 */
@Tag(name = "知识库文档", description = "文档上传解析与生命周期")
@RestController
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @Operation(summary = "上传文档", description = "pdf/docx/txt/md，≤20MB；立即返回 PENDING，后台解析",
            security = @SecurityRequirement(name = "bearerAuth"))
    @PostMapping(value = "/api/knowledge-bases/{kbId}/documents",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<DocumentView> upload(@PathVariable Long kbId,
                                       @RequestParam("file") MultipartFile file,
                                       Authentication authentication) {
        return Result.ok(documentService.upload(authentication.getName(), kbId, file));
    }

    @Operation(summary = "文档列表", security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/api/knowledge-bases/{kbId}/documents")
    public Result<List<DocumentView>> list(@PathVariable Long kbId, Authentication authentication) {
        return Result.ok(documentService.list(authentication.getName(), kbId));
    }

    @Operation(summary = "文档详情", description = "status: PENDING/PARSING/READY/FAILED",
            security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/api/documents/{id}")
    public Result<DocumentView> detail(@PathVariable Long id, Authentication authentication) {
        return Result.ok(documentService.detail(authentication.getName(), id));
    }

    @Operation(summary = "删除文档", description = "同时删除切片与源文件",
            security = @SecurityRequirement(name = "bearerAuth"))
    @DeleteMapping("/api/documents/{id}")
    public Result<Void> delete(@PathVariable Long id, Authentication authentication) {
        documentService.delete(authentication.getName(), id);
        return Result.ok();
    }

    @Operation(summary = "重建索引", description = "清空旧切片后重新解析",
            security = @SecurityRequirement(name = "bearerAuth"))
    @PostMapping("/api/documents/{id}/reindex")
    public Result<DocumentView> reindex(@PathVariable Long id, Authentication authentication) {
        return Result.ok(documentService.reindex(authentication.getName(), id));
    }
}

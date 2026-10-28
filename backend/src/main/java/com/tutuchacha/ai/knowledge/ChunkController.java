package com.tutuchacha.ai.knowledge;

import com.tutuchacha.ai.common.BusinessException;
import com.tutuchacha.ai.common.ErrorCode;
import com.tutuchacha.ai.common.Result;
import com.tutuchacha.ai.knowledge.dto.ChunkView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * M4-02 引用溯源：前端点引用卡片时按切片 ID 取完整原文。
 * 只返回自己库里的切片，别人的一律 403。
 */
@Tag(name = "知识库切片", description = "按切片 ID 取原文，用于引用溯源")
@RestController
public class ChunkController {

    private final DocumentChunkMapper chunkMapper;
    private final KbDocumentMapper documentMapper;
    private final KnowledgeBaseService knowledgeBaseService;

    public ChunkController(DocumentChunkMapper chunkMapper, KbDocumentMapper documentMapper,
                           KnowledgeBaseService knowledgeBaseService) {
        this.chunkMapper = chunkMapper;
        this.documentMapper = documentMapper;
        this.knowledgeBaseService = knowledgeBaseService;
    }

    @Operation(summary = "切片原文", description = "客服/对话引用点击时取完整片段；仅限本人知识库",
            security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/api/chunks/{id}")
    public Result<ChunkView> detail(@PathVariable Long id, Authentication authentication) {
        DocumentChunk chunk = chunkMapper.selectById(id);
        if (chunk == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "切片不存在");
        }
        knowledgeBaseService.requireOwned(authentication.getName(), chunk.getKbId());
        KbDocument document = chunk.getDocumentId() == null
                ? null : documentMapper.selectById(chunk.getDocumentId());
        return Result.ok(new ChunkView(chunk.getId(), chunk.getDocumentId(), chunk.getKbId(),
                document == null ? "" : document.getFilename(), chunk.getChunkIndex(),
                chunk.getTokenCount(), chunk.getContent()));
    }
}

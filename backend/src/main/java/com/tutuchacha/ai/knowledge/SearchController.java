package com.tutuchacha.ai.knowledge;

import com.tutuchacha.ai.common.Result;
import com.tutuchacha.ai.knowledge.dto.SearchHit;
import com.tutuchacha.ai.knowledge.dto.SearchRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 知识库检索调试入口：M2-05 聊天接 RAG 复用同一个 service。 */
@Tag(name = "知识库检索", description = "按问题检索库内切片")
@RestController
public class SearchController {

    private final RetrievalService retrievalService;

    public SearchController(RetrievalService retrievalService) {
        this.retrievalService = retrievalService;
    }

    @Operation(summary = "检索切片", description = "余弦相似度排序，默认 topK=6、阈值 0.2",
            security = @SecurityRequirement(name = "bearerAuth"))
    @PostMapping("/api/knowledge-bases/{id}/search")
    public Result<List<SearchHit>> search(@PathVariable Long id,
                                          @Valid @RequestBody SearchRequest request,
                                          Authentication authentication) {
        return Result.ok(retrievalService.search(authentication.getName(), id, request));
    }
}

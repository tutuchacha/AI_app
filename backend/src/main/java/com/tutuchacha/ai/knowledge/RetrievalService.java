package com.tutuchacha.ai.knowledge;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tutuchacha.ai.common.BusinessException;
import com.tutuchacha.ai.common.ErrorCode;
import com.tutuchacha.ai.gateway.AiGateway;
import com.tutuchacha.ai.knowledge.dto.SearchHit;
import com.tutuchacha.ai.knowledge.dto.SearchRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 向量检索：问题向量化 → 取库内切片 → 余弦相似度 → 阈值过滤 → 截断 topK。
 * 规模原型阶段在 Java 侧算分；切片量上来再换向量库（M6）。
 */
@Service
public class RetrievalService {

    private static final Logger log = LoggerFactory.getLogger(RetrievalService.class);

    /** 单次检索最多加载的切片数，超过只取最早的一批，防止大库拖垮请求。 */
    private static final int MAX_CANDIDATES = 5000;

    private final KnowledgeBaseService knowledgeBaseService;
    private final DocumentChunkMapper chunkMapper;
    private final KbDocumentMapper documentMapper;
    private final AiGateway aiGateway;
    private final RagProperties properties;
    private final ObjectMapper objectMapper;

    public RetrievalService(KnowledgeBaseService knowledgeBaseService,
                            DocumentChunkMapper chunkMapper,
                            KbDocumentMapper documentMapper,
                            AiGateway aiGateway,
                            RagProperties properties,
                            ObjectMapper objectMapper) {
        this.knowledgeBaseService = knowledgeBaseService;
        this.chunkMapper = chunkMapper;
        this.documentMapper = documentMapper;
        this.aiGateway = aiGateway;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public List<SearchHit> search(String username, Long knowledgeBaseId, SearchRequest request) {
        knowledgeBaseService.requireOwned(username, knowledgeBaseId);

        String query = request.query() == null ? "" : request.query().trim();
        if (query.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "检索内容不能为空");
        }
        int topK = request.topK() == null ? properties.topKOrDefault() : request.topK();
        double minScore = request.minScore() == null ? properties.minScoreOrDefault() : request.minScore();

        float[] queryVector = embedQuery(query);

        List<DocumentChunk> chunks = chunkMapper.selectList(Wrappers.<DocumentChunk>lambdaQuery()
                .eq(DocumentChunk::getKbId, knowledgeBaseId)
                .isNotNull(DocumentChunk::getEmbedding)
                .orderByAsc(DocumentChunk::getId)
                .last("LIMIT " + MAX_CANDIDATES));
        if (chunks.isEmpty()) {
            return List.of();
        }

        List<Scored> scored = new ArrayList<>();
        for (DocumentChunk chunk : chunks) {
            float[] vector = parseEmbedding(chunk.getEmbedding());
            if (vector == null || vector.length != queryVector.length) {
                continue;
            }
            double score = CosineSimilarity.between(queryVector, vector);
            if (score >= minScore) {
                scored.add(new Scored(chunk, score));
            }
        }
        scored.sort(Comparator.comparingDouble(Scored::score).reversed());
        List<Scored> top = scored.subList(0, Math.min(topK, scored.size()));
        if (top.isEmpty()) {
            return List.of();
        }

        Map<Long, String> filenames = loadFilenames(top);
        return top.stream()
                .map(scoredChunk -> new SearchHit(
                        scoredChunk.chunk().getId(),
                        scoredChunk.chunk().getDocumentId(),
                        filenames.getOrDefault(scoredChunk.chunk().getDocumentId(), ""),
                        scoredChunk.chunk().getChunkIndex(),
                        scoredChunk.score(),
                        scoredChunk.chunk().getContent()))
                .toList();
    }

    private float[] embedQuery(String query) {
        List<float[]> vectors = aiGateway.embed(List.of(query));
        if (vectors == null || vectors.size() != 1 || vectors.get(0) == null
                || vectors.get(0).length == 0) {
            throw new BusinessException(ErrorCode.BAD_GATEWAY, "向量服务返回异常，请稍后重试");
        }
        return vectors.get(0);
    }

    /** 解析失败跳过该片：脏数据不该让整次检索失败。 */
    private float[] parseEmbedding(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, float[].class);
        } catch (Exception ex) {
            log.warn("切片向量解析失败，已跳过：{}", ex.getMessage());
            return null;
        }
    }

    private Map<Long, String> loadFilenames(List<Scored> top) {
        List<Long> documentIds = top.stream()
                .map(scoredChunk -> scoredChunk.chunk().getDocumentId())
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        return documentMapper.selectByIds(documentIds).stream()
                .collect(Collectors.toMap(KbDocument::getId, KbDocument::getFilename,
                        (left, right) -> left));
    }

    private record Scored(DocumentChunk chunk, double score) {
    }
}

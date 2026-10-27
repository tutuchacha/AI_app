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
 * 向量检索：问题向量化 → 取库内切片 → 相似度 → 阈值过滤 → 截断 topK。
 * 正式库走 pgvector（SQL 排序 + HNSW）；H2 测试库没有扩展，退回 Java 余弦。
 */
@Service
public class RetrievalService {

    private static final Logger log = LoggerFactory.getLogger(RetrievalService.class);

    /** memory 模式下单次检索最多加载的切片数，防止大库拖垮请求（pg 模式由 SQL LIMIT 裁剪）。 */
    private static final int MAX_CANDIDATES = 5000;

    private static final String STORE_PG = "pg";

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

        List<Hit> top = usePgVector()
                ? searchByPgvector(knowledgeBaseId, queryVector, topK, minScore)
                : searchInMemory(knowledgeBaseId, queryVector, topK, minScore);
        if (top.isEmpty()) {
            return List.of();
        }

        Map<Long, String> filenames = loadFilenames(top);
        return top.stream()
                .map(hit -> new SearchHit(
                        hit.chunkId(),
                        hit.documentId(),
                        filenames.getOrDefault(hit.documentId(), ""),
                        hit.chunkIndex(),
                        hit.score(),
                        hit.content()))
                .toList();
    }

    private boolean usePgVector() {
        return STORE_PG.equals(properties.vectorStoreOrDefault());
    }

    /** pgvector：阈值与 topK 交给 PG，返回顺序即相似度倒序。 */
    private List<Hit> searchByPgvector(Long knowledgeBaseId, float[] queryVector, int topK, double minScore) {
        return chunkMapper.searchByVector(knowledgeBaseId, Vectors.toLiteral(queryVector), minScore, topK)
                .stream()
                .map(row -> new Hit(row.id(), row.documentId(), row.chunkIndex(), row.content(),
                        row.score() == null ? 0d : row.score()))
                .toList();
    }

    private List<Hit> searchInMemory(Long knowledgeBaseId, float[] queryVector, int topK, double minScore) {
        List<DocumentChunk> chunks = chunkMapper.selectList(Wrappers.<DocumentChunk>lambdaQuery()
                .eq(DocumentChunk::getKbId, knowledgeBaseId)
                .isNotNull(DocumentChunk::getEmbedding)
                .orderByAsc(DocumentChunk::getId)
                .last("LIMIT " + MAX_CANDIDATES));
        if (chunks.isEmpty()) {
            return List.of();
        }

        List<Hit> scored = new ArrayList<>();
        for (DocumentChunk chunk : chunks) {
            float[] vector = parseEmbedding(chunk.getEmbedding());
            if (vector == null || vector.length != queryVector.length) {
                continue;
            }
            double score = CosineSimilarity.between(queryVector, vector);
            if (score >= minScore) {
                scored.add(new Hit(chunk.getId(), chunk.getDocumentId(), chunk.getChunkIndex(),
                        chunk.getContent(), score));
            }
        }
        scored.sort(Comparator.comparingDouble(Hit::score).reversed());
        return List.copyOf(scored.subList(0, Math.min(topK, scored.size())));
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

    private Map<Long, String> loadFilenames(List<Hit> top) {
        List<Long> documentIds = top.stream()
                .map(Hit::documentId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        return documentMapper.selectByIds(documentIds).stream()
                .collect(Collectors.toMap(KbDocument::getId, KbDocument::getFilename,
                        (left, right) -> left));
    }

    private record Hit(Long chunkId, Long documentId, Integer chunkIndex, String content, double score) {
    }
}

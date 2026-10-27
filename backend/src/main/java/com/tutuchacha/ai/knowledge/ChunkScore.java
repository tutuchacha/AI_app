package com.tutuchacha.ai.knowledge;

/** pgvector 检索返回的一行：只取展示与打分需要的字段。 */
public record ChunkScore(Long id, Long documentId, Integer chunkIndex, String content, Double score) {
}

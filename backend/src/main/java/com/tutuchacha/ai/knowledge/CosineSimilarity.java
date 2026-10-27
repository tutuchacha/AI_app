package com.tutuchacha.ai.knowledge;

/**
 * 余弦相似度：值域 [-1, 1]，文本向量实践中落在 [0, 1] 附近。
 * 维度不一致或零向量直接判 0，避免除零与错配脏数据放大分数。
 */
public final class CosineSimilarity {

    private CosineSimilarity() {
    }

    public static double between(float[] a, float[] b) {
        if (a == null || b == null || a.length == 0 || a.length != b.length) {
            return 0d;
        }
        double dot = 0d;
        double normA = 0d;
        double normB = 0d;
        for (int i = 0; i < a.length; i += 1) {
            dot += (double) a[i] * b[i];
            normA += (double) a[i] * a[i];
            normB += (double) b[i] * b[i];
        }
        if (normA == 0d || normB == 0d) {
            return 0d;
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}

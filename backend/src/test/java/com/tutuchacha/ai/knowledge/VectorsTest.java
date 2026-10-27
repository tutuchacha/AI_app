package com.tutuchacha.ai.knowledge;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 向量字面量同时喂给 MySQL JSON 列与 pgvector 输入，格式错了检索就静默失效。 */
class VectorsTest {

    @Test
    void writesJavaScriptCompatibleArray() {
        assertThat(Vectors.toLiteral(new float[]{1f, 0.5f, -2f})).isEqualTo("[1.0,0.5,-2.0]");
    }

    @Test
    void vectorStoreDefaultsToPgAndIgnoresCase() {
        assertThat(new RagProperties(null, null, null, null, null, null, null).vectorStoreOrDefault())
                .isEqualTo("pg");
        assertThat(new RagProperties(null, null, null, null, " MEMORY ", null, null).vectorStoreOrDefault())
                .isEqualTo("memory");
    }
}

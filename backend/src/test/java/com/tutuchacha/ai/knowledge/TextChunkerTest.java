package com.tutuchacha.ai.knowledge;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TextChunkerTest {

    @Test
    void splitsLongTextWithOverlap() {
        String text = "第一句话。第二句话。第三句话。第四句话。第五句话。";
        List<String> pieces = TextChunker.split(text, 20, 5);

        assertThat(pieces).hasSizeGreaterThan(1);
        assertThat(pieces.get(0)).startsWith("第一句话。");
        // 相邻切片必须重叠，保证跨片语义不断裂
        assertThat(pieces.get(1)).contains(pieces.get(0).substring(pieces.get(0).length() - 5));
    }

    @Test
    void shortTextStaysSinglePiece() {
        assertThat(TextChunker.split("只有一句话。", 100, 10)).containsExactly("只有一句话。");
    }

    @Test
    void crlfIsNormalizedAndBlankReturnsEmpty() {
        assertThat(TextChunker.split("a\r\nb", 100, 10)).containsExactly("a\nb");
        assertThat(TextChunker.split("   \n\n ", 100, 10)).isEmpty();
        assertThat(TextChunker.split(null, 100, 10)).isEmpty();
    }

    @Test
    void coversWholeTextWithoutGaps() {
        String text = "0123456789".repeat(20);
        List<String> pieces = TextChunker.split(text, 40, 10);

        assertThat(pieces.get(0)).startsWith(text.substring(0, 20));
        assertThat(pieces.get(pieces.size() - 1)).endsWith(text.substring(text.length() - 10));
    }

    @Test
    void toleratesOverlapNotSmallerThanSize() {
        List<String> pieces = TextChunker.split("abcdef", 3, 10);
        assertThat(pieces).isNotEmpty();
        assertThat(pieces.get(0)).hasSizeLessThanOrEqualTo(6);
    }
}

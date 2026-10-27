package com.tutuchacha.ai.knowledge;

import java.util.ArrayList;
import java.util.List;

/**
 * 按字符窗口切片：优先在换行/句号处断开，保证相邻切片有 overlap 个字符重叠。
 * 纯函数，方便单测覆盖边界。
 */
public final class TextChunker {

    private static final String BREAK_CHARS = "\n。！？!?；;";

    private TextChunker() {
    }

    public static List<String> split(String text, int chunkSize, int overlap) {
        List<String> pieces = new ArrayList<>();
        if (text == null) {
            return pieces;
        }
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n').trim();
        if (normalized.isEmpty()) {
            return pieces;
        }
        int size = Math.max(1, chunkSize);
        int step = Math.max(1, size - Math.max(0, overlap));

        int start = 0;
        while (start < normalized.length()) {
            int end = Math.min(normalized.length(), start + size);
            if (end < normalized.length()) {
                int cut = lastBreak(normalized, start + size / 2, end);
                if (cut > start) {
                    end = cut;
                }
            }
            String piece = normalized.substring(start, end).trim();
            if (!piece.isEmpty()) {
                pieces.add(piece);
            }
            if (end >= normalized.length()) {
                break;
            }
            start += step;
        }
        return pieces;
    }

    /** 在 [from, to) 内找最后一个断句字符，返回其下标 + 1；找不到返回 -1。 */
    private static int lastBreak(String text, int from, int to) {
        for (int i = to - 1; i >= Math.max(0, from); i -= 1) {
            if (BREAK_CHARS.indexOf(text.charAt(i)) >= 0) {
                return i + 1;
            }
        }
        return -1;
    }
}

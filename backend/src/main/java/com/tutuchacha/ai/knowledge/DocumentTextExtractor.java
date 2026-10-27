package com.tutuchacha.ai.knowledge;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/** 文本抽取：pdf/docx/txt/md 统一走 Tika，解析细节不外泄给业务层。 */
@Component
public class DocumentTextExtractor {

    /** 超出部分直接丢弃，避免超大 PDF 把内存和切片数拖爆。 */
    private static final int MAX_TEXT_LENGTH = 2_000_000;

    private final Parser parser = new AutoDetectParser();

    public String extract(Path file, String filename) {
        try (InputStream in = Files.newInputStream(file)) {
            BodyContentHandler handler = new BodyContentHandler(-1);
            Metadata metadata = new Metadata();
            metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, filename);
            // 纯文本按 UTF-8 读，避免 Tika 猜成 windows-1252 把中文读成乱码
            String contentType = plainTextContentType(filename);
            if (contentType != null) {
                metadata.set(Metadata.CONTENT_TYPE, contentType);
            }
            parser.parse(in, handler, metadata, new ParseContext());
            String text = handler.toString();
            return text.length() <= MAX_TEXT_LENGTH ? text : text.substring(0, MAX_TEXT_LENGTH);
        } catch (Exception ex) {
            throw new IllegalStateException("文档解析失败：" + ex.getMessage(), ex);
        }
    }

    private String plainTextContentType(String filename) {
        String lower = filename.toLowerCase(java.util.Locale.ROOT);
        return lower.endsWith(".txt") || lower.endsWith(".md") ? "text/plain; charset=UTF-8" : null;
    }
}

package com.tutuchacha.ai.knowledge;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 单个文档的解析流水：PENDING → PARSING → READY / FAILED。
 * 解析失败只影响该文档，异常全部转成 FAILED + error_msg，不向上抛。
 */
@Service
public class DocumentParser {

    private static final Logger log = LoggerFactory.getLogger(DocumentParser.class);
    private static final int MAX_CHUNKS = 2000;

    private final KbDocumentMapper documentMapper;
    private final DocumentChunkMapper chunkMapper;
    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final DocumentTextExtractor extractor;
    private final RagProperties properties;

    public DocumentParser(KbDocumentMapper documentMapper,
                          DocumentChunkMapper chunkMapper,
                          KnowledgeBaseMapper knowledgeBaseMapper,
                          DocumentTextExtractor extractor,
                          RagProperties properties) {
        this.documentMapper = documentMapper;
        this.chunkMapper = chunkMapper;
        this.knowledgeBaseMapper = knowledgeBaseMapper;
        this.extractor = extractor;
        this.properties = properties;
    }

    public void parse(Long documentId) {
        KbDocument document = documentMapper.selectById(documentId);
        if (document == null) {
            return;
        }
        mark(documentId, "PARSING", 0, "");
        try {
            KnowledgeBase knowledgeBase = knowledgeBaseMapper.selectById(document.getKbId());
            if (knowledgeBase == null) {
                throw new IllegalStateException("知识库已删除");
            }
            Path file = Path.of(properties.uploadDirOrDefault(), documentId + "." + document.getExt());
            if (!Files.exists(file)) {
                throw new IllegalStateException("源文件已丢失，请重新上传");
            }

            String text = extractor.extract(file, document.getFilename());
            List<String> pieces = TextChunker.split(text, knowledgeBase.getChunkSize(),
                    knowledgeBase.getChunkOverlap());
            if (pieces.isEmpty()) {
                throw new IllegalStateException("未提取到文本内容");
            }
            if (pieces.size() > MAX_CHUNKS) {
                log.warn("文档切片过多，截断到 {} 片 documentId={}", MAX_CHUNKS, documentId);
                pieces = pieces.subList(0, MAX_CHUNKS);
            }

            replaceChunks(document, pieces);
            mark(documentId, "READY", pieces.size(), "");
            log.info("文档解析完成 documentId={} chunks={}", documentId, pieces.size());
        } catch (Exception ex) {
            String message = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
            log.warn("文档解析失败 documentId={}: {}", documentId, message);
            mark(documentId, "FAILED", 0, truncate(message));
        }
    }

    private void replaceChunks(KbDocument document, List<String> pieces) {
        chunkMapper.delete(Wrappers.<DocumentChunk>lambdaQuery()
                .eq(DocumentChunk::getDocumentId, document.getId()));
        for (int i = 0; i < pieces.size(); i += 1) {
            String content = pieces.get(i);
            DocumentChunk chunk = new DocumentChunk();
            chunk.setDocumentId(document.getId());
            chunk.setKbId(document.getKbId());
            chunk.setChunkIndex(i);
            chunk.setContent(content);
            chunk.setTokenCount(content.length());
            chunk.setMetadataJson("{\"source\":\"" + document.getFilename().replace("\"", "") + "\"}");
            chunkMapper.insert(chunk);
        }
    }

    private void mark(Long documentId, String status, int chunkCount, String errorMsg) {
        KbDocument patch = new KbDocument();
        patch.setId(documentId);
        patch.setStatus(status);
        patch.setChunkCount(chunkCount);
        patch.setErrorMsg(errorMsg);
        documentMapper.updateById(patch);
    }

    private String truncate(String message) {
        return message.length() <= 500 ? message : message.substring(0, 500);
    }
}

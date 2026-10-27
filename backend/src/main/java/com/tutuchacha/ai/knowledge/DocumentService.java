package com.tutuchacha.ai.knowledge;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.tutuchacha.ai.common.BusinessException;
import com.tutuchacha.ai.common.ErrorCode;
import com.tutuchacha.ai.knowledge.dto.DocumentView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 文档上传与生命周期：校验 → 去重 → 落盘 → 提交解析。
 * 同库同内容（sha256）重复上传直接返回已有文档，不重复切片。
 */
@Service
public class DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "docx", "txt", "md");
    private static final DateTimeFormatter ISO_LOCAL_DATE_TIME = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final int MAX_NAME_LENGTH = 200;

    private final KbDocumentMapper documentMapper;
    private final DocumentChunkMapper chunkMapper;
    private final KnowledgeBaseService knowledgeBaseService;
    private final DocumentParser parser;
    private final DocumentIndexer indexer;
    private final RagProperties properties;

    public DocumentService(KbDocumentMapper documentMapper,
                           DocumentChunkMapper chunkMapper,
                           KnowledgeBaseService knowledgeBaseService,
                           DocumentParser parser,
                           DocumentIndexer indexer,
                           RagProperties properties) {
        this.documentMapper = documentMapper;
        this.chunkMapper = chunkMapper;
        this.knowledgeBaseService = knowledgeBaseService;
        this.parser = parser;
        this.indexer = indexer;
        this.properties = properties;
    }

    public DocumentView upload(String username, Long knowledgeBaseId, MultipartFile file) {
        KnowledgeBase knowledgeBase = knowledgeBaseService.requireOwned(username, knowledgeBaseId);
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请选择文件");
        }
        String filename = safeFilename(file.getOriginalFilename());
        String ext = extensionOf(filename);
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "仅支持 pdf / docx / txt / md");
        }
        long maxBytes = properties.maxFileSizeBytes();
        if (file.getSize() > maxBytes) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "文件不能超过 " + (maxBytes / 1024 / 1024) + "MB");
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "读取上传文件失败");
        }
        if (bytes.length == 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "文件内容为空");
        }
        String sha256 = sha256(bytes);

        KbDocument existing = documentMapper.selectOne(Wrappers.<KbDocument>lambdaQuery()
                .eq(KbDocument::getKbId, knowledgeBaseId)
                .eq(KbDocument::getSha256, sha256)
                .last("LIMIT 1"));
        if (existing != null) {
            log.info("重复上传，复用已有文档 documentId={} kbId={}", existing.getId(), knowledgeBaseId);
            return toView(existing);
        }

        KbDocument document = new KbDocument();
        document.setKbId(knowledgeBaseId);
        document.setFilename(filename);
        document.setExt(ext);
        document.setSize((long) bytes.length);
        document.setSha256(sha256);
        document.setStatus("PENDING");
        document.setChunkCount(0);
        document.setErrorMsg("");
        documentMapper.insert(document);

        writeFile(document, bytes);
        knowledgeBaseService.adjustDocumentCount(knowledgeBase.getId(), 1);
        dispatch(document.getId());
        return toView(documentMapper.selectById(document.getId()));
    }

    public DocumentView detail(String username, Long id) {
        return toView(requireOwned(username, id));
    }

    public void delete(String username, Long id) {
        KbDocument document = requireOwned(username, id);
        // 显式清切片：不依赖外键级联，导入/迁移场景也能保持一致
        chunkMapper.delete(Wrappers.<DocumentChunk>lambdaQuery().eq(DocumentChunk::getDocumentId, id));
        documentMapper.deleteById(id);
        knowledgeBaseService.adjustDocumentCount(document.getKbId(), -1);
        try {
            Files.deleteIfExists(originalPath(document));
        } catch (IOException ex) {
            log.warn("删除源文件失败 documentId={}: {}", id, ex.getMessage());
        }
    }

    /** 重建：清空旧切片后重新解析，状态回到 PENDING 方便前端轮询。 */
    public DocumentView reindex(String username, Long id) {
        KbDocument document = requireOwned(username, id);
        KbDocument patch = new KbDocument();
        patch.setId(id);
        patch.setStatus("PENDING");
        patch.setChunkCount(0);
        patch.setErrorMsg("");
        documentMapper.updateById(patch);
        dispatch(id);
        return toView(documentMapper.selectById(document.getId()));
    }

    /** 列表供 M2-03 检索与前端文档面板复用。 */
    public List<DocumentView> list(String username, Long knowledgeBaseId) {
        knowledgeBaseService.requireOwned(username, knowledgeBaseId);
        return documentMapper.selectList(Wrappers.<KbDocument>lambdaQuery()
                        .eq(KbDocument::getKbId, knowledgeBaseId)
                        .orderByDesc(KbDocument::getId))
                .stream()
                .map(this::toView)
                .toList();
    }

    private void dispatch(Long documentId) {
        if (properties.asyncEnabled()) {
            indexer.submit(documentId);
        } else {
            parser.parse(documentId);
        }
    }

    private void writeFile(KbDocument document, byte[] bytes) {
        Path target = originalPath(document);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, bytes);
        } catch (IOException ex) {
            documentMapper.deleteById(document.getId());
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "保存文件失败");
        }
    }

    private Path originalPath(KbDocument document) {
        return Path.of(properties.uploadDirOrDefault(), document.getId() + "." + document.getExt());
    }

    /** 取当前用户名下的文档：不存在 404，文档所属库不属于本人 403。M3 文档分析复用同一校验。 */
    public KbDocument requireOwned(String username, Long id) {
        KbDocument document = documentMapper.selectById(id);
        if (document == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "文档不存在");
        }
        // 借知识库归属判断文档权限：库不是自己的就 403
        knowledgeBaseService.requireOwned(username, document.getKbId());
        return document;
    }

    private String safeFilename(String original) {
        String name = StringUtils.hasText(original) ? original : "upload.txt";
        // 只保留最后一段，去掉客户端可能带上的路径
        name = name.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).trim();
        if (name.isEmpty()) {
            name = "upload.txt";
        }
        return name.length() <= MAX_NAME_LENGTH ? name : name.substring(0, MAX_NAME_LENGTH);
    }

    private String extensionOf(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private DocumentView toView(KbDocument document) {
        return new DocumentView(document.getId(), document.getKbId(), document.getFilename(),
                document.getExt(), document.getSize(), document.getStatus(), document.getChunkCount(),
                document.getErrorMsg(), iso(document.getCreatedAt()), iso(document.getUpdatedAt()));
    }

    private String iso(LocalDateTime time) {
        return time == null ? "" : time.format(ISO_LOCAL_DATE_TIME);
    }
}

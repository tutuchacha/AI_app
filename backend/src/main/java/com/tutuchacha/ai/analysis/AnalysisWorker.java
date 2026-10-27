package com.tutuchacha.ai.analysis;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.tutuchacha.ai.gateway.AiChatRequest;
import com.tutuchacha.ai.gateway.AiChatResponse;
import com.tutuchacha.ai.gateway.AiGateway;
import com.tutuchacha.ai.gateway.AiMessage;
import com.tutuchacha.ai.knowledge.DocumentChunk;
import com.tutuchacha.ai.knowledge.DocumentChunkMapper;
import com.tutuchacha.ai.knowledge.KbDocument;
import com.tutuchacha.ai.knowledge.KbDocumentMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 单个分析任务的执行体：取切片文本 → 拼提示词 → 调模型 → 落结果。
 * 失败只把本任务标 FAILED + error_msg，不向上抛，避免影响线程池。
 */
@Component
public class AnalysisWorker {

    private static final Logger log = LoggerFactory.getLogger(AnalysisWorker.class);
    private static final String SYSTEM_PROMPT = """
            你是星屿 AI 的文档分析师。只依据用户提供的文档正文作答，不补充正文之外的事实；
            信息不足时明确说明。输出使用简体中文。""";
    private static final int MAX_ERROR_LENGTH = 500;

    private final DocumentAnalysisMapper analysisMapper;
    private final DocumentChunkMapper chunkMapper;
    private final KbDocumentMapper documentMapper;
    private final AiGateway aiGateway;
    private final AnalysisProperties properties;

    public AnalysisWorker(DocumentAnalysisMapper analysisMapper,
                          DocumentChunkMapper chunkMapper,
                          KbDocumentMapper documentMapper,
                          AiGateway aiGateway,
                          AnalysisProperties properties) {
        this.analysisMapper = analysisMapper;
        this.chunkMapper = chunkMapper;
        this.documentMapper = documentMapper;
        this.aiGateway = aiGateway;
        this.properties = properties;
    }

    public void run(Long analysisId) {
        DocumentAnalysis analysis = analysisMapper.selectById(analysisId);
        if (analysis == null || !"PENDING".equals(analysis.getStatus())) {
            return;
        }
        mark(analysisId, "RUNNING", null, null, 0, 0, 0, "");

        try {
            KbDocument document = documentMapper.selectById(analysis.getDocumentId());
            if (document == null) {
                throw new IllegalStateException("文档已删除");
            }
            String text = collectText(document.getId());
            if (text.isBlank()) {
                throw new IllegalStateException("文档没有可分析的文本");
            }
            AnalysisType type = AnalysisType.parse(analysis.getAnalysisType());
            AiChatResponse response = aiGateway.complete(buildRequest(type, document.getFilename(), text));
            if (response == null || response.content() == null || response.content().isBlank()) {
                throw new IllegalStateException("模型未返回内容");
            }
            mark(analysisId, "READY", response.content(), response.model(),
                    response.promptTokens(), response.completionTokens(), (int) response.latencyMs(), "");
            log.info("文档分析完成 analysisId={} type={} tokens={}+{}", analysisId,
                    type.name(), response.promptTokens(), response.completionTokens());
        } catch (Exception ex) {
            String message = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
            log.warn("文档分析失败 analysisId={}: {}", analysisId, message);
            mark(analysisId, "FAILED", null, null, 0, 0, 0, truncate(message));
        }
    }

    /** 按切片顺序拼正文，超过配置上限就截断并显式告知模型。 */
    private String collectText(Long documentId) {
        List<DocumentChunk> chunks = chunkMapper.selectList(Wrappers.<DocumentChunk>lambdaQuery()
                .eq(DocumentChunk::getDocumentId, documentId)
                .orderByAsc(DocumentChunk::getChunkIndex)
                .last("LIMIT " + properties.maxChunksOrDefault()));
        int maxChars = properties.maxCharsOrDefault();
        StringBuilder text = new StringBuilder();
        for (DocumentChunk chunk : chunks) {
            if (chunk.getContent() == null || chunk.getContent().isBlank()) {
                continue;
            }
            if (!text.isEmpty()) {
                text.append("\n\n");
            }
            text.append(chunk.getContent().trim());
            if (text.length() >= maxChars) {
                break;
            }
        }
        String content = text.toString();
        if (content.length() > maxChars) {
            content = content.substring(0, maxChars) + "\n\n（正文较长，已截断）";
        }
        return content;
    }

    private AiChatRequest buildRequest(AnalysisType type, String filename, String text) {
        String system = SYSTEM_PROMPT + "\n\n" + type.instruction();
        String user = "文档名：" + filename + "\n\n正文：\n" + text;
        List<AiMessage> messages = List.of(AiMessage.system(system), AiMessage.user(user));
        return new AiChatRequest(null, messages, 0.2d, properties.maxTokensOrDefault());
    }

    /** 只更新非空字段；updatedAt 显式写入，兼容没有 ON UPDATE 的测试库。 */
    private void mark(Long analysisId, String status, String content, String model,
                      int promptTokens, int completionTokens, int latencyMs, String errorMsg) {
        DocumentAnalysis patch = new DocumentAnalysis();
        patch.setId(analysisId);
        patch.setStatus(status);
        patch.setUpdatedAt(LocalDateTime.now());
        if (content != null) {
            patch.setContent(content);
        }
        if (model != null) {
            patch.setModel(model);
        }
        if ("READY".equals(status) || "FAILED".equals(status)) {
            patch.setPromptTokens(promptTokens);
            patch.setCompletionTokens(completionTokens);
            patch.setLatencyMs(latencyMs);
            patch.setErrorMsg(errorMsg);
        }
        analysisMapper.updateById(patch);
    }

    private String truncate(String message) {
        return message.length() <= MAX_ERROR_LENGTH ? message : message.substring(0, MAX_ERROR_LENGTH);
    }
}

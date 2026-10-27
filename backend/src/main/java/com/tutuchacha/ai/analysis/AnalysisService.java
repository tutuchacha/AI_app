package com.tutuchacha.ai.analysis;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.tutuchacha.ai.analysis.dto.AnalysisRequest;
import com.tutuchacha.ai.analysis.dto.AnalysisView;
import com.tutuchacha.ai.common.BusinessException;
import com.tutuchacha.ai.common.ErrorCode;
import com.tutuchacha.ai.knowledge.DocumentService;
import com.tutuchacha.ai.knowledge.KbDocument;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 文档分析任务的生命周期：创建（校验文档 READY）→ 异步执行 → 查询 / 删除。
 * 归属沿用文档所属知识库的判断，越权 403、不存在 404。
 */
@Service
public class AnalysisService {

    private static final int MAX_LIST_SIZE = 20;
    private static final DateTimeFormatter ISO_LOCAL_DATE_TIME = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final DocumentAnalysisMapper analysisMapper;
    private final DocumentService documentService;
    private final AnalysisRunner runner;
    private final AnalysisWorker worker;
    private final AnalysisProperties properties;

    public AnalysisService(DocumentAnalysisMapper analysisMapper,
                           DocumentService documentService,
                           AnalysisRunner runner,
                           AnalysisWorker worker,
                           AnalysisProperties properties) {
        this.analysisMapper = analysisMapper;
        this.documentService = documentService;
        this.runner = runner;
        this.worker = worker;
        this.properties = properties;
    }

    public AnalysisView create(String username, Long documentId, AnalysisRequest request) {
        KbDocument document = documentService.requireOwned(username, documentId);
        if (!"READY".equals(document.getStatus())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "文档尚未解析完成（当前 " + document.getStatus() + "），请等待 READY 后再分析");
        }
        AnalysisType type = AnalysisType.parse(request == null ? null : request.type());

        // 同一文档同一类型已有任务在跑：直接复用，避免前端连点产生重复调用
        DocumentAnalysis running = analysisMapper.selectOne(Wrappers.<DocumentAnalysis>lambdaQuery()
                .eq(DocumentAnalysis::getDocumentId, documentId)
                .eq(DocumentAnalysis::getAnalysisType, type.name())
                .in(DocumentAnalysis::getStatus, "PENDING", "RUNNING")
                .orderByDesc(DocumentAnalysis::getId)
                .last("LIMIT 1"));
        if (running != null) {
            return toView(running);
        }

        DocumentAnalysis entity = new DocumentAnalysis();
        entity.setDocumentId(documentId);
        entity.setKbId(document.getKbId());
        entity.setAnalysisType(type.name());
        entity.setStatus("PENDING");
        entity.setContent("");
        entity.setModel("");
        entity.setPromptTokens(0);
        entity.setCompletionTokens(0);
        entity.setLatencyMs(0);
        entity.setErrorMsg("");
        analysisMapper.insert(entity);

        dispatch(entity.getId());
        return toView(analysisMapper.selectById(entity.getId()));
    }

    /** 某文档的分析记录，最新在前；前端按 type 取各自最新一条。 */
    public List<AnalysisView> list(String username, Long documentId) {
        documentService.requireOwned(username, documentId);
        return analysisMapper.selectList(Wrappers.<DocumentAnalysis>lambdaQuery()
                        .eq(DocumentAnalysis::getDocumentId, documentId)
                        .orderByDesc(DocumentAnalysis::getId)
                        .last("LIMIT " + MAX_LIST_SIZE))
                .stream()
                .map(this::toView)
                .toList();
    }

    public AnalysisView detail(String username, Long id) {
        return toView(requireOwned(username, id));
    }

    public void delete(String username, Long id) {
        analysisMapper.deleteById(requireOwned(username, id).getId());
    }

    private void dispatch(Long analysisId) {
        if (properties.asyncEnabled()) {
            runner.submit(analysisId);
        } else {
            worker.run(analysisId);
        }
    }

    private DocumentAnalysis requireOwned(String username, Long id) {
        DocumentAnalysis analysis = analysisMapper.selectById(id);
        if (analysis == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "分析记录不存在");
        }
        // 借文档归属判断分析记录权限：文档不是自己的就 403
        documentService.requireOwned(username, analysis.getDocumentId());
        return analysis;
    }

    private AnalysisView toView(DocumentAnalysis entity) {
        return new AnalysisView(entity.getId(), entity.getDocumentId(), entity.getKbId(),
                entity.getAnalysisType(), entity.getStatus(), entity.getContent(), entity.getModel(),
                entity.getPromptTokens(), entity.getCompletionTokens(), entity.getLatencyMs(),
                entity.getErrorMsg(), iso(entity.getCreatedAt()), iso(entity.getUpdatedAt()));
    }

    private String iso(LocalDateTime time) {
        return time == null ? "" : time.format(ISO_LOCAL_DATE_TIME);
    }
}

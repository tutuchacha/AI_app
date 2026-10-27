package com.tutuchacha.ai.analysis;

import com.tutuchacha.ai.common.BusinessException;
import com.tutuchacha.ai.common.ErrorCode;

import java.util.Locale;

/**
 * 三种分析任务及其提示词。枚举名直接落库，对外契约只认这三个值。
 * 提示词集中在这里，调整输出格式不用改调度与服务代码。
 */
public enum AnalysisType {

    SUMMARY("摘要", """
            请输出 3~6 句话的中文摘要，覆盖文档目的、主要对象（人物/组织/系统）、关键结论或数据。
            直接输出摘要正文，不要加标题，不要使用 Markdown 列表。"""),

    OUTLINE("结构", """
            请抽取文档的章节结构，使用 Markdown 输出：
            - 用 `## ` 作为一级章节标题，最多 8 个；
            - 每个章节用 `- ` 列出 2~5 条要点；
            - 保留原文的层级关系，不要编造正文里没有的内容。"""),

    QUESTIONS("追问", """
            请生成 6~8 个用于深入理解或评审该文档的中文追问，使用 Markdown 有序列表。
            每个问题单独一行，下一行用「考察点：」说明该问题想验证什么。
            问题要具体到文档内容，避免泛泛而谈。""");

    private final String label;
    private final String instruction;

    AnalysisType(String label, String instruction) {
        this.label = label;
        this.instruction = instruction;
    }

    public String label() {
        return label;
    }

    public String instruction() {
        return instruction;
    }

    /** 接收前端字符串，大小写不敏感；为空或未知类型一律 400。 */
    public static AnalysisType parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "type 必填：SUMMARY / OUTLINE / QUESTIONS");
        }
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "不支持的 type：" + raw + "，可选 SUMMARY / OUTLINE / QUESTIONS");
        }
    }
}

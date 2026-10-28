package com.tutuchacha.ai.date.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * AI 返回的结构化建议；只做展示与落库，不参与业务判断。
 * 模型偶尔会多给字段，这里忽略未知字段，缺字段按空处理。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "恋爱助手：AI 建议内容")
public record AdviceContent(
        @Schema(description = "本阶段一句话目标") String goal,
        @Schema(description = "3 个可执行动作") List<Action> actions,
        @Schema(description = "参考话术") List<Script> scripts,
        @Schema(description = "禁忌 / 不要做") List<String> avoid,
        @Schema(description = "怎么判断这一步成了") String success,
        @Schema(description = "用于查高德 POI 的关键词") List<String> spotKeywords
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Action(String title, String detail, String timing) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Script(String scene, String text) {
    }

    public List<Action> actionsOrEmpty() {
        return actions == null ? List.of() : actions;
    }

    public List<Script> scriptsOrEmpty() {
        return scripts == null ? List.of() : scripts;
    }

    public List<String> avoidOrEmpty() {
        return avoid == null ? List.of() : avoid;
    }

    public List<String> keywordsOrEmpty() {
        return spotKeywords == null ? List.of() : spotKeywords.stream().filter(k -> k != null && !k.isBlank()).toList();
    }
}

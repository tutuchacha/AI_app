package com.tutuchacha.ai.date.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/** 模型对问题的解析：一句回复 + 区域 + 检索关键词。 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AskPlan(String reply, String area, List<String> keywords) {

    public List<String> keywordsOrEmpty() {
        return keywords == null ? List.of()
                : keywords.stream().filter(k -> k != null && !k.isBlank()).map(String::trim).distinct().limit(3).toList();
    }
}

package com.tutuchacha.ai.date.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** 模型复盘输出；缺字段按空处理，非法值在服务层兜底。 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ReviewContent(String verdict, String reason, String nextStep, String stage) {
}

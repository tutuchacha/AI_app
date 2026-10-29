package com.tutuchacha.ai.date;

import com.tutuchacha.ai.common.BusinessException;
import com.tutuchacha.ai.common.ErrorCode;

/** 执行状态：计划 / 完成 / 跳过。 */
public enum DateActionStatus {

    PLANNED("计划中"),
    DONE("已完成"),
    SKIPPED("已跳过");

    private final String label;

    DateActionStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** 空值回落 PLANNED；非法值 400，避免脏数据进库。 */
    public static DateActionStatus parse(String value) {
        if (value == null || value.isBlank()) {
            return PLANNED;
        }
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "执行状态只能是 PLANNED / DONE / SKIPPED");
        }
    }
}

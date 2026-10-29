package com.tutuchacha.ai.date;

import com.tutuchacha.ai.common.BusinessException;
import com.tutuchacha.ai.common.ErrorCode;

/** 对方反应：回填后复盘据此判断继续还是降速。 */
public enum DateActionResult {

    UNKNOWN("未回填"),
    GOOD("反应不错"),
    NEUTRAL("一般"),
    COLD("有点冷"),
    NO_REPLY("没回复");

    private final String label;

    DateActionResult(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** 空值回落 UNKNOWN；非法值 400。 */
    public static DateActionResult parse(String value) {
        if (value == null || value.isBlank()) {
            return UNKNOWN;
        }
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "反馈结果只能是 UNKNOWN / GOOD / NEUTRAL / COLD / NO_REPLY");
        }
    }
}

package com.tutuchacha.ai.date;

import com.tutuchacha.ai.common.BusinessException;
import com.tutuchacha.ai.common.ErrorCode;

/** 关系阶段：AI 按阶段给建议，前端按阶段配色。 */
public enum DateStage {

    MET("刚认识"),
    CHATTING("聊天中"),
    INVITED("已邀约"),
    DATING("约会中"),
    TOGETHER("已确定关系"),
    PAUSED("暂停");

    private final String label;

    DateStage(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** 空值回落 MET；非法值直接 400，避免脏数据进库。 */
    public static DateStage parse(String value) {
        if (value == null || value.isBlank()) {
            return MET;
        }
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "阶段只能是 MET / CHATTING / INVITED / DATING / TOGETHER / PAUSED");
        }
    }
}

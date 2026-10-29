package com.tutuchacha.ai.date;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 阶段变更流水：只在阶段真的变了时写一条，供时间线展示。 */
@Data
@TableName("date_stage_log")
public class DateStageLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long targetId;

    private String fromStage;

    private String toStage;

    /** MANUAL 手动修改 / REVIEW 复盘后应用。 */
    private String source;

    private LocalDateTime createdAt;
}

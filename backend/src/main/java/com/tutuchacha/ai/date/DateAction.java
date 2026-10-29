package com.tutuchacha.ai.date;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 恋爱助手：一次执行记录 + 对方反应；复盘只看这张表与最近一条建议。 */
@Data
@TableName("date_action")
public class DateAction {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long targetId;

    /** 来自哪条建议，可空（自定义待办）。 */
    private Long adviceId;

    private String title;

    private String detail;

    private LocalDate plannedAt;

    private String status;

    private String result;

    private String note;

    private LocalDateTime doneAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}

package com.tutuchacha.ai.date;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 恋爱助手：目标对象档案；stage 决定 AI 给什么阶段的建议。 */
@Data
@TableName("date_target")
public class DateTarget {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String name;

    private String channel;

    private String stage;

    private Integer age;

    private String city;

    private String interests;

    private String taboos;

    private String notes;

    private LocalDate lastContactAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}

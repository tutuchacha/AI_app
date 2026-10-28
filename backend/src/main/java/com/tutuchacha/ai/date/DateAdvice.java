package com.tutuchacha.ai.date;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 恋爱助手：一次 AI 建议；content / spots 都是 JSON 文本，按需解析。 */
@Data
@TableName("date_advice")
public class DateAdvice {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long targetId;

    private String stage;

    private String city;

    private String goal;

    private String content;

    private String spots;

    private String model;

    private Integer promptTokens;

    private Integer completionTokens;

    private Integer latencyMs;

    private LocalDateTime createdAt;
}

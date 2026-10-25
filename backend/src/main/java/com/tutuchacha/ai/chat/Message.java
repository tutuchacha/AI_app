package com.tutuchacha.ai.chat;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 消息：role 为 user / assistant / system，status 见 V2 注释。 */
@Data
@TableName("message")
public class Message {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long conversationId;

    private String role;

    private String content;

    private Integer tokensIn;

    private Integer tokensOut;

    private Integer latencyMs;

    private String model;

    private String status;

    private String requestId;

    private LocalDateTime createdAt;
}

package com.tutuchacha.ai.chat;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 会话：一个 uid 对应一段多轮对话，消息挂在 id 下。 */
@Data
@TableName("conversation")
public class Conversation {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String uid;

    private Long userId;

    private String module;

    private Long personaId;

    private Long kbId;

    private String title;

    private Long modelId;

    private LocalDateTime lastMessageAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}

package com.tutuchacha.ai.support;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 转人工工单：客服兜底（最高引用分低于阈值）时落一条，只做 OPEN / CLOSED 两态。
 * conversation_uid / kb_id 只留痕，不建外键，会话或库删了工单仍可查。
 */
@Data
@TableName("support_ticket")
public class SupportTicket {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String conversationUid;

    private Long kbId;

    private String question;

    private Double bestScore;

    private String status;

    private String note;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}

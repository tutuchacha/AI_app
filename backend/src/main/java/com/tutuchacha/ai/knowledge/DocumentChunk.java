package com.tutuchacha.ai.knowledge;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 文档切片：M2-02 只落文本，embedding 由 M2-03 回填。 */
@Data
@TableName("document_chunk")
public class DocumentChunk {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long documentId;

    private Long kbId;

    private Integer chunkIndex;

    private String content;

    private String embedding;

    private Integer tokenCount;

    private String metadataJson;

    private LocalDateTime createdAt;
}

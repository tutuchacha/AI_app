package com.tutuchacha.ai.knowledge;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 知识库：文档与切片的归属单位，检索时按库过滤。 */
@Data
@TableName("knowledge_base")
public class KnowledgeBase {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String name;

    private String description;

    private String embeddingModel;

    private Integer chunkSize;

    private Integer chunkOverlap;

    private Integer documentCount;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}

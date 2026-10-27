package com.tutuchacha.ai.knowledge;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 知识库文档：一条记录对应一个上传文件，状态由解析流程推进。 */
@Data
@TableName("document")
public class KbDocument {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long kbId;

    private String filename;

    private String ext;

    private Long size;

    private String sha256;

    private String status;

    private Integer chunkCount;

    private String errorMsg;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}

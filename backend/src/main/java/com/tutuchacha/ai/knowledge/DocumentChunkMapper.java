package com.tutuchacha.ai.knowledge;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface DocumentChunkMapper extends BaseMapper<DocumentChunk> {

    /**
     * pgvector 检索：余弦距离 <=> 排序，HNSW 索引（idx_chunk_vec）命中；
     * 相似度 = 1 - 距离，阈值与 topK 都在库里裁掉，不再把整库切片拉进 JVM。
     */
    @Select("""
            SELECT id,
                   document_id,
                   chunk_index,
                   content,
                   1 - (embedding_vec <=> CAST(#{vector} AS vector)) AS score
            FROM document_chunk
            WHERE kb_id = #{kbId}
              AND embedding_vec IS NOT NULL
              AND 1 - (embedding_vec <=> CAST(#{vector} AS vector)) >= #{minScore}
            ORDER BY embedding_vec <=> CAST(#{vector} AS vector)
            LIMIT #{limit}
            """)
    @ConstructorArgs({
            @Arg(column = "id", javaType = Long.class),
            @Arg(column = "document_id", javaType = Long.class),
            @Arg(column = "chunk_index", javaType = Integer.class),
            @Arg(column = "content", javaType = String.class),
            @Arg(column = "score", javaType = Double.class)
    })
    List<ChunkScore> searchByVector(@Param("kbId") Long kbId,
                                    @Param("vector") String vector,
                                    @Param("minScore") double minScore,
                                    @Param("limit") int limit);
}

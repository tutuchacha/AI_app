package com.tutuchacha.ai.metrics;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用量统计只读查询：直接聚合 message，不碰正文列。
 * 只取 assistant 行（一次模型调用一行），时间下界由 Java 传入，避免方言函数。
 */
@Mapper
public interface UsageMapper {

    @Select("""
            SELECT m.tokens_in, m.tokens_out, m.latency_ms, m.status, m.created_at, c.module
              FROM message m
              LEFT JOIN conversation c ON c.id = m.conversation_id
             WHERE m.role = 'assistant' AND m.created_at >= #{since}
            """)
    List<UsageRow> selectSince(@Param("since") LocalDateTime since);

    @Select("""
            SELECT m.tokens_in, m.tokens_out, m.latency_ms, m.status, m.created_at, c.module
              FROM message m
              JOIN conversation c ON c.id = m.conversation_id
             WHERE c.user_id = #{userId} AND m.role = 'assistant' AND m.created_at >= #{since}
            """)
    List<UsageRow> selectSinceForUser(@Param("userId") Long userId, @Param("since") LocalDateTime since);
}

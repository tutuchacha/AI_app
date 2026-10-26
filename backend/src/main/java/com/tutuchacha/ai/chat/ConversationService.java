package com.tutuchacha.ai.chat;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.tutuchacha.ai.common.BusinessException;
import com.tutuchacha.ai.common.ErrorCode;
import com.tutuchacha.ai.chat.dto.ConversationDetail;
import com.tutuchacha.ai.chat.dto.ConversationSummary;
import com.tutuchacha.ai.gateway.AiMessage;
import com.tutuchacha.ai.user.User;
import com.tutuchacha.ai.user.UserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * 会话与消息落库：负责「取历史 + 存本轮」，让模型看到多轮上下文。
 * 数据库异常时降级为无记忆，保证聊天本身仍可用。
 */
@Service
public class ConversationService {

    private static final Logger log = LoggerFactory.getLogger(ConversationService.class);
    /** 带进提示词的历史条数（user + assistant 合计） */
    private static final int MAX_HISTORY = 20;
    private static final int TITLE_LENGTH = 30;
    private static final int MAX_CONVERSATIONS = 50;
    private static final int MAX_DETAIL_MESSAGES = 200;
    private static final DateTimeFormatter ISO_LOCAL_DATE_TIME = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final ConversationMapper conversationMapper;
    private final MessageMapper messageMapper;
    private final UserMapper userMapper;

    public ConversationService(ConversationMapper conversationMapper,
                               MessageMapper messageMapper,
                               UserMapper userMapper) {
        this.conversationMapper = conversationMapper;
        this.messageMapper = messageMapper;
        this.userMapper = userMapper;
    }

    /** 本轮上下文：对外 uid、历史消息、刚插入的两条消息 id。 */
    public record Context(Long conversationId, String uid, List<AiMessage> history,
                          Long userMessageId, Long assistantMessageId) {
    }

    /** 本轮结果，用于回填 assistant 消息。 */
    public record Finish(String content, int promptTokens, int completionTokens, long latencyMs, String status) {
    }

    /**
     * 取历史并写入本轮 user 消息与 assistant 占位行。
     * conversationId 不存在时按传入 uid 新建；uid 属于其他用户则 403。
     * retry=true 表示重试上一轮失败的回答：不再插入 user 消息，只补一条 assistant 占位行。
     */
    @Transactional
    public Context prepare(String username, String uid, String module, String model,
                           String content, String requestId, boolean retry) {
        String conversationUid = StringUtils.hasText(uid) ? uid : newUid();
        try {
            User user = userMapper.selectOne(Wrappers.<User>lambdaQuery().eq(User::getUsername, username));
            if (user == null) {
                log.warn("skip persistence: user not found username={}", username);
                return new Context(null, conversationUid, List.of(), null, null);
            }

            Conversation conversation = findOrCreate(conversationUid, user.getId(), module, content);
            if (retry) {
                supersedeStoppedAnswer(conversation.getId());
            }
            List<AiMessage> history = historyOf(conversation.getId());
            // 重试时若历史里已有这条提问就不再插；连接没建好就取消的情况会走到插入分支
            boolean asked = retry && questionAlreadyAsked(history, content);
            Long userMessageId = asked ? null : insertMessage(conversation.getId(), "user", content,
                    "DONE", requestId, model);
            Long assistantMessageId = insertMessage(conversation.getId(), "assistant", "",
                    "STREAMING", requestId, model);
            return new Context(conversation.getId(), conversationUid, history, userMessageId, assistantMessageId);
        } catch (DataAccessException ex) {
            log.warn("persistence unavailable, continue without memory: {}", ex.getMessage());
            return new Context(null, conversationUid, List.of(), null, null);
        }
    }

    /** 回填 assistant 消息与会话时间；没有落库时静默跳过。 */
    @Transactional
    public void finish(Context context, Finish result) {
        if (context == null || context.assistantMessageId() == null) {
            return;
        }
        try {
            Message message = new Message();
            message.setId(context.assistantMessageId());
            message.setContent(result.content());
            message.setTokensIn(result.promptTokens());
            message.setTokensOut(result.completionTokens());
            message.setLatencyMs((int) result.latencyMs());
            message.setStatus(result.status());
            messageMapper.updateById(message);

            Conversation conversation = new Conversation();
            conversation.setId(context.conversationId());
            conversation.setLastMessageAt(LocalDateTime.now());
            conversationMapper.updateById(conversation);
        } catch (DataAccessException ex) {
            log.warn("save assistant message failed: {}", ex.getMessage());
        }
    }

    /** 左侧会话列表：只返回当前用户的会话，按最后消息时间倒序。 */
    public List<ConversationSummary> listSummaries(String username, String module) {
        User user = findUser(username);
        if (user == null) {
            return List.of();
        }
        List<Conversation> rows = conversationMapper.selectList(Wrappers.<Conversation>lambdaQuery()
                .eq(Conversation::getUserId, user.getId())
                .eq(StringUtils.hasText(module), Conversation::getModule, module)
                .orderByDesc(Conversation::getLastMessageAt)
                .last("LIMIT " + MAX_CONVERSATIONS));
        return rows.stream()
                .map(row -> new ConversationSummary(row.getUid(), row.getTitle(), row.getModule(),
                        iso(row.getLastMessageAt())))
                .toList();
    }

    /** 会话详情：uid 不存在 404，属于别人 403。 */
    public ConversationDetail detail(String username, String uid) {
        User user = findUser(username);
        Conversation conversation = user == null ? null : conversationMapper.selectOne(
                Wrappers.<Conversation>lambdaQuery().eq(Conversation::getUid, uid));
        if (conversation == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "会话不存在");
        }
        if (!conversation.getUserId().equals(user.getId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权访问该会话");
        }
        List<Message> rows = messageMapper.selectList(Wrappers.<Message>lambdaQuery()
                .eq(Message::getConversationId, conversation.getId())
                .in(Message::getStatus, "DONE", "STOPPED")
                .ne(Message::getContent, "")
                .orderByAsc(Message::getId)
                .last("LIMIT " + MAX_DETAIL_MESSAGES));
        List<ConversationDetail.Item> messages = rows.stream()
                .map(row -> new ConversationDetail.Item(row.getId(), row.getRole(), row.getContent(),
                        row.getStatus(), row.getTokensIn(), row.getTokensOut(), row.getLatencyMs(),
                        row.getModel(), iso(row.getCreatedAt())))
                .toList();
        return new ConversationDetail(conversation.getUid(), conversation.getTitle(),
                conversation.getModule(), messages);
    }

    private User findUser(String username) {
        return userMapper.selectOne(Wrappers.<User>lambdaQuery().eq(User::getUsername, username));
    }

    private String iso(LocalDateTime time) {
        return time == null ? "" : time.format(ISO_LOCAL_DATE_TIME);
    }

    private Conversation findOrCreate(String uid, Long userId, String module, String content) {
        Conversation existing = conversationMapper.selectOne(
                Wrappers.<Conversation>lambdaQuery().eq(Conversation::getUid, uid));
        if (existing != null) {
            if (!userId.equals(existing.getUserId())) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "无权访问该会话");
            }
            return existing;
        }

        Conversation conversation = new Conversation();
        conversation.setUid(uid);
        conversation.setUserId(userId);
        conversation.setModule(StringUtils.hasText(module) ? module : "chat");
        conversation.setTitle(titleOf(content));
        conversation.setLastMessageAt(LocalDateTime.now());
        try {
            conversationMapper.insert(conversation);
            return conversation;
        } catch (DuplicateKeyException ex) {
            // 并发下另一个请求已建好同一 uid，复用即可
            return conversationMapper.selectOne(
                    Wrappers.<Conversation>lambdaQuery().eq(Conversation::getUid, uid));
        }
    }

    /** 历史里最后一条 user 是否就是本次提问（重试时用来避免重复落库）。 */
    private boolean questionAlreadyAsked(List<AiMessage> history, String content) {
        for (int i = history.size() - 1; i >= 0; i -= 1) {
            AiMessage message = history.get(i);
            if ("user".equals(message.role())) {
                return content.equals(message.content());
            }
        }
        return false;
    }

    /** 重试时把上一条「停止」的半截回答标成 FAILED，避免它再进上下文和历史详情。 */
    private void supersedeStoppedAnswer(Long conversationId) {
        Message last = messageMapper.selectOne(Wrappers.<Message>lambdaQuery()
                .eq(Message::getConversationId, conversationId)
                .orderByDesc(Message::getId)
                .last("LIMIT 1"));
        if (last == null || !"assistant".equals(last.getRole()) || !"STOPPED".equals(last.getStatus())) {
            return;
        }
        Message patch = new Message();
        patch.setId(last.getId());
        patch.setStatus("FAILED");
        messageMapper.updateById(patch);
    }

    private List<AiMessage> historyOf(Long conversationId) {
        List<Message> rows = messageMapper.selectList(Wrappers.<Message>lambdaQuery()
                .eq(Message::getConversationId, conversationId)
                .in(Message::getStatus, "DONE", "STOPPED")
                .ne(Message::getContent, "")
                .orderByDesc(Message::getId)
                .last("LIMIT " + MAX_HISTORY));
        List<Message> ordered = new ArrayList<>(rows);
        Collections.reverse(ordered);
        return ordered.stream()
                .map(row -> "assistant".equals(row.getRole())
                        ? AiMessage.assistant(row.getContent())
                        : AiMessage.user(row.getContent()))
                .toList();
    }

    private Long insertMessage(Long conversationId, String role, String content,
                               String status, String requestId, String model) {
        Message message = new Message();
        message.setConversationId(conversationId);
        message.setRole(role);
        message.setContent(content);
        message.setStatus(status);
        message.setRequestId(requestId);
        message.setModel(model == null ? "" : model);
        message.setTokensIn(0);
        message.setTokensOut(0);
        message.setLatencyMs(0);
        messageMapper.insert(message);
        return message.getId();
    }

    private String titleOf(String content) {
        String title = content.replaceAll("\\s+", " ").trim();
        return title.length() <= TITLE_LENGTH ? title : title.substring(0, TITLE_LENGTH);
    }

    private String newUid() {
        return "c_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }
}

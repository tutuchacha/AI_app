package com.tutuchacha.ai.support;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.tutuchacha.ai.common.BusinessException;
import com.tutuchacha.ai.common.ErrorCode;
import com.tutuchacha.ai.support.dto.SupportTicketRequest;
import com.tutuchacha.ai.support.dto.SupportTicketView;
import com.tutuchacha.ai.user.User;
import com.tutuchacha.ai.user.UserMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 转人工工单：只做「提交 / 我的列表 / 关闭」三件事，两态（OPEN / CLOSED）。
 * 所有读写都按当前登录用户过滤，别人的工单返回 403。
 */
@Service
public class SupportTicketService {

    private static final int MAX_LIST_SIZE = 20;
    private static final DateTimeFormatter ISO_LOCAL_DATE_TIME = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final SupportTicketMapper ticketMapper;
    private final UserMapper userMapper;

    public SupportTicketService(SupportTicketMapper ticketMapper, UserMapper userMapper) {
        this.ticketMapper = ticketMapper;
        this.userMapper = userMapper;
    }

    public SupportTicketView create(String username, SupportTicketRequest request) {
        User user = requireUser(username);
        SupportTicket entity = new SupportTicket();
        entity.setUserId(user.getId());
        entity.setConversationUid(request.conversationUid() == null ? "" : request.conversationUid().trim());
        entity.setKbId(request.kbId());
        entity.setQuestion(request.question().trim());
        entity.setBestScore(request.bestScore() == null ? 0d : request.bestScore());
        entity.setStatus("OPEN");
        entity.setNote("");
        ticketMapper.insert(entity);
        return toView(ticketMapper.selectById(entity.getId()));
    }

    /** 我的工单，最新的在前；只取 20 条，够左侧面板用。 */
    public List<SupportTicketView> list(String username) {
        User user = requireUser(username);
        return ticketMapper.selectList(Wrappers.<SupportTicket>lambdaQuery()
                        .eq(SupportTicket::getUserId, user.getId())
                        .orderByDesc(SupportTicket::getId)
                        .last("LIMIT " + MAX_LIST_SIZE))
                .stream()
                .map(this::toView)
                .toList();
    }

    /** 关闭工单，幂等：已关闭再点一次直接返回当前状态。 */
    public SupportTicketView close(String username, Long id) {
        User user = requireUser(username);
        SupportTicket entity = ticketMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "工单不存在");
        }
        if (!user.getId().equals(entity.getUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "没有权限处理该工单");
        }
        if (!"CLOSED".equals(entity.getStatus())) {
            entity.setStatus("CLOSED");
            ticketMapper.updateById(entity);
            entity = ticketMapper.selectById(id);
        }
        return toView(entity);
    }

    private User requireUser(String username) {
        User user = StringUtils.hasText(username)
                ? userMapper.selectOne(Wrappers.<User>lambdaQuery().eq(User::getUsername, username))
                : null;
        if (user == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "用户不存在");
        }
        return user;
    }

    private SupportTicketView toView(SupportTicket entity) {
        return new SupportTicketView(entity.getId(), entity.getQuestion(), entity.getStatus(),
                entity.getBestScore(), entity.getNote(), entity.getConversationUid(),
                iso(entity.getCreatedAt()), iso(entity.getUpdatedAt()));
    }

    private String iso(LocalDateTime time) {
        return time == null ? "" : time.format(ISO_LOCAL_DATE_TIME);
    }
}

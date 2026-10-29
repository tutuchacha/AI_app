package com.tutuchacha.ai.date;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.tutuchacha.ai.common.BusinessException;
import com.tutuchacha.ai.common.ErrorCode;
import com.tutuchacha.ai.date.dto.DateActionRequest;
import com.tutuchacha.ai.date.dto.DateActionUpdateRequest;
import com.tutuchacha.ai.date.dto.DateActionView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * D-04 执行与反馈：把建议里的动作落成待办，做完回填对方反应。
 * 只存事实（做没做 / 反应如何），判断交给复盘服务，避免前端各自解释。
 */
@Service
public class DateActionService {

    private static final int MAX_ACTIONS = 20;
    private static final DateTimeFormatter ISO_LOCAL_DATE_TIME = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final DateActionMapper actionMapper;
    private final DateAdviceMapper adviceMapper;
    private final DateService dateService;

    public DateActionService(DateActionMapper actionMapper, DateAdviceMapper adviceMapper, DateService dateService) {
        this.actionMapper = actionMapper;
        this.adviceMapper = adviceMapper;
        this.dateService = dateService;
    }

    @Transactional
    public DateActionView create(String username, DateActionRequest request) {
        if (request.targetId() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请选择对象档案");
        }
        DateTarget target = dateService.requireOwned(username, request.targetId());
        Long adviceId = requireAdviceOfTarget(target, request.adviceId());
        Long count = actionMapper.selectCount(Wrappers.<DateAction>lambdaQuery()
                .eq(DateAction::getTargetId, target.getId()));
        if (count != null && count >= MAX_ACTIONS) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "同一对象最多保留 " + MAX_ACTIONS + " 条执行记录");
        }

        DateAction action = new DateAction();
        action.setUserId(target.getUserId());
        action.setTargetId(target.getId());
        action.setAdviceId(adviceId);
        action.setTitle(truncate(request.title().trim(), 120));
        action.setDetail(truncate(trim(request.detail()), 500));
        action.setPlannedAt(request.plannedAt());
        action.setStatus(DateActionStatus.PLANNED.name());
        action.setResult(DateActionResult.UNKNOWN.name());
        action.setNote("");
        actionMapper.insert(action);
        return toView(actionMapper.selectById(action.getId()));
    }

    /** 最近 20 条，新记录在前；复盘按同一口径取数。 */
    public List<DateActionView> list(String username, Long targetId) {
        DateTarget target = dateService.requireOwned(username, targetId);
        return recent(target.getId()).stream().map(this::toView).toList();
    }

    @Transactional
    public DateActionView update(String username, Long id, DateActionUpdateRequest request) {
        DateAction action = requireOwned(username, id);
        if (StringUtils.hasText(request.status())) {
            DateActionStatus status = DateActionStatus.parse(request.status());
            action.setStatus(status.name());
            if (status != DateActionStatus.PLANNED) {
                action.setDoneAt(LocalDateTime.now());
            } else {
                // 重新打开：保留上次完成时间做历史，只把反馈清空
                action.setResult(DateActionResult.UNKNOWN.name());
            }
        }
        if (StringUtils.hasText(request.result())) {
            action.setResult(DateActionResult.parse(request.result()).name());
        }
        if (request.note() != null) {
            action.setNote(truncate(request.note().trim(), 500));
        }
        action.setUpdatedAt(LocalDateTime.now());
        actionMapper.updateById(action);
        return toView(actionMapper.selectById(id));
    }

    public void delete(String username, Long id) {
        actionMapper.deleteById(requireOwned(username, id).getId());
    }

    /** 复盘取数：同一对象的最近 5 条，倒序。 */
    public List<DateAction> recent(Long targetId) {
        return actionMapper.selectList(Wrappers.<DateAction>lambdaQuery()
                .eq(DateAction::getTargetId, targetId)
                .orderByDesc(DateAction::getId)
                .last("LIMIT " + MAX_ACTIONS));
    }

    private Long requireAdviceOfTarget(DateTarget target, Long adviceId) {
        if (adviceId == null) {
            return null;
        }
        DateAdvice advice = adviceMapper.selectById(adviceId);
        if (advice == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "来源建议不存在");
        }
        if (!target.getId().equals(advice.getTargetId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "建议不属于该对象");
        }
        return advice.getId();
    }

    private DateAction requireOwned(String username, Long id) {
        DateAction action = actionMapper.selectById(id);
        if (action == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "执行记录不存在");
        }
        dateService.requireOwned(username, action.getTargetId());
        return action;
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    private DateActionView toView(DateAction action) {
        return new DateActionView(action.getId(), action.getTargetId(), action.getAdviceId(),
                action.getTitle(), action.getDetail(),
                action.getPlannedAt() == null ? "" : action.getPlannedAt().toString(),
                action.getStatus(), statusLabel(action.getStatus()),
                action.getResult(), resultLabel(action.getResult()),
                action.getNote(), iso(action.getDoneAt()), iso(action.getCreatedAt()));
    }

    private String statusLabel(String status) {
        try {
            return DateActionStatus.valueOf(status).label();
        } catch (RuntimeException ex) {
            return DateActionStatus.PLANNED.label();
        }
    }

    private String resultLabel(String result) {
        try {
            return DateActionResult.valueOf(result).label();
        } catch (RuntimeException ex) {
            return DateActionResult.UNKNOWN.label();
        }
    }

    private String iso(LocalDateTime time) {
        return time == null ? "" : time.format(ISO_LOCAL_DATE_TIME);
    }
}

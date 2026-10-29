package com.tutuchacha.ai.date;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tutuchacha.ai.date.dto.AdviceContent;
import com.tutuchacha.ai.date.dto.DateTimelineEvent;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * D-05 时间线：把建档、建议、执行反馈、阶段变更拼成一条流，用来回看「哪一步起了作用」。
 * 只读，不改任何数据；每类最多取最近若干条，避免长历史拖慢首屏。
 */
@Service
public class DateTimelineService {

    private static final int MAX_ADVICE = 10;
    private static final int MAX_ACTION = 20;
    private static final int MAX_STAGE = 10;
    private static final int MAX_EVENTS = 40;

    private final DateService dateService;
    private final DateAdviceMapper adviceMapper;
    private final DateActionMapper actionMapper;
    private final DateStageLogMapper stageLogMapper;
    private final ObjectMapper objectMapper;

    public DateTimelineService(DateService dateService, DateAdviceMapper adviceMapper,
                               DateActionMapper actionMapper, DateStageLogMapper stageLogMapper,
                               ObjectMapper objectMapper) {
        this.dateService = dateService;
        this.adviceMapper = adviceMapper;
        this.actionMapper = actionMapper;
        this.stageLogMapper = stageLogMapper;
        this.objectMapper = objectMapper;
    }

    public List<DateTimelineEvent> timeline(String username, Long targetId) {
        DateTarget target = dateService.requireOwned(username, targetId);
        List<Dated> items = new ArrayList<>();

        items.add(new Dated(target.getCreatedAt(), new DateTimelineEvent("TARGET", "建档",
                "建档：" + target.getName(), "初始阶段「" + stageLabel(target.getStage()) + "」", "")));

        adviceMapper.selectList(Wrappers.<DateAdvice>lambdaQuery()
                        .eq(DateAdvice::getTargetId, target.getId())
                        .orderByDesc(DateAdvice::getId)
                        .last("LIMIT " + MAX_ADVICE))
                .forEach(advice -> items.add(new Dated(advice.getCreatedAt(), new DateTimelineEvent(
                        "ADVICE", "AI 建议", "建议 · " + stageLabel(advice.getStage()), goalOf(advice.getContent()), ""))));

        actionMapper.selectList(Wrappers.<DateAction>lambdaQuery()
                        .eq(DateAction::getTargetId, target.getId())
                        .orderByDesc(DateAction::getId)
                        .last("LIMIT " + MAX_ACTION))
                .forEach(action -> items.add(new Dated(
                        action.getDoneAt() == null ? action.getCreatedAt() : action.getDoneAt(),
                        new DateTimelineEvent("ACTION", "执行反馈", action.getTitle(), actionDetail(action), ""))));

        stageLogMapper.selectList(Wrappers.<DateStageLog>lambdaQuery()
                        .eq(DateStageLog::getTargetId, target.getId())
                        .orderByDesc(DateStageLog::getId)
                        .last("LIMIT " + MAX_STAGE))
                .forEach(log -> items.add(new Dated(log.getCreatedAt(), new DateTimelineEvent("STAGE", "阶段变更",
                        stageLabel(log.getFromStage()) + " → " + stageLabel(log.getToStage()),
                        "REVIEW".equals(log.getSource()) ? "复盘后应用" : "手动修改", ""))));

        return items.stream()
                .filter(item -> item.at() != null)
                .sorted(Comparator.comparing(Dated::at).reversed())
                .limit(MAX_EVENTS)
                .map(item -> new DateTimelineEvent(item.event().type(), item.event().typeLabel(),
                        item.event().title(), item.event().detail(), item.at().toString()))
                .toList();
    }

    private String actionDetail(DateAction action) {
        StringBuilder sb = new StringBuilder(statusLabel(action.getStatus()));
        sb.append(" · ").append(resultLabel(action.getResult()));
        if (StringUtils.hasText(action.getNote())) {
            sb.append(" · ").append(action.getNote().trim());
        }
        return sb.toString();
    }

    private String goalOf(String json) {
        if (!StringUtils.hasText(json)) {
            return "";
        }
        try {
            String goal = objectMapper.readValue(json, AdviceContent.class).goal();
            return goal == null ? "" : goal;
        } catch (Exception ex) {
            return "";
        }
    }

    private String stageLabel(String stage) {
        try {
            return DateStage.valueOf(stage).label();
        } catch (RuntimeException ex) {
            return DateStage.MET.label();
        }
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

    private record Dated(LocalDateTime at, DateTimelineEvent event) {
    }
}

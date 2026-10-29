package com.tutuchacha.ai.date;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.tutuchacha.ai.common.BusinessException;
import com.tutuchacha.ai.common.ErrorCode;
import com.tutuchacha.ai.date.dto.DateProfileRequest;
import com.tutuchacha.ai.date.dto.DateProfileView;
import com.tutuchacha.ai.date.dto.DateTargetRequest;
import com.tutuchacha.ai.date.dto.DateTargetView;
import com.tutuchacha.ai.user.User;
import com.tutuchacha.ai.user.UserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 恋爱助手建档：我的档案一人一份（PUT 覆盖），对象档案可多份但限 20 个。
 * 所有读写按当前登录用户过滤，越权统一 403、不存在 404。
 */
@Service
public class DateService {

    private static final int MAX_TARGETS = 20;
    private static final DateTimeFormatter ISO_LOCAL_DATE_TIME = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final DateProfileMapper profileMapper;
    private final DateTargetMapper targetMapper;
    private final UserMapper userMapper;
    private final DateStageLogMapper stageLogMapper;

    public DateService(DateProfileMapper profileMapper, DateTargetMapper targetMapper, UserMapper userMapper,
                       DateStageLogMapper stageLogMapper) {
        this.profileMapper = profileMapper;
        this.targetMapper = targetMapper;
        this.userMapper = userMapper;
        this.stageLogMapper = stageLogMapper;
    }

    /** 未建档返回 null，前端据此显示「先完善档案」。 */
    public DateProfileView profile(String username) {
        User user = requireUser(username);
        DateProfile profile = profileMapper.selectOne(Wrappers.<DateProfile>lambdaQuery()
                .eq(DateProfile::getUserId, user.getId()));
        return profile == null ? null : toView(profile);
    }

    @Transactional
    public DateProfileView saveProfile(String username, DateProfileRequest request) {
        User user = requireUser(username);
        DateProfile profile = profileMapper.selectOne(Wrappers.<DateProfile>lambdaQuery()
                .eq(DateProfile::getUserId, user.getId()));
        boolean created = profile == null;
        if (created) {
            profile = new DateProfile();
            profile.setUserId(user.getId());
        }
        profile.setNickname(trim(request.nickname()));
        profile.setGender(trim(request.gender()));
        profile.setAge(request.age());
        profile.setCity(trim(request.city()));
        profile.setOccupation(trim(request.occupation()));
        profile.setHobbies(trim(request.hobbies()));
        profile.setPersonality(trim(request.personality()));
        profile.setGoal(trim(request.goal()));
        profile.setNotes(trim(request.notes()));
        if (created) {
            profileMapper.insert(profile);
        } else {
            profileMapper.updateById(profile);
        }
        return toView(profileMapper.selectById(profile.getId()));
    }

    public List<DateTargetView> targets(String username) {
        User user = requireUser(username);
        return targetMapper.selectList(Wrappers.<DateTarget>lambdaQuery()
                        .eq(DateTarget::getUserId, user.getId())
                        .orderByDesc(DateTarget::getId)
                        .last("LIMIT " + MAX_TARGETS))
                .stream()
                .map(this::toView)
                .toList();
    }

    @Transactional
    public DateTargetView createTarget(String username, DateTargetRequest request) {
        User user = requireUser(username);
        Long count = targetMapper.selectCount(Wrappers.<DateTarget>lambdaQuery()
                .eq(DateTarget::getUserId, user.getId()));
        if (count != null && count >= MAX_TARGETS) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "最多同时跟进 " + MAX_TARGETS + " 个对象");
        }
        DateTarget target = new DateTarget();
        target.setUserId(user.getId());
        apply(target, request);
        targetMapper.insert(target);
        return toView(targetMapper.selectById(target.getId()));
    }

    @Transactional
    public DateTargetView updateTarget(String username, Long id, DateTargetRequest request) {
        User user = requireUser(username);
        DateTarget target = requireOwned(user, id);
        String before = target.getStage();
        apply(target, request);
        targetMapper.updateById(target);
        if (!before.equals(target.getStage())) {
            DateStageLog log = new DateStageLog();
            log.setUserId(user.getId());
            log.setTargetId(target.getId());
            log.setFromStage(before);
            log.setToStage(target.getStage());
            log.setSource("REVIEW".equalsIgnoreCase(request.stageSource()) ? "REVIEW" : "MANUAL");
            stageLogMapper.insert(log);
        }
        return toView(targetMapper.selectById(id));
    }

    public void deleteTarget(String username, Long id) {
        User user = requireUser(username);
        targetMapper.deleteById(requireOwned(user, id).getId());
    }

    /** D-02 起出建议、记执行都要先过这一关。 */
    public DateTarget requireOwned(String username, Long id) {
        return requireOwned(requireUser(username), id);
    }

    private DateTarget requireOwned(User user, Long id) {
        DateTarget target = targetMapper.selectById(id);
        if (target == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "对象档案不存在");
        }
        if (!user.getId().equals(target.getUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "没有权限操作该档案");
        }
        return target;
    }

    private void apply(DateTarget target, DateTargetRequest request) {
        target.setName(request.name().trim());
        target.setChannel(trim(request.channel()));
        target.setStage(DateStage.parse(request.stage()).name());
        target.setAge(request.age());
        target.setCity(trim(request.city()));
        target.setInterests(trim(request.interests()));
        target.setTaboos(trim(request.taboos()));
        target.setNotes(trim(request.notes()));
        target.setLastContactAt(request.lastContactAt());
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

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private DateProfileView toView(DateProfile profile) {
        return new DateProfileView(profile.getId(), profile.getNickname(), profile.getGender(),
                profile.getAge(), profile.getCity(), profile.getOccupation(), profile.getHobbies(),
                profile.getPersonality(), profile.getGoal(), profile.getNotes(), iso(profile.getUpdatedAt()));
    }

    private DateTargetView toView(DateTarget target) {
        return new DateTargetView(target.getId(), target.getName(), target.getChannel(),
                target.getStage(), label(target.getStage()), target.getAge(), target.getCity(),
                target.getInterests(), target.getTaboos(), target.getNotes(),
                target.getLastContactAt() == null ? "" : target.getLastContactAt().toString(),
                iso(target.getCreatedAt()), iso(target.getUpdatedAt()));
    }

    private String label(String stage) {
        try {
            return DateStage.valueOf(stage).label();
        } catch (RuntimeException ex) {
            return DateStage.MET.label();
        }
    }

    private String iso(LocalDateTime time) {
        return time == null ? "" : time.format(ISO_LOCAL_DATE_TIME);
    }
}

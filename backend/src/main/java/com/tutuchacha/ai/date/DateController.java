package com.tutuchacha.ai.date;

import com.tutuchacha.ai.common.Result;
import com.tutuchacha.ai.date.dto.DateActionRequest;
import com.tutuchacha.ai.date.dto.DateActionUpdateRequest;
import com.tutuchacha.ai.date.dto.DateActionView;
import com.tutuchacha.ai.date.dto.DateAdviceRequest;
import com.tutuchacha.ai.date.dto.DateAdviceView;
import com.tutuchacha.ai.date.dto.DateAskRequest;
import com.tutuchacha.ai.date.dto.DateAskView;
import com.tutuchacha.ai.date.dto.DateProfileRequest;
import com.tutuchacha.ai.date.dto.DateProfileView;
import com.tutuchacha.ai.date.dto.DateReviewRequest;
import com.tutuchacha.ai.date.dto.DateReviewView;
import com.tutuchacha.ai.date.dto.DateTargetRequest;
import com.tutuchacha.ai.date.dto.DateTargetView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 恋爱助手建档：我的画像 + 目标对象档案。策略与反馈在 D-02/D-03 接。 */
@Tag(name = "恋爱助手", description = "个人画像、对象档案与关系阶段")
@RestController
@RequestMapping("/api/date")
public class DateController {

    private final DateService dateService;
    private final DateAdviceService adviceService;
    private final DateAskService askService;
    private final DateActionService actionService;
    private final DateReviewService reviewService;

    public DateController(DateService dateService, DateAdviceService adviceService, DateAskService askService,
                          DateActionService actionService, DateReviewService reviewService) {
        this.dateService = dateService;
        this.adviceService = adviceService;
        this.askService = askService;
        this.actionService = actionService;
        this.reviewService = reviewService;
    }

    @Operation(summary = "我的画像", description = "未建档返回 data=null",
            security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/profile")
    public Result<DateProfileView> profile(Authentication authentication) {
        return Result.ok(dateService.profile(authentication.getName()));
    }

    @Operation(summary = "保存我的画像", description = "整体覆盖，未传字段即清空",
            security = @SecurityRequirement(name = "bearerAuth"))
    @PutMapping("/profile")
    public Result<DateProfileView> saveProfile(@Valid @RequestBody DateProfileRequest request,
                                               Authentication authentication) {
        return Result.ok(dateService.saveProfile(authentication.getName(), request));
    }

    @Operation(summary = "对象列表", description = "id 倒序，最多 20 个",
            security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/targets")
    public Result<List<DateTargetView>> targets(Authentication authentication) {
        return Result.ok(dateService.targets(authentication.getName()));
    }

    @Operation(summary = "新建对象档案", security = @SecurityRequirement(name = "bearerAuth"))
    @PostMapping("/targets")
    public Result<DateTargetView> createTarget(@Valid @RequestBody DateTargetRequest request,
                                               Authentication authentication) {
        return Result.ok(dateService.createTarget(authentication.getName(), request));
    }

    @Operation(summary = "更新对象档案", security = @SecurityRequirement(name = "bearerAuth"))
    @PutMapping("/targets/{id}")
    public Result<DateTargetView> updateTarget(@PathVariable Long id,
                                               @Valid @RequestBody DateTargetRequest request,
                                               Authentication authentication) {
        return Result.ok(dateService.updateTarget(authentication.getName(), id, request));
    }

    @Operation(summary = "删除对象档案", security = @SecurityRequirement(name = "bearerAuth"))
    @DeleteMapping("/targets/{id}")
    public Result<Void> deleteTarget(@PathVariable Long id, Authentication authentication) {
        dateService.deleteTarget(authentication.getName(), id);
        return Result.ok();
    }

    @Operation(summary = "生成下一步建议", description = "按画像 + 对方档案 + 阶段调模型，并补高德地点",
            security = @SecurityRequirement(name = "bearerAuth"))
    @PostMapping("/advice")
    public Result<DateAdviceView> createAdvice(@Valid @RequestBody DateAdviceRequest request,
                                               Authentication authentication) {
        return Result.ok(adviceService.create(authentication.getName(), request.targetId()));
    }

    @Operation(summary = "建议历史", description = "同一对象最近 5 条，倒序",
            security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/advice")
    public Result<List<DateAdviceView>> adviceHistory(@RequestParam Long targetId,
                                                      Authentication authentication) {
        return Result.ok(adviceService.history(authentication.getName(), targetId));
    }

    @Operation(summary = "约会问答", description = "「今天去哪玩 / 晚上吃什么」→ 建议 + 高德地点",
            security = @SecurityRequirement(name = "bearerAuth"))
    @PostMapping("/ask")
    public Result<DateAskView> ask(@Valid @RequestBody DateAskRequest request, Authentication authentication) {
        return Result.ok(askService.ask(authentication.getName(), request));
    }

    @Operation(summary = "新建执行记录", description = "可来自某条建议的动作，也可手写",
            security = @SecurityRequirement(name = "bearerAuth"))
    @PostMapping("/actions")
    public Result<DateActionView> createAction(@Valid @RequestBody DateActionRequest request,
                                               Authentication authentication) {
        return Result.ok(actionService.create(authentication.getName(), request));
    }

    @Operation(summary = "执行记录列表", description = "同一对象最近 20 条，新在前",
            security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/actions")
    public Result<List<DateActionView>> actions(@RequestParam Long targetId, Authentication authentication) {
        return Result.ok(actionService.list(authentication.getName(), targetId));
    }

    @Operation(summary = "回填执行与反馈", description = "只改传了的字段；DONE 时写完成时间",
            security = @SecurityRequirement(name = "bearerAuth"))
    @PutMapping("/actions/{id}")
    public Result<DateActionView> updateAction(@PathVariable Long id,
                                               @RequestBody DateActionUpdateRequest request,
                                               Authentication authentication) {
        return Result.ok(actionService.update(authentication.getName(), id, request));
    }

    @Operation(summary = "删除执行记录", security = @SecurityRequirement(name = "bearerAuth"))
    @DeleteMapping("/actions/{id}")
    public Result<Void> deleteAction(@PathVariable Long id, Authentication authentication) {
        actionService.delete(authentication.getName(), id);
        return Result.ok();
    }

    @Operation(summary = "复盘", description = "最近建议 + 执行反馈 → 继续 / 换策略 / 降速 / 暂停，并建议阶段",
            security = @SecurityRequirement(name = "bearerAuth"))
    @PostMapping("/review")
    public Result<DateReviewView> review(@Valid @RequestBody DateReviewRequest request,
                                         Authentication authentication) {
        return Result.ok(reviewService.review(authentication.getName(), request.targetId()));
    }
}

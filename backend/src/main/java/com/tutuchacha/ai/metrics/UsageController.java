package com.tutuchacha.ai.metrics;

import com.tutuchacha.ai.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 用量看板接口：前端只打这一个口，Prometheus 地址不外露。 */
@Tag(name = "用量看板", description = "调用量 / Token / P95 / 错误率")
@RestController
@RequestMapping("/api/metrics")
public class UsageController {

    private final UsageService usageService;

    public UsageController(UsageService usageService) {
        this.usageService = usageService;
    }

    @Operation(summary = "用量统计", description = "today = 全局，mine = 当前登录用户；days 取 1~30",
            security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/usage")
    public Result<UsageView> usage(@RequestParam(defaultValue = "7") int days,
                                   Authentication authentication) {
        return Result.ok(usageService.usage(authentication.getName(), days));
    }
}

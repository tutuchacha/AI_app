package com.tutuchacha.ai.support;

import com.tutuchacha.ai.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 客服策略下发：阈值改了前端刷新即生效，不用发版。 */
@Tag(name = "智能客服", description = "置信阈值与转人工开关")
@RestController
@RequestMapping("/api/support")
public class SupportController {

    private final SupportProperties properties;

    public SupportController(SupportProperties properties) {
        this.properties = properties;
    }

    @Operation(summary = "客服策略", security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/policy")
    public Result<SupportPolicy> policy() {
        return Result.ok(new SupportPolicy(properties.minScoreOrDefault(),
                properties.topKOrDefault(), properties.handoffEnabledOrDefault()));
    }
}

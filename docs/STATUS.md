# 项目状态

> 仪表盘：只写当前，≤35 行 / 1.8k 字符。历史去 progress，决策去 ADR。

- 更新时间：2026-10-24
- 阶段：**M0 后端 B01–B05 完成，只剩前端联调**
- 代码提交：`f69abdb`（B05；B04 为 `bb3c827`）
- 数据库：`127.0.0.1:3306 / ai_app`，Flyway `V1` 已执行
- 鉴权：BCrypt + JWT（HS256，2h）
- 模型：百炼 `qwen-plus` 已验证（Key 在环境变量）
- 远程：`git@github.com:tutuchacha/AI_app.git`（已同步）

## 下一步（按顺序）

1. `M0-B06` 前端接真实登录：`api/http.ts`、`stores/auth.ts`、`LoginView`
2. 保留 `VITE_USE_MOCK=true` 离线模式；联调后 M0 收尾
3. M1：会话/消息表、SSE 聊天、`AiGateway`

## 已完成

- 前端 8 页 + Mock 流式 + 学习提示（`b75358b`）
- B01 骨架 / B02 建库 / B03 统一响应 / B04 登录 JWT
- B05：Swagger（3 个 auth 接口 + bearerAuth）、`/actuator/prometheus` JVM 指标
- 测试 17 passed；真实服务 Swagger 200、Prometheus 200
- 文档：架构图、数据库手册、接口约定、ADR-0003、治理规则

## 阻塞

- 无（换终端/IDE 后需重启读取 `DASHSCOPE_API_KEY`）

## 恢复命令

```powershell
cd D:\AI_learn\backend
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=local   # 8080 / swagger-ui.html
cd ..\frontend; npm run dev                                    # 5173
```

提交前：`pwsh scripts/doc-check.ps1`。

# 项目状态

> 仪表盘：只写当前，≤35 行 / 1.8k 字符。历史去 progress，决策去 ADR。

- 更新时间：2026-10-25
- 阶段：**M1 进行中**（M1-01、M1-02 完成）
- 代码提交：`c76863a`（M1-01）；M1-02 本次
- 数据库：`127.0.0.1:3306 / ai_app`，Flyway `V1` + `V2 chat` 已执行
- 鉴权：BCrypt + JWT（HS256，2h）；前端 `api/http.ts` 自动带 token
- 模型：百炼 `qwen-plus`，`AiGateway` 统一下发；真实调用 48/24 tokens，约 1.8s
- 远程：`git@github.com:tutuchacha/AI_app.git`（已同步）

## 下一步（按顺序）

1. `M1-03` SSE `/api/chat/stream`（`meta/delta/usage/done/error`）
2. `M1-04` 前端 `SseChatTransport` 替换 `mockTransport`
3. `M1-05` 会话/消息落库、列表与历史

## 已完成

- M0：前端 8 页 + 真实登录；后端骨架/建库/统一响应/JWT/Swagger/指标
- M1-01：`V2__chat.sql` 建 `conversation`、`message`
- M1-02：`gateway/`（`AiGateway` + Spring AI 实现）、`chat/` 编排、`POST /api/chat`
- 验证：24 个测试通过；真实登录 → `/api/chat` 返回回答与 token/耗时
- 文档：`database.md`、`chat.md`、`backend.md`、`ARCHITECTURE.md`、`02-接口约定.md`

## 阻塞

- 无（`app.ai.base-url` 不要带 `/v1`；未配 Key 时启动正常、调用返回 503）

## 恢复命令

```powershell
cd D:\AI_learn\backend
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=local   # 8080
cd ..\frontend; npm run dev                                    # 5173
```

演示账号 `demo / demo1234`；提交前 `pwsh scripts/doc-check.ps1`。

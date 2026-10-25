# 项目状态

> 仪表盘：只写当前，≤35 行 / 1.8k 字符。历史去 progress，决策去 ADR。

- 更新时间：2026-10-25
- 阶段：**M1 进行中**（M1-01 建表完成，下一步 M1-02）
- 代码提交：`c76863a`（M1-01；M0 收尾 `e414ff2`）
- 数据库：`127.0.0.1:3306 / ai_app`，Flyway `V1` + `V2 chat` 已执行
- 鉴权：BCrypt + JWT（HS256，2h）；前端 `api/http.ts` 自动带 token
- 模型：百炼 `qwen-plus`（Key 在环境变量，M1-02 接入）
- 远程：`git@github.com:tutuchacha/AI_app.git`（已同步）

## 下一步（按顺序）

1. `M1-02` `AiGateway` 接百炼 `qwen-plus`，提供非流式 `/api/chat`
2. `M1-03` SSE `/api/chat/stream`（事件 `meta/delta/usage/done/error`）
3. `M1-04` 前端 `SseChatTransport` 替换 `mockTransport`

## 已完成

- M0：前端 8 页 + 真实登录；后端骨架/建库/统一响应/JWT/Swagger/指标
- M1-01：`V2__chat.sql` 建 `conversation`、`message`（utf8mb4、注释、级联、索引）
- 验证：Flyway `version=2 success=1`；两表与 FK 已在库中确认
- 文档：`database.md` 表字典 + ER 图、`chat.md` 进度、`backend.md` 速览
- 测试：后端 17 passed；`typecheck` / `build` 通过

## 阻塞

- 无（IDEA 启动需 JDK 21；演示账号 `demo / demo1234`）

## 恢复命令

```powershell
cd D:\AI_learn\backend
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=local   # 8080
cd ..\frontend; npm run dev                                    # 5173
```

提交前：`pwsh scripts/doc-check.ps1`。

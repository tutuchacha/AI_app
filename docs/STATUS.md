# 项目状态

> 仪表盘：只写当前，≤35 行 / 1.8k 字符。历史去 progress，决策去 ADR。

- 更新时间：2026-10-26
- 阶段：**M1 进行中**（M1-01 ~ M1-05 完成）
- 代码提交：`d3a5658`（交互修复）；M1-05 `4c36a6c`
- 数据库：Flyway `V1` + `V2 chat` + `V3 conversation uid` 已执行
- 鉴权：BCrypt + JWT（HS256，2h）；`SecurityConfig` 放行 `ASYNC/ERROR`
- 模型：百炼 `qwen-plus`；`max-tokens` 默认 4096（`AI_MAX_TOKENS` 可覆盖）
- 远程：`git@github.com:tutuchacha/AI_app.git`

## 下一步（按顺序）

1. `M1-06` token/时延指标接 Grafana
2. `M1-05` 补：失败重试（重新生成最后一条）
3. `M1-07` WebSocket 传输对照

## 已完成

- M0：前端 8 页 + 真实登录；后端骨架/建库/统一响应/JWT/Swagger/指标
- M1-01：`V2__chat.sql` 建 `conversation`、`message`
- M1-02：`gateway/`（`AiGateway` + Spring AI 实现）、`POST /api/chat`
- M1-03：`POST /api/chat/stream`，事件 `meta/delta/usage/done/error`，断连即取消
- M1-04：`sseTransport.ts` 接流；模型下拉、用量/耗时页脚、停止生效
- M1-05：落库 + 最近 20 条上下文；`GET /api/conversations`、`GET /api/conversations/{uid}`；左侧列表真实数据、点开恢复历史、每轮结束刷新
- 交互：`KeepAlive` 保留切页状态；切页回来固定最新；右侧对话导航（横条 + hover 列表 + 跳转高亮）
- 实测：刷新 `/chat` 自动恢复最近会话 8 条并贴底；新对话首条消息后列表 21→22 且置顶
- 验证：35 后端测试通过；typecheck/build 通过；doc-check 通过

## 阻塞

- 无（`app.ai.base-url` 不带 `/v1`；未配 Key 时启动正常、调用返回 503）

## 恢复命令

```powershell
cd D:\AI_learn\backend
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=local   # 8080
cd ..\frontend; npm run dev                                    # 5173
```

演示账号 `demo / demo1234`；提交前 `pwsh scripts/doc-check.ps1`。

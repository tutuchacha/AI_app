# 项目状态

> 仪表盘：只写当前，≤35 行 / 1.8k 字符。历史去 progress，决策去 ADR。

- 更新时间：2026-10-26
- 阶段：**M1 进行中**（M1-01 ~ M1-06 完成）
- 代码提交：`baab40a`（M1-05 会话列表）；M1-06 见下一条提交
- 数据库：Flyway `V1` + `V2 chat` + `V3 conversation uid` 已执行
- 鉴权：BCrypt + JWT（HS256，2h）；`SecurityConfig` 放行 `ASYNC/ERROR`
- 模型：百炼 `qwen-plus`；`max-tokens` 默认 4096（`AI_MAX_TOKENS` 可覆盖）
- 远程：`git@github.com:tutuchacha/AI_app.git`

## 下一步（按顺序）

1. `M1-06` 收尾：装 Docker 后跑 `ops/docker-compose.yml`，确认 Grafana 看板
2. `M1-05` 补：失败重试（重新生成最后一条）
3. `M1-07` WebSocket 传输对照

## 已完成

- M0：前端 8 页 + 真实登录；后端骨架/建库/统一响应/JWT/Swagger/指标
- M1-01：`V2__chat.sql` 建 `conversation`、`message`
- M1-02：`gateway/`（`AiGateway` + Spring AI 实现）、`POST /api/chat`
- M1-03：`POST /api/chat/stream`，事件 `meta/delta/usage/done/error`，断连即取消
- M1-04：`sseTransport.ts` 接流；模型下拉、用量/耗时页脚、停止生效
- M1-05：落库 + 最近 20 条上下文；会话列表/详情接口；左侧列表真实数据、恢复历史
- M1-06：`ChatMetrics` 埋点（请求/结果/token/耗时/进行中流）；`ops/` 下 Prometheus + Grafana 编排与看板
- 交互：`KeepAlive` 保留切页状态；切页回来固定最新；右侧对话导航（横条 + hover 列表 + 跳转高亮）
- 实测：`ai_chat_requests_total{status="done|stopped"}`、token、P95、`stream_active` 均正确；停止后 gauge 回落 0
- 验证：37 后端测试通过；typecheck/build/doc-check 通过

## 阻塞

- 本机未装 Docker：Grafana 面板未实跑（Prometheus 指标已用 `/actuator/prometheus` 验证）

## 恢复命令

```powershell
cd D:\AI_learn\backend
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=local   # 8080
cd ..\frontend; npm run dev                                    # 5173
```

演示账号 `demo / demo1234`；提交前 `pwsh scripts/doc-check.ps1`。

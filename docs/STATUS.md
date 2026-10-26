# 项目状态

> 仪表盘：只写当前，≤35 行 / 1.8k 字符。历史去 progress，决策去 ADR。

- 更新时间：2026-10-26
- 阶段：**M1 完成**（M1-01 ~ M1-09 + 失败重试）
- 数据库：Flyway `V1` + `V2 chat` + `V3 conversation uid` 已执行
- 鉴权：BCrypt + JWT（HS256，2h）；`SecurityConfig` 放行 `ASYNC/ERROR` 与 `/ws/**`
- 模型：百炼 `qwen-plus`；`max-tokens` 默认 4096；超时默认 60s
- 远程：`git@github.com:tutuchacha/AI_app.git`

## 下一步（按顺序）

1. **M2 RAG**：知识库表、文档上传解析、切片与检索、`citation` 事件
2. 装 Docker 后跑 `ops/docker-compose.yml`，确认 Grafana 看板
3. 可选：多实例限流换 Redis（M6）

## 已完成

- M0：前端 8 页 + 真实登录；后端骨架/建库/统一响应/JWT/Swagger/指标
- M1-01：`V2__chat.sql` 建 `conversation`、`message`
- M1-02：`gateway/`（`AiGateway` + Spring AI 实现）、`POST /api/chat`
- M1-03：`POST /api/chat/stream`，事件 `meta/delta/usage/done/error`，断连即取消
- M1-04：`sseTransport.ts` 接流；模型下拉、用量/耗时页脚、停止生效
- M1-05：落库 + 最近 20 条上下文；会话列表/详情；失败重试（不重复提问）
- M1-06：`ChatMetrics` 埋点；`ops/` 下 Prometheus + Grafana 编排与看板
- M1-07：`/ws/chat?token=JWT` + `WsChatTransport`；头部 `SSE / WS` 切换并记忆
- M1-08：单机限流 20 轮/分钟（429 / `RATE_LIMITED`）；模型超时 60s（504 / `LLM_TIMEOUT`）；上游 429 同样映射
- M1-09：WS 改持久连接（一次握手多轮）；`stop` 不断连、20s 心跳、断线重连；页脚显示握手/复用/首字耗时
- 实测：WS 握手 1 次连跑 4 轮，stop 后继续可用；切 SSE 正常、切回 WS 重新握手；限流/超时提示均可重试
- 验证：48 后端测试通过；typecheck/build/doc-check 通过

## 阻塞

- 本机未装 Docker：Grafana 面板未实跑（Prometheus 指标已用 `/actuator/prometheus` 验证）

## 恢复命令

```powershell
cd D:\AI_learn\backend
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=local   # 8080
cd ..\frontend; npm run dev                                    # 5173
```

演示账号 `demo / demo1234`；提交前 `pwsh scripts/doc-check.ps1`。

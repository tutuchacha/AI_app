# 项目状态

> 仪表盘：只写当前，≤35 行 / 1.8k 字符。历史去 progress，决策去 ADR。

- 更新时间：2026-10-27
- 阶段：**M1 完成**；M2 RAG 进行中（M2-01 起步）
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
- M1-01~05：`V2` 会话消息表、`AiGateway`、`/api/chat`、SSE 流式、落库 + 最近 20 条上下文
- M1-06~09：Prometheus 埋点、`/ws/chat` 持久连接（心跳重连）、单机限流 429、模型超时 504
- M1-10：容器化部署件（backend/frontend Dockerfile、nginx、compose + monitoring profile）— 未实跑
- 验证：48 后端测试通过；typecheck/build/doc-check 通过

## 阻塞

- 本机未装 Docker：镜像/compose 与 Grafana 面板未实跑（Prometheus 已用 `/actuator/prometheus` 验证）

## 恢复命令

```powershell
cd D:\AI_learn\backend
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=local   # 8080
cd ..\frontend; npm run dev                                    # 5173
```

演示账号 `demo / demo1234`；提交前 `pwsh scripts/doc-check.ps1`。

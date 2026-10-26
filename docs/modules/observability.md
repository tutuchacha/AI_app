# 可观测性（M1-06）

> 指标定义、Prometheus/Grafana 启动方式、看板说明。日志与 TraceId 见 `../02-接口约定.md`。

## 0. 速览（做 M1-06 只读这节）

- 采集：Micrometer → `/actuator/prometheus`（已暴露，无需登录）
- 业务指标：`ai_chat_requests_total`、`ai_chat_tokens_total`、`ai_chat_latency_seconds`、`ai_chat_stream_active`
- 埋点：`ChatOrchestrator` → `ChatMetrics`（done / stopped / failed）
- 启动：`docker compose -f ops/docker-compose.yml up -d` → Prometheus `:9090`、Grafana `:3000`
- 看板：`ops/grafana/dashboards/ai-chat.json` 自动挂载到「星屿 AI」目录
- 验证：`pwsh ops/smoke-metrics.ps1`（只查指标名，不需要 Docker）

## 1. 指标

| 指标 | 类型 | 标签 | 含义 |
| --- | --- | --- | --- |
| `ai_chat_requests_total` | counter | `module` `model` `status` | 每轮对话计数；status = `done` / `stopped` / `failed` |
| `ai_chat_tokens_total` | counter | `type` = `input` / `output` | token 消耗 |
| `ai_chat_latency_seconds` | timer | `module` `model` | 一轮耗时（含模型生成），出 `quantile=0.5/0.95` 与 `_count` |
| `ai_chat_stream_active` | gauge | — | 正在进行的 SSE 流数量 |

JVM、HTTP、连接池等指标由 Actuator 自带（`jvm_memory_used_bytes` 等）。

## 2. 埋点位置

- `ChatMetrics`（`chat/`）：统一封 `Counter/Timer/Gauge`，标签缺省补 `chat` / `unknown`
- `ChatOrchestrator.chat()`：成功 `done`，异常 `failed` 后原样抛出
- `ChatOrchestrator.stream()`：`concatWith` 里 `done`、`onErrorResume` 里 `failed`、`doOnCancel` 里 `stopped`
- `stream_active`：`doOnSubscribe` +1，`doFinally` -1（完成/异常/取消都会回落）

## 3. 启动与验证

```powershell
# 1) 后端先跑起来（8080），并至少发一轮对话
docker compose -f ops/docker-compose.yml up -d
pwsh ops/smoke-metrics.ps1
```

- Prometheus targets：<http://127.0.0.1:9090/targets>，`ai-app` 应为 UP（抓 `host.docker.internal:8080/actuator/prometheus`，15s 一次）
- Grafana：<http://127.0.0.1:3000>（`admin / admin`）→「星屿 AI」→「星屿 AI · 聊天」
- 没有 Docker 时：只能看 <http://127.0.0.1:8080/actuator/prometheus> 原文，Grafana 面板待装 Docker 后验证

## 4. 看板面板

1. 请求速率（按 status）：`sum by (status) (rate(ai_chat_requests_total[5m]))`
2. P95 延迟（按 model）：`ai_chat_latency_seconds{quantile="0.95"}`
3. Token 速率（input/output）：`sum by (type) (rate(ai_chat_tokens_total[5m]))`
4. 进行中的流：`ai_chat_stream_active`
5. 失败率：`failed / total`（5 分钟窗口）

## 5. 已知限制

- Timer 用 `publishPercentiles` 出 `quantile` 系列，未开 histogram bucket；要用 `histogram_quantile` 时给 builder 加 `publishPercentileHistogram()`
- 指标只在进程内累计，重启清零（跨重启统计靠 `message` 表）
- 暂无告警规则与 Grafana 告警通道（M6 再做）

# 可观测性（M1-06）

> 指标定义、Prometheus/Grafana 启动方式、看板说明。日志与 TraceId 见 `../02-接口约定.md`。

## 0. 速览（做 M1-06 只读这节）

- 采集：Micrometer → `/actuator/prometheus`（已暴露，无需登录）
- 业务指标：`ai_chat_requests_total`、`ai_chat_tokens_total`、`ai_chat_latency_seconds`、`ai_chat_stream_active`
- 埋点：`ChatOrchestrator` → `ChatMetrics`（done / stopped / failed）
- 部署：**CentOS VM `192.168.101.137`**（Docker）→ Prometheus `:9090`、Grafana `:3000`
- 看板：`ops/grafana/dashboards/ai-chat.json` 自动挂载到「星屿 AI」目录（uid `ai-chat`）
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
# 1) 后端先跑起来（8080），并至少发一轮对话，指标才有值
cd D:\AI_learn\backend; .\mvnw.cmd spring-boot:run
# 2) VM 上起 PG + Prometheus + Grafana（文件在 /opt/xingyu，见 ops/docker-compose.vm.yml）
ssh root@192.168.101.137 "cd /opt/xingyu && docker compose -f docker-compose.vm.yml up -d"
```

- Prometheus targets：<http://192.168.101.137:9090/targets>，`ai-app` 应为 UP
  （抓 `host.docker.internal:8080/actuator/prometheus`，15s 一次；compose 里把
  `host.docker.internal` 映射到 `BACKEND_HOST`，本机部署时用 `host-gateway`）
- Grafana：<http://192.168.101.137:3000>（`admin / xy_grafana_2026`）→「星屿 AI」→「星屿 AI · 聊天」
- 全容器部署（后端也在 Docker 里）用 `ops/docker-compose.yml` + `--profile monitoring`，
  Prometheus 抓 compose 网络内的 `backend:8080`（`prometheus/prometheus.yml`）
- 没有 Docker 时：只能看 <http://127.0.0.1:8080/actuator/prometheus> 原文，或本机跑
  `prometheus.exe --config.file=ops/prometheus/prometheus-local.yml`

## 4. 看板面板

1. 请求数（近 5 分钟）：`sum by (status) (increase(ai_chat_requests_total[5m]))`
2. P95 延迟（按 model）：`ai_chat_latency_seconds{quantile="0.95"}`
3. Token 消耗（近 5 分钟）：`sum by (type) (increase(ai_chat_tokens_total[5m]))`
4. 进行中的流：`ai_chat_stream_active`（stat）
5. 失败率：`failed / total`（5 分钟窗口，无失败补 `or vector(0)` 画 0 线）

> 低流量下 `rate()` 只有千分之几，图表几乎贴底，所以计数面板用 `increase()`。

## 5. 验证记录（2026-10-27，VM 部署）

- `up{job="ai-app"} = 1`，目标实例 `host.docker.internal:8080`
- 一轮真实问答后：`ai_chat_requests_total{status="done"} = 1`、
  `ai_chat_tokens_total{type="input"}` 286 / `output` 72、P95 ≈ 2.2s
- Grafana 五个面板均出数，失败率 0%、进行中的流 0

## 6. 已知限制

- Timer 用 `publishPercentiles` 出 `quantile` 系列，未开 histogram bucket；要用 `histogram_quantile` 时给 builder 加 `publishPercentileHistogram()`
- 指标只在进程内累计，重启清零（跨重启统计靠 `message` 表）
- 暂无告警规则与 Grafana 告警通道（M6 再做）
- VM 1.9G 内存 / 2.1G 剩余磁盘，只放 PG + 监控；后端仍跑 Windows

# ADR 0004：单机限流与模型调用超时

- 状态：已采纳（M1-08）
- 日期：2026-10-26

## 背景

M1 聊天接口对外可用后，需要防止误触连点、脚本刷接口，以及模型长时间无响应把连接挂死。
当前是单实例 + 个人项目，暂不引入 Redis 或网关层限流。

## 决策

1. **限流**：`ChatRateLimiter` 用单机固定 60s 窗口，按用户名计数，默认 20 轮/分钟。
   - 配置：`app.chat.rate-limit-per-minute`（环境变量 `CHAT_RATE_LIMIT`），`0` 关闭。
   - 超限抛 `BusinessException(TOO_MANY_REQUESTS)`：REST 返回 429，SSE/WS 发 `RATE_LIMITED` 错误事件。
   - 窗口按整分钟切换，不保证严格滑动；只防误触与刷接口，不追求精确配额。
2. **超时**：网关层统一加 `timeout`，默认 60s。
   - 配置：`app.ai.timeout`（环境变量 `AI_TIMEOUT`）。
   - `Mono/Flux.timeout` 触发后映射为 `GATEWAY_TIMEOUT`：REST 504，流式事件 `LLM_TIMEOUT`。
3. 上游返回 429 时同样映射为 `TOO_MANY_REQUESTS`，与本地限流共用前端提示。

## 后果

- 多实例部署时每个实例各自计数，需要换成 Redis 或网关限流（M6 再处理）。
- 超时按“两个事件之间的间隔”计算，长回答持续有增量时不会被误杀。
- 前端只按错误码展示文案，无需区分本地限流还是上游限流。

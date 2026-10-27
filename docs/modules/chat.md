# 聊天模块（M1）

> 只描述聊天与流式传输。RAG 见 `rag.md`（M2），角色卡见 `companion.md`（M4）。
> 决策背景见 `../adr/0002-streaming-transport.md`。

## 0. 速览

- 目标：多轮会话、流式、停止、落库；SSE 为主，WS 为对照
- 唯一入口：`ChatOrchestrator`；输出统一 `ChatEvent` 流
- 接口：`POST /api/chat/stream`（SSE）、`WS /ws/chat`；事件 `meta/delta/citation/usage/done/error`
- 关键类：`ChatOrchestrator`、`ChatTransport`、`SseTransport`、`WsTransport`
- 验收：前端停止能中断后端；SSE 与 WS 输出一致
- 进度：M1-01~09 完成（上下文、落库、历史恢复、WS 持久连接、限流与超时）；M2-05 接入知识库引用；指标见 `observability.md`

## 1. 目标

- 多轮会话、流式输出、停止与重试；同一套逻辑支持 SSE 与 WS。
- 会话与消息落 PostgreSQL，刷新可恢复；token/时延/错误进 Grafana。

## 2. 分层

```
Controller/Handler → ChatOrchestrator → AiGateway → Spring AI ChatModel
                          ├─ ChatMemory（短期）
                          ├─ ConversationService（落库）
                          └─ UsageRecorder（指标）
```

- `ChatOrchestrator` 是唯一业务入口，输出 `ChatEvent` 流；传输层只负责编码与连接生命周期。
- RAG 检索发生在拼提示词前：`ConversationService` 落库、`RetrievalService` 取片段、`UsageRecorder` 记指标。

## 3. 接口

### SSE（默认，M1）

```http
POST /api/chat/stream
Authorization: Bearer <jwt>
Content-Type: application/json

{ "conversationId": "c_8f3a2b1c9d0e", "content": "你好", "module": "chat", "model": "qwen-plus", "kbId": 12 }
```

`conversationId` 留空 → 后端生成 `c_` 前缀并落库；带已有 ID → 读取该会话最近 20 条消息作为上下文。
`kbId` 可选：先校验库归属再落库，命中片段拼进系统提示词（只依据资料回答）；库会绑定到会话，后续轮次不传也生效。

响应头：`Cache-Control: no-cache`、`X-Accel-Buffering: no`、`Connection: keep-alive`。

### 会话列表 / 历史（M1-05）

`GET /api/conversations?module=chat`（列表，按最后消息倒序，≤50）与 `GET /api/conversations/{uid}`（详情，按 id 正序，仅 `DONE/STOPPED`，≤200），均需 Bearer。

- 只返回当前用户的会话：别人的 403、不存在 404；时间字段为本地 ISO-8601 字符串
- 实现：`ConversationController` → `ConversationService.listSummaries/detail`

### WebSocket（M1-09 持久连接）

```
ws://localhost:8080/ws/chat?token=<jwt>
```

- 一次握手跑多轮，同一连接同时只跑一轮；`done/error` 后连接保留
- 客户端帧：`{"type":"chat","content":...,"module":...}`、`{"type":"stop"}`、`{"type":"ping"}`
- 服务端帧：`ChatEvent`（同 SSE）或 `{"type":"pong"}`；`stop` 只取消当前轮（消息标 `STOPPED`）
- 客户端每 20s ping，45s 无 pong 视为假死；断线后下次发送自动重连
- 握手失败 401；页脚显示「握手 N 次 / 复用 N 轮 / 首字 Xms」

## 4. 事件协议

```json
{ "type": "meta",  "conversationId": "c1", "messageId": "m1", "model": "qwen2.5:7b" }
{ "type": "citation", "citation": { "id": "9", "title": "手册.pdf", "snippet": "年假…", "score": 0.83 } }
{ "type": "delta", "content": "你" }
{ "type": "usage", "promptTokens": 820, "completionTokens": 96 }
{ "type": "done",  "finishReason": "stop", "latencyMs": 1530 }
{ "type": "error", "code": "LLM_TIMEOUT", "message": "模型超时，请重试" }
```

规则：

- `meta` 必须第一个发；`done/error` 必须最后一个发，且只发一次。
- 引用随 assistant 消息写入 `message.citation_json`，刷新后由会话详情还原。
- `citation` 在 `delta` 前批量发送，一条一帧；`snippet` ≤120 字，`id` 为切片 ID。
- `delta` 允许为空但不要高频空帧；服务端按 20–50ms 或按 token 批量 flush。
- 客户端断开/发 `stop` → 取消上游 Flux，已生成内容照常落库。
- `usage` 依赖 `stream_options.include_usage`（网关已开）；取不到时输出 0。
- `done.finishReason`：`stop` 正常结束、`length` 达到 max-tokens（前端提示可继续）、`stopped` 客户端停止。
- WS 帧就是上述 JSON（无 `data:` 前缀）；一条连接跑一轮，`done/error` 后服务端关闭连接。

## 5. 数据库

```sql
conversation(id, uid, user_id, module, persona_id, kb_id, title, model_id, last_message_at, created_at)
message(id, conversation_id, role, content, tokens_in, tokens_out, latency_ms, model, status, request_id, citation_json, created_at)
```

- `conversation.uid`：对外 ID（`c_` 前缀），主键为 BIGINT（`V3`）；`kb_id` 为会话绑定的知识库（M2-05）。
- `message.status`：`STREAMING / DONE / STOPPED / FAILED`；`citation_json` 为引用数组（`V5`）。
- `V2__chat.sql` 已执行；`request_id` 用于重试与日志关联。
- 会话标题取首条用户消息前 30 字；每轮取最近 20 条 `DONE/STOPPED` 消息做上下文（M1-05）。

## 6. 状态机

```
IDLE → CONNECTING → STREAMING → DONE
                        ├→ STOPPED（用户停止）
                        └→ FAILED（超时/限流/模型错误）
```

前端按钮：`STREAMING` 时“停止”替换“发送”；`FAILED` 可重试；`STOPPED` 保留半截内容。
重试（`retry: true`）不新增 user 消息，旧半截回答标 `FAILED` 后重新生成。
保护（M1-08）：单机每分钟 20 轮（`app.chat.rate-limit-per-minute`，0 关闭）超限 429；`app.ai.timeout` 默认 60s 超时 504。
RAG（M2-05）：`kbId` 归属在落库前校验（`FORBIDDEN` / `KB_NOT_FOUND`）；检索异常降级为普通回答。

## 7. 前端

```ts
interface ChatTransport {
  send(cmd: ChatCommand, handlers: ChatHandlers, signal?: AbortSignal): Promise<void>
}
```

- `SseChatTransport`：fetch + `ReadableStream` + `TextDecoder`，按 `\n\n` 切事件；`useChat()` 只依赖 `ChatTransport`。
- `WsChatTransport`：持久连接，`chat/stop/ping` 帧，断线自动重连；页脚统计握手/复用/首字耗时。
- 页面头部 `SSE / WS` 切换；渲染走 `markdown-it` + `DOMPurify`，>200 条虚拟滚动。

## 8. 分步计划

| 步骤 | 内容 | 验证 |
| --- | --- | --- |
| M1-01 | 会话/消息表 + Flyway | 表存在，CRUD 通过 |
| M1-02 | `AiGateway` + 非流式 `/api/chat` ✅ | curl 返回一句话 |
| M1-03 | SSE 接口 + 事件编码 ✅ | curl 能看到逐字输出 |
| M1-04 | 前端聊天页 + SSE 解析 ✅ | 页面逐字显示、停止生效 |
| M1-05 | 上下文落库 ✅ / 会话列表与历史恢复 ✅ / 失败重试 ✅ | 追问记得上文，刷新可恢复，重试不重复提问 |
| M1-06 | token/时延指标 + 落库 ✅ | Grafana 有曲线 |
| M1-07 | WebSocket 传输 + 前端切换 ✅ | 两通道结果一致 |
| M1-08 | 错误处理、限流、超时 ✅ | 429/504 有友好提示，可重试 |
| M1-09 | WS 持久连接 + 心跳/重连 ✅ | 一次握手多轮，stop 不断连 |
| M2-05 | 聊天接 RAG：检索片段拼 Prompt + `citation` 事件与落库 ✅ | 带 `kbId` 提问有引用，刷新可恢复 |

## 9. 测试清单

- [ ] 中途断开标 `STOPPED` 无孤儿线程；429/超时返回 `error` 可重试
- [ ] SSE 经 Nginx 不缓冲；WS 断线重连后历史不丢

## 10. 暂不做

- 多模态（图片/语音）M4 之后；工具调用/Agent、消息编辑与分支对话按需再排。

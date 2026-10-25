# 聊天模块（M1）

> 只描述聊天与流式传输。RAG 见 `rag.md`（M2），角色卡见 `companion.md`（M4）。
> 决策背景见 `../adr/0002-streaming-transport.md`。

## 0. 速览（做 M1 只读这节）

- 目标：多轮会话、流式、停止、落库；SSE 为主，WS 为对照
- 唯一入口：`ChatOrchestrator`；输出统一 `ChatEvent` 流
- 接口：`POST /api/chat/stream`；事件 `meta/delta/citation/usage/done/error`
- 关键类：`ChatOrchestrator`、`ChatTransport`、`SseTransport`、`WsTransport`
- 验收：前端停止能中断后端；SSE 与 WS 输出一致
- 进度：M1-01 建表、M1-02 `AiGateway` + `/api/chat` 完成；下一步 M1-03 SSE

## 1. 目标

- 多轮会话、流式输出、停止生成、重新生成。
- 同一套业务逻辑支持 SSE 与 WebSocket 两种传输。
- 会话与消息落 MySQL，刷新页面可恢复。
- 记录 token、时延、错误，供 Grafana 展示。

## 2. 分层

```
Controller/Handler → ChatOrchestrator → AiGateway → Spring AI ChatModel
                          ├─ ChatMemory（短期）
                          ├─ ConversationService（落库）
                          └─ UsageRecorder（指标）
```

- `ChatOrchestrator` 是唯一业务入口，入参是统一的 `ChatCommand`。
- 输出统一为 `ChatEvent` 流（`meta/delta/citation/usage/done/error`）。
- 传输层只负责把 `ChatEvent` 编码成 SSE 或 WS 帧，以及处理连接生命周期。

## 3. 接口

### SSE（默认，M1）

```http
POST /api/chat/stream
Authorization: Bearer <jwt>
Content-Type: application/json
Accept: text/event-stream

{ "conversationId": "c1", "content": "你好", "modelId": 1, "useRag": false, "kbIds": [] }
```

响应头：`Cache-Control: no-cache`、`X-Accel-Buffering: no`、`Connection: keep-alive`。

### WebSocket（M1.5，对照）

```
ws://localhost:8080/ws/chat?token=<jwt>
```

客户端帧：

```json
{ "type": "chat", "requestId": "r1", "conversationId": "c1", "content": "你好" }
{ "type": "stop", "requestId": "r1" }
{ "type": "ping" }
```

服务端帧：`meta / delta / citation / usage / done / error / pong`，字段与 SSE 的 `data` 一致。

## 4. 事件协议

```json
{ "type": "meta",  "conversationId": "c1", "messageId": "m1", "model": "qwen2.5:7b" }
{ "type": "delta", "content": "你" }
{ "type": "citation", "documentId": "d1", "chunkId": "k9", "score": 0.83, "title": "手册.pdf" }
{ "type": "usage", "promptTokens": 820, "completionTokens": 96 }
{ "type": "done",  "finishReason": "stop", "latencyMs": 1530 }
{ "type": "error", "code": "LLM_TIMEOUT", "message": "模型超时，请重试" }
```

规则：

- `meta` 必须第一个发；`done/error` 必须最后一个发，且只发一次。
- `delta` 允许为空但不要高频空帧；服务端按 20–50ms 或按 token 批量 flush。
- 客户端断开/发 `stop` → 取消上游 Flux，已生成内容照常落库。
- 所有事件带 `requestId`，为多路复用与重连预留。

## 5. 数据库

```sql
conversation(id, user_id, module, persona_id, kb_id, title, model_id, last_message_at, created_at)
message(id, conversation_id, role, content, tokens_in, tokens_out, latency_ms, model, status, created_at)
```

- `message.status`：`STREAMING / DONE / STOPPED / FAILED`。
- 已执行 `V2__chat.sql`（M1-01）；`message.request_id` 可用于重试与日志关联。
- 会话标题：首条用户消息截断 30 字；可手动重命名。
- 短期记忆：最近 20 条或 4k token，超出截断；M4 再加长期记忆表。

## 6. 状态机

```
IDLE → CONNECTING → STREAMING → DONE
                        ├→ STOPPED（用户停止）
                        └→ FAILED（超时/限流/模型错误）
```

前端按钮：`STREAMING` 时“停止”替换“发送”；`FAILED` 可重试；`STOPPED` 保留半截内容。

## 7. 前端

```ts
interface ChatTransport {
  send(cmd: ChatCommand, handlers: ChatHandlers, signal?: AbortSignal): Promise<void>
}
```

- `SseChatTransport`：fetch + `ReadableStream` + `TextDecoder`，按 `\n\n` 切事件。
- `WsChatTransport`：连接复用、`requestId` 匹配、心跳、指数退避重连。
- `useChat()` 只依赖 `ChatTransport`，切换通道改配置即可。
- 渲染：`markdown-it` + `highlight.js` + `DOMPurify`；超过 200 条启用虚拟滚动。

## 8. 分步计划（每步一个 commit）

| 步骤 | 内容 | 验证 |
| --- | --- | --- |
| M1-01 | 会话/消息表 + Flyway | 表存在，CRUD 通过 |
| M1-02 | `AiGateway` + 非流式 `/api/chat` ✅ | curl 返回一句话 |
| M1-03 | SSE 接口 + 事件编码 | curl 能看到逐字输出 |
| M1-04 | 前端聊天页 + SSE 解析 | 页面逐字显示 |
| M1-05 | 会话列表/历史/停止/重试 | 刷新可恢复，停止生效 |
| M1-06 | token/时延指标 + 落库 | Grafana 有曲线 |
| M1-07 | WebSocket 传输 + 前端切换 | 两通道结果一致 |
| M1-08 | 错误处理、限流、超时 | 断网/超时有友好提示 |

## 9. 测试清单

- [ ] 空会话首条消息自动建会话
- [ ] 客户端中途断开，消息标记 `STOPPED`，无孤儿线程
- [ ] 模型 429/超时 → `error` 事件 + 可重试
- [ ] 同一用户并发两个请求互不串流
- [ ] SSE 经 Nginx 不缓冲（`proxy_buffering off`）
- [ ] WS 断线重连后历史不丢
- [ ] 超长输入被截断/拒绝，且有提示

## 10. 暂不做

- 多模态（图片/语音）：M4 之后。
- 工具调用/Agent：M2 之后按需。
- 消息编辑、分支对话：M3 之后。

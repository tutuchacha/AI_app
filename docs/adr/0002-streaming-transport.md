# ADR-0002 流式传输：SSE 为主，WebSocket 为对照通道

- 状态：Accepted
- 日期：2026-10-21

## 背景

聊天需要逐字输出。备选：SSE（HTTP 单向流）与 WebSocket（全双工）。希望两者都能实践，但不能复制业务逻辑。

## 决策

1. **M1 先做 SSE**：`POST /api/chat/stream`，作为默认与生产通道。
2. **M1.5 增加 WebSocket**：`/ws/chat`，作为独立传输适配器与学习对照。
3. 两者共用 `ChatOrchestrator`，只替换 Transport 层；前端用 `ChatTransport` 接口切换。

## 为什么默认不用 WebSocket

| 维度 | SSE | WebSocket |
| --- | --- | --- |
| 方向 | 服务端→客户端单向，足够 token 流 | 全双工 |
| 协议 | 普通 HTTP，鉴权/网关/日志沿用 | 需 Upgrade，代理与鉴权额外配置 |
| 断线 | 浏览器可重连，配 `Last-Event-ID` 续传 | 需自建心跳、重连、补偿 |
| 扩展 | 无状态，水平扩容简单 | 有状态，需要粘性会话/广播 |
| 取消 | `AbortController` 断开即取消 | 客户端发 `stop` 帧 |
| 适用 | 文本生成、通知、进度 | 语音、协同、人工接管、双向指令 |

结论：**纯文本流式用 SSE 更简单可靠**；WebSocket 的价值在双向实时场景（语音对话、人工客服接管、多端同步），
所以作为第二阶段模块引入，而不是一上来就承担全部聊天流量。

## 模块结构

```
chat/
├─ ChatOrchestrator        # 唯一业务入口：记忆、RAG、模型调用、落库
├─ transport/sse/          # SseChatController + 事件编码
├─ transport/ws/           # WsChatHandler + 帧协议
└─ dto/                    # 两种传输共用的请求/事件模型
```

## 代价与约束

- 两套传输要各自做鉴权、心跳、错误映射与测试，成本约增加 30%。
- WebSocket 需要额外处理：连接数上限、空闲超时、消息大小、背压。
- 指标按 `transport=sse|ws` 打标签，便于对比延迟与错误率。
- 业务逻辑只允许存在于 `ChatOrchestrator`，传输层禁止直接调用模型。

# 智能客服（support）

> M4-01~03：真实知识库绑定 + 引用溯源 + 低置信转人工提示。
> 客服**没有独立后端包**，复用聊天与 RAG；只有策略与切片原文两个小接口。

## 0. 速览（做 M4 / 改客服只读这节）

- 入口：`/support`（`SupportView.vue`）→ `ChatPanel mode="support"`
- 选库：左侧卡片读 `GET /api/knowledge-bases`，选中值经 `v-model:kb-id` 传给 `ChatPanel`，随消息带 `kbId`
- 引用：回答下方引用卡片 → `GET /api/chunks/{id}` 取原文，按 `snippet` 前 30 字高亮
- 兜底：最高引用分 < `app.support.min-score`（默认 **0.6**）→ 前端出「没有可靠依据 + 转人工」
- 提示词：`support` 模块走 `SUPPORT_EMPTY_PROMPT` / `SUPPORT_RAG_PROMPT`，比通用 RAG 更严
- 转人工工单：**M4-04 未实现**，按钮只发 `handoff` 事件

## 1. 口径与阈值

| 项 | 值 | 说明 |
| --- | --- | --- |
| 检索条数 | `app.support.top-k`（5） | 与聊天 RAG 的 `rag.top-k` 独立 |
| 兜底线 | `app.support.min-score`（0.6） | 实测相关命中 0.74+，无关提问 0.46~0.51 |
| 转人工开关 | `app.support.handoff-enabled`（true） | 关掉后前端不显示入口 |

阈值由后端下发（`GET /api/support/policy`），前端不写死；调参只改 `application.yml` 或环境变量
`SUPPORT_MIN_SCORE`，刷新页面即生效。

## 2. 接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/api/support/policy` | 返回 `minScore` / `topK` / `handoffEnabled`，需登录 |
| `GET` | `/api/chunks/{chunkId}` | 切片原文 + 文件名 + 序号 + token 数；非本人库返回 403 |

对话本身仍走 `POST /api/chat/stream`，`module` 传 `support`、`kbId` 传选中库。

## 3. 提示词策略

| 场景 | 系统提示 | 关键约束 |
| --- | --- | --- |
| 客服 + 有检索结果 | `SUPPORT_RAG_PROMPT` | 只答资料内、不外推、≤4 行、超范围转人工 |
| 客服 + 无结果 | `SUPPORT_EMPTY_PROMPT` | 禁止编造政策金额时效，只说明没查到 + 转人工 |
| 通用/陪伴 | `SYSTEM_PROMPT` / `RAG_SYSTEM_PROMPT` | 原逻辑不变 |

`ChatOrchestrator.systemPrompt(module, rag)` 统一分发，两条流式/非流式入口共用。

## 4. 前端结构

- `SupportView.vue`：真实库列表 + 文档/切片统计 + FAQ + 流程说明；阈值来自 policy 接口
- `ChatPanel.vue`：新增 `kb-picker`（客服页也显示选库）、`low-score`（兜底阈值）、`handoff` 事件；凭 `defineModel('kbId')` 与父页双向绑定
- `MessageItem.vue`：引用卡片改为可点按钮，`@cite` 抛 `Citation`
- 引用弹窗：`el-dialog` + `chunkParts` 三段渲染（命中段 `<mark>`），读不到时显示错误文案

## 5. 验证记录（2026-10-28）

- `SupportFlowTest` 4/4：policy 默认值、切片越权 403/不存在 404、有资料走严格提示、无资料劝转人工
- 真库：`demo` 库 `test001`（406 切片）问「退款多久到账」→ 命中 0.505 < 0.6 → 出兜底卡；
  问「什么是JVM进程缓存」→ 0.752 → 正常回答带引用，点引用可看原文
- `npm run typecheck` 通过

## 6. 已知限制 / 下一步

- 兜底只在**前端**判断（引用分随 SSE `citation` 事件到达）；后端仅做提示词约束，不会中止生成
- 阈值是按当前 `text-embedding-v3` + 中文语料实测的，换向量模型必须重标
- 不漏检的前提是库里有对应切片；空库时一律走兜底
- M4-04：`support_ticket` 表（V8）+ 提交/列表接口 + 工单状态机

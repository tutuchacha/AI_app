# 前端模块（M1 真实流式）

> 目标：页面只依赖契约，传输层可替换；M1-04 起默认走真实 SSE。
> 启动：`cd frontend && npm install && npm run dev` → http://127.0.0.1:5173

## 0. 速览（接前端只读这节）

- 启动：`cd frontend; npm install; npm run dev` → http://127.0.0.1:5173
- 结构：`api/`（请求与 token）、`chat/`（SSE/Mock 传输 + 状态）、`components/chat/`（UI）
- 关键：`ChatTransport` 契约、`useChat` 状态机、`SseChatTransport` 切帧解析
- 已完成：M0 页面 + 真实登录；M1 全链真实（SSE/WS、会话恢复）；M2 知识库页与聊天选库接真实接口
- 下一步：M3 文档分析页（页面形态待用户确认）
- 改哪个页面，再跳到对应步骤，不从头读

## 1. 目录职责

```
frontend/src/
├─ api/                  # http.ts（fetch/token/统一错误）+ auth.ts（登录）
├─ chat/                 # 流式能力，与页面解耦
│  ├─ sseTransport.ts    # 真实 SSE：fetch + ReadableStream + 事件分发
│  ├─ mockTransport.ts   # 假模型：按事件逐字返回（离线用）
│  └─ useChat.ts         # 消息状态、发送、停止、重置
├─ components/
│  ├─ layout/AppShell    # 侧边栏 + 顶栏 + 路由出口
│  ├─ chat/ChatPanel     # 三种模式复用的聊天容器
│  ├─ chat/MessageItem   # 气泡 + 引用 + 状态
│  └─ LearningNote       # 「学习提示」卡片
├─ data/personas.ts      # 角色卡 Mock 数据
├─ stores/               # auth / ui
├─ views/                # 每个业务页面
├─ router/               # 路由与登录守卫
└─ types/chat.ts         # ChatTransport / ChatEvent 契约
```

原则：**页面不直接发请求，只依赖 `ChatTransport` 接口**。这是后续换 SSE / WS 的关键。

## 2. 已实现的页面

| 路由 | 页面 | M0 能看到的 |
| --- | --- | --- |
| `/login` | 登录 | 真实 JWT 登录；`VITE_USE_MOCK=true` 时离线 |
| `/chat` | 对话 | 会话列表、流式输出、停止、耗时 |
| `/companion` | 恋爱陪伴 | 3 张角色卡、人设开场、切换角色 |
| `/support` | 智能客服 | 知识库卡片、引用来源、常见问题 |
| `/knowledge` | 知识库 | 真实接口：建库、上传、状态轮询、检索测试 |
| `/analysis` | 文档分析 | 拖拽上传、模拟进度、结构化结果 |
| `/dashboard` | 用量看板 | 指标卡、7 日柱状图、模块占比 |
| `/settings` | 设置 | 模型参数、学习模式、环境清单 |

右上角「学习提示」可关闭；内容会随页面变化，作为实现思路的入口。

## 3. 每个功能点的实现步骤

### F0-01 工程与路由
- 文件：`vite.config.ts`、`src/router/index.ts`、`main.ts`
- 关键：Vite alias `@`、懒加载路由、`beforeEach` 登录守卫
- 验收：未登录访问 `/chat` 会跳到 `/login`

### F0-02 应用外壳
- 文件：`components/layout/AppShell.vue`
- 关键：侧边栏品牌区、`RouterLink` 激活态、顶栏学习开关、响应式收起
- 验收：切换菜单高亮正确，窗口缩到 1000px 侧栏变图标

### F0-03 登录与状态
- 文件：`views/LoginView.vue`、`stores/auth.ts`
- 关键：Pinia setup store、localStorage、Mock token
- 验收：任意账号登录，刷新后仍保持登录；退出回到登录页

### F0-04 聊天契约
- 文件：`types/chat.ts`
- 关键：`ChatTransport` 只有一个 `send` 方法；事件分为 meta/delta/citation/usage/done/error
- 为什么：传输方式变化不影响 UI；事件协议与后端共用
- 验收：类型检查通过，所有页面只依赖这套类型

### F0-05 Mock 流式
- 文件：`chat/mockTransport.ts`
- 关键：`AbortSignal` 取消、每 4 个字一段、20ms 间隔、按模式返回不同内容
- 验收：点击发送后逐字出现，点“停止”立刻中断

### F0-06 消息状态机
- 文件：`chat/useChat.ts`
- 关键：`streaming / done / stopped / failed`；发送时先插入用户消息和空助手消息；`finally` 收尾
- 验收：停止后保留半截内容并标记“已停止”；刷新清空（M1 落库）

### F0-07 复用的聊天容器
- 文件：`components/chat/ChatPanel.vue`
- 关键：props 决定标题/人设/开场白/建议问题；`watch + nextTick` 贴底（用户上滑后停止跟随）
- 导航：右侧 `outline` 每条用户提问一个小横条，hover 展开问题列表，点击跳到该提问并高亮
- 贴底：`onActivated` 里 `pinToBottom()`（0/60/220/500ms 各贴一次，等列表高度稳定）
- 验收：对话、陪伴、客服三处复用同一个组件

### F0-08 三种业务皮肤
- 文件：`CompanionView.vue`、`SupportView.vue`
- 关键：角色卡只提供数据；客服把「知识库 + 引用 + 转人工」放在侧栏
- 验收：切换角色后开场白和配色变化；客服回答带引用卡片

### F0-09 知识库与文档分析
- 文件：`KnowledgeView.vue`、`AnalysisView.vue`、`api/knowledge.ts`
- 关键：库 CRUD、上传后 1.5s 轮询 `PENDING→READY/FAILED`、重建、检索测试；聊天页顶部选库透传 `kbId`
- 验收：做完上传状态自动走到 READY；检索出片段带分数；聊天引用可点开看原文

### F0-10 看板与设置
- 文件：`DashboardView.vue`、`SettingsView.vue`
- 关键：纯 CSS 图表、localStorage 配置、学习模式开关
- 验收：设置刷新后保留；关闭学习提示后卡片消失

## 4. SSE 传输已接（M1-04）

文件：`chat/sseTransport.ts`，页面与 `useChat` 都不需要改。

实现要点：
1. `fetch(POST /api/chat/stream)`，带 `Authorization: Bearer`、`Accept: text/event-stream`，`signal` 直连 `AbortController`。
2. 用 `ReadableStream` + `TextDecoder` 累积 buffer，按空行（`\r?\n\r?\n`）切帧，最后一帧收尾兜底。
3. 每帧取出 `data:` 行拼 JSON，按 `type` 分发到 `onMeta/onDelta/onCitation/onUsage/onDone/onError`。
4. 非 2xx 读 body 的 `message + traceId` 抛错；401 提示重新登录。
5. `useChat` 用 `VITE_USE_MOCK` 二选一；`onMeta` 记住 `conversationId`，`onDone` 结束流式态。

配套改动：`api/http.ts` 导出 `API_BASE`；`ChatRequest.conversationId` 可选（首条由后端生成 `c_` 前缀）；请求体带 `module`（取自页面 mode），后端据此归档会话。

多轮上下文：同一页面内 `useChat` 复用后端返回的 `conversationId`，后端按它加载历史，所以追问能记住上文。`done.finishReason === 'length'` 时页脚提示「已达长度上限，可发继续」。

会话保留：`AppShell` 用 `<KeepAlive>` 缓存路由组件，切到别的页面再回来消息还在；输入框在生成中不再 `disabled`，发送/停止后自动 `focus()`，不用再点一次。

会话列表与恢复（M1-05）：`ChatView` 挂载时 `GET /api/conversations?module=chat` 拉真实列表并自动打开最近一条；点列表项 `GET /api/conversations/{uid}` → `panelRef.loadConversation(detail)` → `useChat.hydrate(uid, messages)`，消息接上同一 `conversationId`，可继续追问。每轮结束 `ChatPanel` 抛 `changed(uid)`，`ChatView` 刷新列表（新会话出现、排序与时间更新）。`新对话` 只重置面板，首条消息落库后才出现在列表里。

踩坑记录：`<KeepAlive>` 外面再包 `<transition>` 会让 `onActivated` 不再触发（切页回来滚动位置丢失、组件却仍是缓存的那份）。现改为 `<KeepAlive>` 直接包路由组件，页面淡入用 CSS `@keyframes page-in`，激活钩子恢复正常。

踩坑记录：SSE 结束时 Tomcat 会以 `ASYNC` 再走一次过滤器链，此时 `SecurityContext` 已清空会 403，前端 `finally` 之前就中断，表现为「已生成完但仍显示生成中、不能输入」。后端放行 `DispatcherType.ASYNC/ERROR` 后解决。

下一步（M1-06）：token/时延指标接 Grafana；失败重试与 WebSocket 对照随后。

## 5. 已知限制

- `/analysis`、`/dashboard`、`/settings` 仍为示例数据。
- 聊天选库不支持解绑与多库同检；`el-option` 的 value 不能为 `null`，「不指定」用 `0` 哨兵。
- 暂无 Markdown 渲染、代码高亮、虚拟滚动。
- Element Plus 全量引入，首包约 1MB；后续按需引入优化。

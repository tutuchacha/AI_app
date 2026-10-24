# 前端模块（M0 界面预览）

> 目标：先把产品样子跑起来，理解每个页面的职责；后端接入时只替换数据层。
> 启动：`cd frontend && npm install && npm run dev` → http://127.0.0.1:5173

## 0. 速览（接前端只读这节）

- 启动：`cd frontend; npm install; npm run dev` → http://127.0.0.1:5173
- 结构：`api/`（请求与 token）、`chat/`（传输与状态）、`components/chat/`（UI）、`views/`（页面）
- 关键：`ChatTransport` 契约、`useChat` 状态机、`api/http.ts` 统一解包
- 已完成：F0-01~F0-10 + B06 真实登录；`VITE_USE_MOCK=true` 可离线
- 下一步：M1 接 SSE 聊天，替换 `mockTransport`
- 改哪个页面，再跳到对应 F0 步骤，不从头读

## 1. 目录职责

```
frontend/src/
├─ api/                  # http.ts（fetch/token/统一错误）+ auth.ts（登录）
├─ chat/                 # 流式能力，与页面解耦
│  ├─ mockTransport.ts   # 假模型：按事件逐字返回
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
| `/knowledge` | 知识库 | 知识库/文档表格、状态标签、上传入口 |
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
- 关键：props 决定标题/人设/开场白/建议问题；滚动到底部用 `watch + nextTick`
- 验收：对话、陪伴、客服三处复用同一个组件

### F0-08 三种业务皮肤
- 文件：`CompanionView.vue`、`SupportView.vue`
- 关键：角色卡只提供数据；客服把「知识库 + 引用 + 转人工」放在侧栏
- 验收：切换角色后开场白和配色变化；客服回答带引用卡片

### F0-09 知识库与文档分析
- 文件：`KnowledgeView.vue`、`AnalysisView.vue`
- 关键：状态标签、表格布局、拖拽上传、模拟进度清理（`onBeforeUnmount`）
- 验收：上传后显示文件名；分析进度到 100% 出现示例结果

### F0-10 看板与设置
- 文件：`DashboardView.vue`、`SettingsView.vue`
- 关键：纯 CSS 图表、localStorage 配置、学习模式开关
- 验收：设置刷新后保留；关闭学习提示后卡片消失

## 4. 接入后端时怎么换

只新增一个实现，不动页面：

```ts
export class SseChatTransport implements ChatTransport {
  readonly name = 'sse'

  async send(req: ChatRequest, handlers: ChatHandlers, signal: AbortSignal) {
    const res = await fetch('/api/chat/stream', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Accept: 'text/event-stream' },
      body: JSON.stringify(req),
      signal,
    })
    const reader = res.body!.getReader()
    const decoder = new TextDecoder()
    let buffer = ''
    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })
      // 按 \n\n 切事件，解析 event/data 后调用 handlers
    }
  }
}
```

切换到真实后端的步骤：
1. 后端实现 `POST /api/chat/stream`，事件格式与 `types/chat.ts` 一致。
2. 新建 `sseTransport.ts`，在 `useChat` 里按环境变量二选一。
3. 会话列表改为请求 `/api/conversations`，删除 Mock 数组。
4. 登录改为 `/api/auth/login`，token 存 Pinia + localStorage。
5. 引用、用量、错误分别对接 `citation / usage / error` 事件。

## 5. M0 已知限制

- 消息只在内存，刷新丢失（M1 解决）。
- Mock 登录不鉴权，任何人都能进（M1 解决）。
- 知识库、分析结果、看板均为示例数据。
- Element Plus 全量引入，首包约 1MB；后续用按需引入优化。
- 暂无 Markdown 渲染、代码高亮、虚拟滚动（M1 补）。

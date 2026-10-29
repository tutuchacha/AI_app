# 星屿 AI（AI_app）

一个边做边学的 AI 应用：通用聊天、恋爱助手、智能客服、文档分析、RAG 与可观测性。

当前进度：**M0~M4 完成，D-01~04 恋爱助手闭环完成**。数据库 PostgreSQL 16 + pgvector，
监控在 VM 上跑通；后端 141 个测试全绿。

## 现在能看什么

| 页面 | 能力 |
| --- | --- |
| 登录 | JWT 登录，演示账号 `demo / demo1234` |
| 对话 | SSE / WS 两种流式通道，知识库 RAG 引用 |
| 恋爱助手 | 画像 + 对象档案（阶段）→ AI 建议 / 约会问答 → 执行记录 + 反馈复盘 |
| 智能客服 | 选库问答、引用溯源、低置信兜底、转人工工单 |
| 知识库 | 文档上传、切片、向量索引与检索 |
| 文档分析 | 摘要、亮点、结构化要点、关联追问 |
| 看板 / 设置 | 用量与延迟（Prometheus）、模型与 Prompt 偏好 |

恋爱助手的典型用法：先写自己的画像与城市，给对象建档；
「AI 下一步」按阶段给目标 / 3 个动作 / 话术 / 禁忌 / 成功判断 + 高德地点；
问「今天去哪里玩，晚上吃个饭」并填区域（如「西湖」），会按周边 3km 推荐地点；
动作点「+ 待办」，做完回填对方反应，再「复盘」判断继续、换策略还是降速。

打开 http://127.0.0.1:5173 注册或登录后使用。

## 快速开始

```powershell
# 前端
cd D:\AI_learn\frontend
npm install
npm run dev

# 后端（数据库在 VM 上，先确认 5432 可达）
cd D:\AI_learn\backend
.\mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

- 密钥只放环境变量或 `backend/src/main/resources/application-local.yml`（gitignored）：
  `DASHSCOPE_API_KEY`、`app.date.amap-key`（高德 Web 服务 key，仅后端使用）。
- PostgreSQL 16 + pgvector 跑在 CentOS VM（`192.168.101.137:5432`，`ops/docker-compose.vm.yml`）；
  建库与连接见 `docs/modules/database.md`。

## 技术栈

| 层 | 技术 |
| --- | --- |
| 后端 | JDK 21、Spring Boot 3.5、Spring AI 1.1、MyBatis-Plus、Flyway |
| 前端 | Vue 3、TypeScript、Vite、Pinia、Element Plus |
| 数据 | PostgreSQL 16 + pgvector（HNSW 向量检索） |
| 外部 | 百炼 qwen-plus / text-embedding-v3、高德 Web 服务、Prometheus + Grafana |

业务代码统一走 `AiGateway`，不直接依赖 Spring AI；垂直包：`chat / knowledge / analysis / support / date`。

## 目录

```text
frontend/   Vue 3 应用
backend/    Spring Boot 服务
ops/        PostgreSQL / Prometheus / Grafana 的 compose 与看板
docs/       状态、模块手册、ADR、进度
scripts/    文档预算检查
```

## 换 Agent / 换机器继续

> 新会话不要粘贴旧对话；文档就是交接单。开工包约 1.6k tokens。

```text
在 D:\AI_learn 继续「星屿 AI」。
先 git pull，然后读 AGENTS.md，只读：
1) docs/STATUS.md 全文
2) docs/INDEX.md 中与本次相关的一行
3) 对应模块文档的「0. 速览」

当前 M0~M4 与 D-01~04 已完成；下一步 D-05：时间线（建档 → 建议 → 执行 → 反馈）。
先复述：现在做到哪、下一步、阻塞项，再开始动手。
每完成一步：重写 docs/STATUS.md、追加 docs/progress/YYYY-MM.md、commit + push。
不要全量读 docs/、00-架构方案.md、progress 历史或旧 ADR。
```

换机器：`git clone git@github.com:tutuchacha/AI_app.git`，环境见 `docs/01-环境准备.md`。

## 文档与上下文规则

- 开工只走：`STATUS.md` → `INDEX.md` 对应行 → 模块文档 §0 → 相关代码
- 不默认读：`00-架构方案.md` 全文、`progress/` 历史、旧 ADR、README
- 回答默认 ≤15 行；细节写文件，不往对话里倒
- 检查文档体积：`pwsh scripts/doc-check.ps1`（开工包 >1600 tokens 直接失败）

## 约定

- 密钥、数据库密码只放 `application-local.yml` 或环境变量，禁止提交
- 数据库变更只走 Flyway；每个阶段保证：能启动、有最小测试、可回滚
- 恋爱助手只优化表达与节奏：不操控、不伪造身份、不收集对方隐私

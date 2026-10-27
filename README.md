# 星屿 AI（AI_app）

一个边做边学的 AI 应用：通用聊天、恋爱陪伴、智能客服、文档分析、RAG、可观测性。
当前 **M3 文档分析完成**（M0~M3 已交付），数据库为 PostgreSQL 16 + pgvector，监控在 VM 上跑通。

## 现在能看什么

- 8 个页面：登录、对话、陪伴、客服、知识库、文档分析、看板、设置（除看板外均接真实后端）
- 真实登录（JWT）、SSE/WS 流式对话、RAG 引用、文档上传解析、四类文档分析
- 打开 http://127.0.0.1:5173 ，注册或登录后使用

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

PostgreSQL 16 + pgvector 跑在 CentOS VM（`192.168.101.137:5432`，`ops/docker-compose.vm.yml`）；建库与连接见 `docs/modules/database.md`。

## 技术栈

| 层 | 技术 |
| --- | --- |
| 后端 | JDK 21、Spring Boot 3.5、Spring AI 1.1、MyBatis-Plus、Flyway |
| 前端 | Vue 3、TypeScript、Vite、Pinia、Element Plus |
| 数据 | PostgreSQL 16 + pgvector（HNSW 向量检索） |
| 基础设施 | Nginx、Prometheus、Grafana、Ollama / OpenAI 兼容 API |

## 目录

```text
frontend/   Vue 3 应用
backend/    Spring Boot 服务
ops/        PostgreSQL / Prometheus / Grafana 的 compose 与看板
docs/       状态、模块手册、ADR、进度
scripts/    文档预算检查
```

## 换 Agent / 换机器继续

> 新会话不要粘贴旧对话；文档就是交接单。最小上下文约 1.3k tokens。

复制下面这段给新 Agent：

```text
在 D:\AI_learn 继续「星屿 AI」。
先 git pull，然后读 AGENTS.md，只读：
1) docs/STATUS.md 全文
2) docs/INDEX.md 中与本次相关的一行
3) docs/modules/backend.md 的「0. 速览」（做其他模块就换对应文件）

当前 M3 文档分析已完成，下一步 M4 内容待确认；数据库为 PostgreSQL 16 + pgvector（VM）。
先复述：现在做到哪、下一步、阻塞项，再开始动手。
每完成一步：重写 docs/STATUS.md、追加 docs/progress/2026-10.md、commit + push。
不要全量读 docs/、00-架构方案.md、progress 历史或旧 ADR。
```

换机器：`git clone git@github.com:tutuchacha/AI_app.git`，JDK 21 与 PostgreSQL/pgvector 见 `docs/01-环境准备.md`。

## 文档与上下文规则

- 开工只走：`STATUS.md` → `INDEX.md` 对应行 → 模块文档 §0 → 相关代码
- 不默认读：`00-架构方案.md` 全文、`progress/` 历史、旧 ADR、README
- 回答默认 ≤15 行；细节写文件，不往对话里倒
- 每个任务一个新会话，完成一步就收尾，避免历史越滚越长
- 检查文档体积：`pwsh scripts/doc-budget.ps1`

## 约定

- 密钥、数据库密码只放 `application-local.yml` 或环境变量，禁止提交
- 数据库变更只走 Flyway
- 每个阶段保证：能启动、有最小测试、可回滚

# 星屿 AI（AI_app）

一个边做边学的 AI 应用：通用聊天、恋爱陪伴、智能客服、文档分析、RAG、可观测性。
当前 **M0：前端界面已完成**，下一步补 Spring Boot 后端。

## 现在能看什么

- 8 个页面：登录、对话、陪伴、客服、知识库、文档分析、看板、设置
- Mock 流式输出、停止生成、客服引用卡片、学习提示
- 打开 http://127.0.0.1:5173 ，演示账号 `demo / 123456`

## 快速开始

```powershell
# 前端（现在可用）
cd D:\AI_learn\frontend
npm install
npm run dev

# 后端（M0-B01 生成后）
cd D:\AI_learn\backend
.\mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

MySQL 需先由管理员执行 `net start mysql`；建库 SQL 见 `docs/modules/backend.md` §3。

## 技术栈

| 层 | 技术 |
| --- | --- |
| 后端 | JDK 21、Spring Boot 3.5、Spring AI 1.1、MyBatis-Plus、Flyway |
| 前端 | Vue 3、TypeScript、Vite、Pinia、Element Plus |
| 数据 | MySQL 8、SimpleVectorStore / 自研 MySQL 向量库 |
| 基础设施 | Nginx、Prometheus、Grafana、Ollama / OpenAI 兼容 API |

## 目录

```text
frontend/   Vue 3 应用（已完成）
backend/    Spring Boot 服务（M0-B01 起）
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

当前 M0 前端已完成，下一步 M0-B01 后端工程初始化。
先复述：现在做到哪、下一步、阻塞项，再开始动手。
每完成一步：重写 docs/STATUS.md、追加 docs/progress/2026-10.md、commit + push。
不要全量读 docs/、00-架构方案.md、progress 历史或旧 ADR。
```

换机器：`git clone git@github.com:tutuchacha/AI_app.git`，JDK 21 与 MySQL 见 `docs/01-环境准备.md`。

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

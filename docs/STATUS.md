# 项目状态

> 唯一进度入口。每步结束**重写**本文件；上限 60 行；不写历史细节（去 `docs/progress/`）。

- 更新时间：2026-10-21
- 当前阶段：**M0 骨架（前端预览完成）**
- 当前分支：`main`
- 最近提交：`f71aed4 docs: 对标 yu-ai-agent 并调整能力路线`
- 远程：`git@github.com:tutuchacha/AI_app.git`（SSH 已通，已推送）

## 现在做到哪

- [x] 总体架构方案 `docs/00-架构方案.md`
- [x] 环境准备文档 `docs/01-环境准备.md`
- [x] Git 初始化 + SSH remote + 文档规则（AGENTS.md / INDEX / STATUS / progress）
- [x] JDK 21 安装（D:\JDK21，用户级 JAVA_HOME 已生效）
- [x] 前端界面预览：8 个页面 + Mock 流式 + 学习提示
- [ ] M0 后端骨架

## 下一步（只做这 3 件）

1. `net start mysql`，建库 `ai_app` 与账号（人工）
2. 生成后端骨架：Spring Boot 3.5 + 登录 + Flyway + Swagger + Actuator
3. 前后端联调登录与健康检查，补 `docs/02-接口约定.md`

## 阻塞项

- MySQL 服务当前 Stopped（服务名 `mysql`）
- 尚无模型 API Key / Ollama（M1 前准备即可）

## 活跃决策

- 主框架 Spring AI 1.1.8 + Boot 3.5.16（ADR-0001）
- 流式双通道：SSE 为主，WebSocket 为对照模块（ADR-0002）
- 向量库一期 SimpleVectorStore，二期自研 MySQL（架构方案 §5.5）
- 目标基线：对标 yu-ai-agent，新增 Tool/MCP 与 ReAct 阶段（`docs/04-对标-yu-ai-agent.md`）

## 快速启动

```powershell
net start mysql
cd backend;  ./mvnw spring-boot:run -Dspring-boot.run.profiles=local
cd frontend; npm install; npm run dev
```

## 最近进度（倒序，最多 5 条）

- 2026-10-21 M0-05：前端界面预览完成（8 页 + Mock 流式）
- 2026-10-21 M0-04：完成 yu-ai-agent 对标分析，路线图新增 Tool/MCP、ReAct
- 2026-10-21 M0-03：JDK 21 安装完成（D:\JDK21，Maven 已验证）
- 2026-10-21 M0-02：SSE/WS 双通道与文档规则落地
- 2026-10-21 M0-01：Git/SSH/文档体系就绪
- 2026-10-21 M0-00：架构方案与环境文档完成

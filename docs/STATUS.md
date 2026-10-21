# 项目状态

> 唯一进度入口。每步结束**重写**本文件；上限 60 行；不写历史细节（去 `docs/progress/`）。

- 更新时间：2026-10-21
- 当前阶段：**M0 骨架（前端完成，后端待开始）**
- 当前分支：`main`
- 最近提交：`b75358b feat: 前端界面预览与 Mock 流式`
- 远程：`git@github.com:tutuchacha/AI_app.git`（SSH 已通，已推送）

## 现在做到哪

- [x] 总体架构方案 `docs/00-架构方案.md`
- [x] 环境准备文档 `docs/01-环境准备.md`
- [x] Git 初始化 + SSH remote + 文档规则（AGENTS.md / INDEX / STATUS / progress）
- [x] JDK 21 安装（D:\JDK21，用户级 JAVA_HOME 已生效）
- [x] 前端界面预览：8 个页面 + Mock 流式 + 学习提示
- [ ] M0 后端骨架 B01–B06（见 `docs/modules/backend.md`）

## 下一步（只做这 3 件）

1. 人工：`net start mysql`，建库 `ai_app`（`docs/01-环境准备.md` §3）
2. 按 `docs/modules/backend.md` 做 B01–B03：工程、Flyway、统一响应
3. 做 B04–B06：JWT 登录、Swagger/Actuator、前端联调

## 阻塞项

- MySQL 服务当前 Stopped（服务名 `mysql`）
- 尚无模型 API Key / Ollama（M1 前准备即可）

## 活跃决策

- 主框架 Spring AI 1.1.8 + Boot 3.5.16（ADR-0001）
- 流式双通道：SSE 为主，WebSocket 为对照模块（ADR-0002）
- 向量库一期 SimpleVectorStore，二期自研 MySQL（架构方案 §5.5）
- 目标基线：对标 yu-ai-agent，新增 Tool/MCP 与 ReAct 阶段（`docs/04-对标-yu-ai-agent.md`）

## 下次怎么继续

1. 新开会话说：**“继续 M0-B01”**（或“继续下一步”）
2. AI 按 `AGENTS.md` 只读：`STATUS.md` → `INDEX.md` → `modules/backend.md`
3. 人工先启动 MySQL 并建库，再让 AI 生成后端代码
4. 需要看界面：`cd frontend; npm run dev` → http://127.0.0.1:5173

## 最近进度（倒序，最多 5 条）

- 2026-10-21 M0-06：保存进度，补齐后端 B01–B06 指南
- 2026-10-21 M0-05：前端界面预览完成（8 页 + Mock 流式）
- 2026-10-21 M0-04：完成 yu-ai-agent 对标分析，路线图新增 Tool/MCP、ReAct
- 2026-10-21 M0-03：JDK 21 安装完成（D:\JDK21，Maven 已验证）
- 2026-10-21 M0-02：SSE/WS 双通道与文档规则落地

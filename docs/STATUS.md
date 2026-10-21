# 项目状态

> 唯一进度入口。每步结束**重写**本文件；上限 60 行；不写历史细节（去 `docs/progress/`）。

- 更新时间：2026-10-21
- 当前阶段：**M0 骨架（未开始）**
- 当前分支：`main`
- 最近提交：`9382684 docs: 初始化 AI App 架构方案`
- 远程：`git@github.com:tutuchacha/AI_app.git`（SSH 已通，仓库为空，待 push）

## 现在做到哪

- [x] 总体架构方案 `docs/00-架构方案.md`
- [x] 环境准备文档 `docs/01-环境准备.md`
- [x] Git 初始化 + SSH remote + 文档规则（AGENTS.md / INDEX / STATUS / progress）
- [ ] M0 后端骨架
- [ ] M0 前端骨架

## 下一步（只做这 3 件）

1. 安装 JDK 21，切换 `JAVA_HOME`，确认 `mvn -v` 为 21（人工）
2. `net start mysql`，建库 `ai_app` 与账号（人工）
3. 生成 M0 骨架：Spring Boot 3.5 + Vue 3，含登录、Flyway、Swagger、Actuator

## 阻塞项

- JDK 8 无法启动 Spring Boot 3.x，需先装 **JDK 21**
- MySQL 服务当前 Stopped（服务名 `mysql`）
- 尚无模型 API Key / Ollama（M1 前准备即可）

## 活跃决策

- 主框架 Spring AI 1.1.8 + Boot 3.5.16（ADR-0001）
- 流式双通道：SSE 为主，WebSocket 为对照模块（ADR-0002）
- 向量库一期 SimpleVectorStore，二期自研 MySQL（架构方案 §5.5）

## 快速启动

```powershell
net start mysql
cd backend;  ./mvnw spring-boot:run -Dspring-boot.run.profiles=local
cd frontend; npm install; npm run dev
```

## 最近进度（倒序，最多 5 条）

- 2026-10-21 M0-01：Git/SSH/文档规则就绪
- 2026-10-21 M0-00：架构方案与环境文档完成

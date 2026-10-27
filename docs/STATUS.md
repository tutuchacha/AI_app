# 项目状态

> 仪表盘：只写当前，≤35 行 / 1.8k 字符。历史去 progress，决策去 ADR。

- 更新时间：2026-10-27
- 阶段：**M3 文档分析完成**（摘要 / 结构 / 追问 / 要素四类，真库真实模型验证）；下一步见下
- 数据库：Flyway `V1`~`V7 analysis fields` 已执行（本机 MySQL 已迁移到 v7）
- 鉴权：BCrypt + JWT（HS256，2h）；`SecurityConfig` 放行 `ASYNC/ERROR` 与 `/ws/**`
- 模型：百炼 `qwen-plus`；向量 `text-embedding-v3`（1024 维）；超时默认 60s
- 远程：`git@github.com:tutuchacha/AI_app.git`

## 下一步（按顺序）

1. 装 Docker 后跑 `ops/docker-compose.yml`，确认 Grafana 看板
2. 聊天选库增强（解绑、多库同检）待用户确认后再排
3. M4 内容待用户确认

## 已完成

- M0：前端 8 页 + 真实登录；后端骨架/建库/统一响应/JWT/Swagger/指标
- M1-01~09：落库、SSE/WS（持久 + 心跳重连）、上下文、限流/超时、Prometheus 埋点
- M1-10：容器化部署件（Dockerfile / nginx / compose）— 本机无 Docker，未实跑
- M2-01~05：知识库 CRUD、解析切片、向量回填、检索 + 聊天接 RAG（引用事件与落库）
- M3-01：`document_analysis` 表 + 摘要/结构/追问（异步 Worker、复用进行中任务、级联删除）；
  分析页选库选文档、三 Tab、轮询、重试、导出 Markdown
- M3-02：要素抽取 `FIELDS`（三列表 + 原文依据，V7 改列注释）；四卡四 Tab、Markdown 表格渲染
- 验证：后端 111 测试通过；浏览器真库四类分析全部 READY（qwen-plus，3.98s / 5.97s / 18.99s / 10.06s）

## 阻塞

- 本机未装 Docker：镜像/compose 与 Grafana 面板未实跑
- 8080 有旧 dev 实例在跑，验证统一另起 8081（未动旧进程）

## 恢复命令

```powershell
cd D:\AI_learn\backend
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=local
cd ..\frontend; npm run dev
```

- 测试：`backend` 下 `$env:JAVA_HOME='D:\JDK21'; .\mvnw.cmd test`；前端 `npm run typecheck`

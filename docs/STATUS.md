# 项目状态

> 仪表盘：只写当前，≤35 行 / 1.8k 字符。历史去 progress，决策去 ADR。

- 更新时间：2026-10-27
- 阶段：**M2 RAG 全部完成**（M2-01~05）；下一步 M3 前端知识库页
- 数据库：Flyway `V1`~`V5 chat citation` 已执行（本机 MySQL 已迁移到 v5）
- 鉴权：BCrypt + JWT（HS256，2h）；`SecurityConfig` 放行 `ASYNC/ERROR` 与 `/ws/**`
- 模型：百炼 `qwen-plus`；向量 `text-embedding-v3`（1024 维）；超时默认 60s
- 远程：`git@github.com:tutuchacha/AI_app.git`

## 下一步（按顺序）

1. **M3 前端**：知识库管理页 + 聊天页选库并传 `kbId`（引用卡片与历史恢复已就绪）
2. 装 Docker 后跑 `ops/docker-compose.yml`，确认 Grafana 看板
3. M3 页面形态（桌面布局等）待用户确认后再排

## 已完成

- M0：前端 8 页 + 真实登录；后端骨架/建库/统一响应/JWT/Swagger/指标
- M1-01~09：落库、SSE/WS（持久 + 心跳重连）、上下文、限流/超时、Prometheus 埋点
- M1-10：容器化部署件（Dockerfile / nginx / compose）— 本机无 Docker，未实跑
- M2-01~04：知识库 CRUD、文档解析切片、向量回填、余弦检索 + `/search`
- M2-05：聊天接 RAG —— `ChatRequest.kbId` 检索片段拼 Prompt、`meta → citation* → delta*` 事件、
  `message.citation_json`（V5）刷新恢复；检索异常降级为普通回答，不阻断聊天
- 验证：100 后端测试通过；真库 8081 冒烟 SSE 引用 0.7344 命中、非流式复用会话库 0.5169、
  详情恢复 2 条引用；冒烟数据已清理

## 阻塞

- 本机未装 Docker：镜像/compose 与 Grafana 面板未实跑
- 8080 端口有旧的 dev 实例在跑，验证时另起 8081 规避（未动旧进程）

## 恢复命令

```powershell
cd D:\AI_learn\backend
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=local   # 8080，启动即跑 Flyway
cd ..\frontend; npm run dev                                    # 5173
```

演示账号 `demo / demo1234`；提交前 `pwsh scripts/doc-check.ps1`。

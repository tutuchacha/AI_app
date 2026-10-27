# 项目状态

> 仪表盘：只写当前，≤35 行 / 1.8k 字符。历史去 progress，决策去 ADR。

- 更新时间：2026-10-27
- 阶段：**M4 待定**；数据库已全量切 **PostgreSQL 16 + pgvector**，VM 监控（Prometheus/Grafana）跑通
- 数据库：VM `192.168.101.137:5432/ai_app`，Flyway `V1`~`V7` 全 PG 语法；向量走 HNSW
- 鉴权：BCrypt + JWT（HS256，2h）；`SecurityConfig` 放行 `ASYNC/ERROR` 与 `/ws/**`
- 模型：百炼 `qwen-plus`；向量 `text-embedding-v3`（1024 维）；超时默认 60s
- 远程：`git@github.com:tutuchacha/AI_app.git`

## 下一步（按顺序）

1. 浏览器过一遍前端（知识库上传/检索/聊天引用/分析），确认切库后无回归
2. 聊天选库增强（解绑、多库同检）待用户确认后再排
3. M4 内容待用户确认

## 已完成

- M0：前端 8 页 + 真实登录；后端骨架/建库/统一响应/JWT/Swagger/指标
- M1-01~10：落库、SSE/WS、上下文、限流/超时、Prometheus 埋点、容器化部署件
- M2-01~05：知识库 CRUD、解析切片、向量回填、pgvector 检索、聊天接 RAG
- M3-01/02：文档分析四类（摘要/结构/追问/要素），异步 Worker、级联删除
- 切库：MySQL → PG 16 + pgvector（生成列 + HNSW 部分索引），113 测试通过；
  真库 1024 维检索命中索引，聊天带 2 条引用
- 监控：VM `ops/docker-compose.vm.yml` 起 PG/Prometheus/Grafana；target UP、五面板出数
- 修复：对话页窄面板下头部自动换行

## 阻塞

- 无。旧 MySQL 库保留作回滚备份（服务已停），新实例占 8080

## 恢复命令

```powershell
cd D:\AI_learn\backend; .\mvnw.cmd spring-boot:run
cd ..\frontend; npm run dev
ssh root@192.168.101.137 "cd /opt/xingyu && docker compose -f docker-compose.vm.yml up -d"
```

- 测试：`backend` 下 `$env:JAVA_HOME='D:\JDK21'; .\mvnw.cmd test`（113）；前端 `npm run typecheck`

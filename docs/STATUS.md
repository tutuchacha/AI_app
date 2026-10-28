# 项目状态

> 仪表盘：只写当前，≤35 行 / 1.8k 字符。历史去 progress，决策去 ADR。

- 更新时间：2026-10-28
- 阶段：**M3 完成**；M4 待定
- 数据库：VM `192.168.101.137:5432/ai_app`（PG 16 + pgvector），Flyway `V1`~`V7` 全部 success
- 切库结论：**成功**，代码无 MySQL 语法残留；RAG/分析全链路已在 PG 跑通
- M3-05 用量看板：`GET /api/metrics/usage?days=7`（全局 `today` + 本人 `mine`、7 天曲线、模块占比、30s 缓存）
  - 口径：调用/token/P95/错误率/模块占比走 PG `message`+`conversation`；活跃流走 Prometheus，超时即退回
  - 前端只打后端取数，不外露内网地址；Grafana 只做外链，不嵌 iframe
- 修复：P95 改用 `_bucket` 直方图（`quantile=` 是 2 分钟窗口，闲时归零），Grafana 同步 `histogram_quantile`
- 测试：113 通过 + 新增 `UsageControllerTest`（全量待服务停后复跑）
- 模型：百炼 `qwen-plus`；向量 `text-embedding-v3`（1024 维）；超时 60s

## 下一步（按顺序）

1. 浏览器过一遍前端四流程（切库后回归）
2. 聊天选库增强（解绑、多库同检）待确认
3. M4 内容待确认（`companion` / `support` 文档尚未建）

## 已知限制

- HNSW：带 `kb_id` 过滤时 10 万行实测退化为 seq scan，量大再优化
- `embedding_vec` 固定 1024 维，换向量模型需迁移
- 回滚库为本机 MySQL 8.0.29 `C:\dev\mysql`（服务已停）

## 恢复命令

```powershell
cd D:\AI_learn\backend; .\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=local"
cd ..\frontend; npm run dev
ssh root@192.168.101.137 "cd /opt/xingyu && docker compose -f docker-compose.vm.yml up -d"
```

- 测试：`backend` 下 `$env:JAVA_HOME='D:\JDK21'; .\mvnw.cmd test`；前端 `npm run typecheck`

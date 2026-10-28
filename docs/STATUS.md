# 项目状态

> 仪表盘：只写当前，≤35 行 / 1.8k 字符。历史去 progress，决策去 ADR。

- 更新时间：2026-10-28
- 阶段：**M3 完成**；M4-01~04 客服闭环完成
- 数据库：VM `192.168.101.137:5432/ai_app`（PG 16 + pgvector），Flyway `V1`~`V7` 全部 success
- 切库结论：**成功**，代码无 MySQL 语法残留；RAG/分析全链路已在 PG 跑通
- M3-05 用量看板：`GET /api/metrics/usage?days=7`（全局 `today` + 本人 `mine`、7 天曲线、模块占比、30s 缓存）
  - 口径：调用/token/P95/错误率/模块占比走 PG `message`+`conversation`；活跃流走 Prometheus，超时即退回
  - 前端只打后端取数，不外露内网地址；Grafana 只做外链，不嵌 iframe
- 修复：P95 改用 `_bucket` 直方图（`quantile=` 是 2 分钟窗口，闲时归零），Grafana 同步 `histogram_quantile`
- M4-01~04 客服：`/support` 接真实知识库（选库 → 带 `kbId`）、引用可点开看原文、低置信出「转人工」兜底卡、点转人工落工单
  - 新接口：`GET /api/support/policy`（阈值下发）、`GET /api/chunks/{id}`（切片原文，越权 403）
  - 工单：`POST/GET /api/support/tickets` + `POST /{id}/close`，表 `support_ticket`（V8），OPEN/CLOSED 两态
  - 阈值 `app.support.min-score` 默认 **0.6**（实测相关命中 0.74+，无关 0.46~0.51），前端不写死
  - `support` 模块独立提示词（有资料只答资料内 / 无资料直接建议转人工），见 `docs/modules/support.md`
- 测试：全量后端 **122/122** 通过（新增 `SupportFlowTest` / `SupportTicketTest` 各 4 个）；typecheck 通过
- 模型：百炼 `qwen-plus`；向量 `text-embedding-v3`（1024 维）；超时 60s

## 下一步（按顺序）

1. 转 `companion` 恋爱陪伴：角色卡 + 长期记忆（`docs/modules/companion.md` 待建）
2. 客服增强（可选）：工单坐席分配 / 通知 / 备注填写
3. 浏览器四流程回归（切库、分析、看板、客服）

## 已知限制

- 兜底只在前端按 `citation.score` 判断，后端仅提示词约束，不会中止生成
- 阈值按当前向量模型实测，换 embedding 必须重标
- 工单只有 OPEN/CLOSED，无坐席分配、通知与 SLA；`note` 已建表但前端未开放
- HNSW：带 `kb_id` 过滤时 10 万行实测退化为 seq scan，量大再优化
- 回滚库为本机 MySQL 8.0.29 `C:\dev\mysql`（服务已停）

## 恢复命令

```powershell
cd D:\AI_learn\backend; .\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=local"
cd ..\frontend; npm run dev
ssh root@192.168.101.137 "cd /opt/xingyu && docker compose -f docker-compose.vm.yml up -d"
```

- 测试：`backend` 下 `$env:JAVA_HOME='D:\JDK21'; .\mvnw.cmd test`；前端 `npm run typecheck`

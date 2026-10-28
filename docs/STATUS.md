# 项目状态

> 仪表盘：只写当前，≤35 行 / 1.8k 字符。历史去 progress，决策去 ADR。

- 更新时间：2026-10-28
- 阶段：**M3 完成**；M4-01~04 客服闭环完成；**D-01~02 恋爱助手建档 + 建议完成**
- 数据库：VM `192.168.101.137:5432/ai_app`（PG 16 + pgvector），Flyway `V1`~`V9` 全部 success
- M4 客服：`/support` 选库 + 引用溯源 + 低置信兜底 + 转人工工单（`support_ticket` V8，OPEN/CLOSED）
  - 阈值 `app.support.min-score` 0.6 由 `GET /api/support/policy` 下发；口径见 `docs/modules/support.md`
- D-01~02 恋爱助手：`/date` 建档（画像 + 对象，阶段 MET→TOGETHER）+「AI 下一步」建议，原 `/companion` 已删
  - 表 `date_profile` / `date_target`（V9）/ `date_advice`（V10）；`GET/PUT /api/date/profile`、`/targets` CRUD
  - `POST /api/date/advice`：模型出「目标 / 3 动作 / 话术 / 禁忌 / 成功判断」JSON，再按城市查高德补 1~2 地点
  - 失败口径：模型输出非 JSON → 502；高德失败降级为空地点；`GET /api/date/advice?targetId=` 取最近 5 条
  - 口径见 `docs/modules/date.md`；高德 key 在 `application-local.yml`（gitignored）
- 测试：全量后端 **130/130** 通过（date 8 个）；typecheck 通过
- 模型：百炼 `qwen-plus`；向量 `text-embedding-v3`（1024 维）；超时 60s

## 下一步（按顺序）

1. D-03：执行/反馈闭环（`date_action` / `date_feedback`，复盘后推进或回退阶段）
2. D-04：右侧时间线（建档 → 建议 → 执行 → 反馈）
3. 客服增强（可选）：工单备注与坐席分配

## 已知限制

- 建议依赖模型返回合法 JSON，格式异常直接 502（前端可重试）；高德失败只是没有地点
- 建议质量靠提示词约束，尚无「有用/没用」反馈回流
- 兜底只在前端按 `citation.score` 判断，后端仅提示词约束
- 工单仅 OPEN/CLOSED，无通知与 SLA；`note` 建表未开放
- HNSW 带 `kb_id` 过滤 10 万行退化为 seq scan
- 回滚库为本机 MySQL 8.0.29（服务已停）

## 恢复命令

```powershell
cd D:\AI_learn\backend; .\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=local"
cd ..\frontend; npm run dev
```

- 测试：`backend` 下 `$env:JAVA_HOME='D:\JDK21'; .\mvnw.cmd test`；前端 `npm run typecheck`

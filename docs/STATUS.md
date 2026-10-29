# 项目状态

> 仪表盘：≤30 行 / 1.5k 字符。历史去 progress，决策去 ADR。

- 更新时间：2026-10-29
- 阶段：M3 完成；M4 客服闭环完成；**D-01~05 恋爱助手闭环（建档 → 建议 → 问答 → 执行反馈 → 时间线）**
- 数据库：VM `192.168.101.137:5432/ai_app`（PG16 + pgvector），Flyway `V1`~`V12` success
- 客服：`/support` 选库 + 引用溯源 + 低置信兜底 + 工单（OPEN/CLOSED）；阈值 0.6 由 policy 下发
- 恋爱助手：`/date` 建档 +「AI 下一步」+「问问助手」问答（区域 3km）+「执行与反馈」复盘 + 时间线
  - 接口：`/api/date/{profile,targets,advice,ask,actions,review,timeline}`；口径 `docs/modules/date.md`
- 工作台 `/dashboard` 用量 30s 静默刷新
- 测试：后端 **144/144**，typecheck 通过；模型 qwen-plus / text-embedding-v3

## 下一步

1. 站内注册 + 用量看板补口径；微信扫码另立 ADR
2. 可选：问答结果「就选这家」写进执行记录

## 已知限制

- 复盘需模型返回合法 JSON，异常 502；阶段只一步一跳
- 质量靠提示词，尚无「有用 / 没用」回流
- 客服兜底只在前端按 `citation.score` 判断；工单无通知与 SLA
- HNSW 带 `kb_id` 过滤 10 万行退化为 seq scan

## 恢复

- 后端：`backend` 下 `.\mvnw spring-boot:run "-Dspring-boot.run.profiles=local"`；前端：`npm run dev`
- 测试：后端 `$env:JAVA_HOME='D:\JDK21'; .\mvnw.cmd test`；前端 `npm run typecheck`

# 项目状态

> 仪表盘：≤30 行 / 1.5k 字符。历史去 progress，决策去 ADR。

- 更新时间：2026-10-28
- 阶段：M3 完成；M4 客服闭环完成；**D-01~03 恋爱助手建档 + 建议 + 问答完成**
- 数据库：VM `192.168.101.137:5432/ai_app`（PG16 + pgvector），Flyway `V1`~`V10` success
- 客服：`/support` 选库 + 引用溯源 + 低置信兜底 + 工单（OPEN/CLOSED）；阈值 0.6 由 policy 下发
- 恋爱助手：`/date` 建档 +「AI 下一步」+「问问助手」问答（区域 3km 地点，不落库）
  - 接口：`/api/date/{profile,targets,advice,ask}`；口径 `docs/modules/date.md`；高德 key 在 `application-local.yml`
- 测试：后端 **135/135**，前端 typecheck 通过；模型 qwen-plus / text-embedding-v3

## 下一步

1. D-04 执行/反馈闭环（`date_action` / `date_feedback`，复盘后推进或回退阶段）
2. D-05 时间线与阶段变更记录
3. 客服增强（可选）：工单备注 / 坐席分配

## 已知限制

- 建议依赖模型返回合法 JSON，异常直接 502；高德失败只是没有地点
- 尚无「有用 / 没用」反馈回流，建议质量靠提示词
- 客服兜底只在前端按 `citation.score` 判断；工单无通知与 SLA
- HNSW 带 `kb_id` 过滤 10 万行退化为 seq scan

## 恢复

- 后端：`backend` 下 `.\mvnw spring-boot:run "-Dspring-boot.run.profiles=local"`；前端：`npm run dev`
- 测试：后端 `$env:JAVA_HOME='D:\JDK21'; .\mvnw.cmd test`；前端 `npm run typecheck`

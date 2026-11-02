# 项目状态

> 仪表盘：≤30 行 / 1.5k 字符。历史去 progress，决策去 ADR。

- 更新时间：2026-11-02
- 阶段：M3 完成；M4 客服闭环完成；**D-01~09 恋爱助手（建档→建议→问答→反馈→复盘→时间线→定位→分栏）**
- 数据库：VM `192.168.101.137:5432/ai_app`（PG16+pgvector），Flyway V1~V12 success
- 客服：`/support` 选库 + 引用溯源 + 低置信兜底 + 工单；阈值 0.6 由 policy 下发
- 恋爱助手：`/date` 右栏分「今日 / 找地点 / 复盘 / 时间线」，含建议、问答、定位回填
  - 接口：`/api/date/{profile,targets,advice,ask,actions,review,timeline,geo/locate}`；口径 `docs/modules/date.md`
- 登录可注册并自动登录；`/dashboard` 用量 30s 刷新
- 测试：后端 **148/148**，typecheck 通过；qwen-plus / text-embedding-v3

## 下一步

1. `/admin` MVP：用户与角色、调用成本、知识库、工单
2. 客服工单备注 / 坐席分配

## 已知限制

- 复盘需模型合法 JSON（异常 502）；定位失败手选；阶段一步一跳
- 质量靠提示词，尚无「有用 / 没用」回流
- 客服兜底在前端按 `citation.score`；工单无通知/SLA
- HNSW 带 `kb_id` 过滤 10 万行退化 seq scan

## 恢复

- 后端：`backend` 下 `.\mvnw spring-boot:run "-Dspring-boot.run.profiles=local"`；前端：`npm run dev`
- 测试：后端 `$env:JAVA_HOME='D:\JDK21'; .\mvnw.cmd test`；前端 `npm run typecheck`

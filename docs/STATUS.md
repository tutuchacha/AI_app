# 项目状态

> 仪表盘：只写当前，≤35 行 / 1.8k 字符。历史去 progress，决策去 ADR。

- 更新时间：2026-10-23
- 阶段：**M0 后端进行中（B01–B02 完成）**
- 代码提交：`7c09e8b`（B01）；B02 本次
- 数据库：本机 `127.0.0.1:3306 / ai_app`，Flyway `V1` 已执行
- 远程：`git@github.com:tutuchacha/AI_app.git`（已同步）

## 下一步（按顺序）

1. `M0-B03` 统一响应 `Result` / 错误码 / 全局异常 / TraceId
2. 之后 `B04` JWT 登录 → `B05` Swagger/Actuator → `B06` 前端联调
3. 文档：`docs/modules/database.md`、`docs/ARCHITECTURE.md`（本次一并产出）

## 已完成

- 前端 8 页 + Mock 流式 + 学习提示（`b75358b`）
- B01：后端骨架、临时 Security/CORS、Maven Wrapper；health=UP
- B02：建库建号（`ai_app`）、`V1__init.sql`（`sys_user`）、Flyway 开启
- 验证：`flyway_schema_history` 有 V1 记录；`/actuator/health` = UP

## 阻塞

- 无（AI Key / Ollama 留到 M1）

## 恢复命令

```powershell
cd D:\AI_learn\backend
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=local   # 8080
cd ..\frontend; npm run dev                                    # 5173
```

演示账号 `demo / 123456`（M0 Mock）。

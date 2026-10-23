# 项目状态

> 仪表盘：只写当前，≤35 行 / 1.8k 字符。历史去 progress，决策去 ADR。

- 更新时间：2026-10-23
- 阶段：**M0 后端 B01–B02 完成；文档治理就绪**
- 代码提交：`48971f7`（B02；B01 为 `7c09e8b`）
- 数据库：本机 `127.0.0.1:3306 / ai_app`，Flyway `V1` 已执行
- 文档：`ARCHITECTURE.md`、`modules/database.md`、`DOC-GOVERNANCE.md`
- 远程：`git@github.com:tutuchacha/AI_app.git`（已同步）

## 下一步（按顺序）

1. `M0-B03` 统一响应 `Result` / 错误码 / 全局异常 / TraceId
2. `B04` JWT 登录 → `B05` Swagger/Actuator → `B06` 前端联调

## 已完成

- 前端 8 页 + Mock 流式 + 学习提示（`b75358b`）
- B01：后端骨架、临时 Security/CORS、Maven Wrapper；health=UP
- B02：建库建号、`sys_user`、Flyway 与 health.db 开启
- 文档：架构图/页面文件地图、数据库手册、治理规则 + `doc-check.ps1`

## 阻塞

- 无（AI Key / Ollama 留到 M1）

## 恢复命令

```powershell
cd D:\AI_learn\backend
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=local   # 8080
cd ..\frontend; npm run dev                                    # 5173
```

提交前：`pwsh scripts/doc-check.ps1`；演示账号 `demo / 123456`（M0 Mock）。

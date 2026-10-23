# 项目状态

> 仪表盘：只写当前，≤35 行 / 1.8k 字符。历史去 progress，决策去 ADR。

- 更新时间：2026-10-23
- 阶段：**M0 后端 B01–B03 完成**
- 代码提交：`217194d`（B03；B02 为 `48971f7`）
- 数据库：本机 `127.0.0.1:3306 / ai_app`，Flyway `V1` 已执行
- 契约：`docs/02-接口约定.md`（code=0 成功，错误码=HTTP 状态）
- 远程：`git@github.com:tutuchacha/AI_app.git`（已同步）

## 下一步（按顺序）

1. `M0-B04` 注册 / 登录 / JWT 过滤器 / `/api/auth/me`
2. `B05` Swagger + Actuator → `B06` 前端真实登录联调

## 已完成

- 前端 8 页 + Mock 流式 + 学习提示（`b75358b`）
- B01：骨架、临时 Security/CORS、Maven Wrapper；health=UP
- B02：建库建号、`sys_user`、Flyway 与 health.db 开启
- B03：`Result` / `ErrorCode` / `BusinessException` / 全局异常 / TraceId
- 验证：8 个测试通过；`/api/nope` → 404 JSON；未登录 → 401 JSON
- 文档：架构图、数据库手册、文档治理、接口约定

## 阻塞

- 无（AI Key / Ollama 留到 M1）

## 恢复命令

```powershell
cd D:\AI_learn\backend
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=local   # 8080
cd ..\frontend; npm run dev                                    # 5173
```

提交前：`pwsh scripts/doc-check.ps1`；演示账号 `demo / 123456`（M0 Mock）。

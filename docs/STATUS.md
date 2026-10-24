# 项目状态

> 仪表盘：只写当前，≤35 行 / 1.8k 字符。历史去 progress，决策去 ADR。

- 更新时间：2026-10-24
- 阶段：**M0 后端 B01–B04 完成**
- 代码提交：本次（B03 为 `217194d`）
- 数据库：`127.0.0.1:3306 / ai_app`，Flyway `V1` 已执行
- 鉴权：BCrypt + JWT（HS256，2h），`/api/auth/*` 已可用
- 远程：`git@github.com:tutuchacha/AI_app.git`（已同步）

## 下一步（按顺序）

1. `M0-B05` Swagger 接口文档 + Actuator 指标
2. `M0-B06` 前端接真实登录（`stores/auth.ts`、`api/http.ts`）

## 已完成

- 前端 8 页 + Mock 流式 + 学习提示（`b75358b`）
- B01 骨架 / B02 建库迁移 / B03 统一响应与异常
- B04：注册、登录、JWT 过滤器、`/me`；未登录 401、密码错误 401、重名 409
- 测试：13 passed（含 H2 独立测试库，不依赖本机 MySQL）
- 文档：架构图、数据库手册、接口约定、文档治理

## 阻塞

- 无（AI Key / Ollama 留到 M1）

## 恢复命令

```powershell
cd D:\AI_learn\backend
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=local   # 8080
cd ..\frontend; npm run dev                                    # 5173
```

提交前：`pwsh scripts/doc-check.ps1`；演示账号 `demo_77468 / passw0rd`（本地库）。

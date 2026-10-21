# 项目状态

> 仪表盘：只写当前，≤35 行 / 1.8k 字符。历史去 progress，决策去 ADR。

- 更新时间：2026-10-21
- 阶段：**M0 前端完成，后端待开始**
- 代码提交：`b75358b`（前端界面）
- 文档提交：`10b8743`（后端指南）
- 上下文：最小开工包 ≈ 1.3k tokens（STATUS + 模块 §0）
- 远程：`git@github.com:tutuchacha/AI_app.git`（已同步）

## 下一步（按顺序）

1. 人工：管理员执行 `net start mysql`，建库（`docs/modules/backend.md` §3）
2. `M0-B01` 后端工程初始化（pom / Application / application-local.yml）
3. `M0-B02` Flyway → `B03` 统一响应 → `B04` JWT → `B05` Swagger/Actuator → `B06` 前端联调

## 已完成

- 架构、环境、文档规则、JDK 21
- 前端 8 页 + Mock 流式 + 学习提示（`b75358b`）
- 后端 B01–B06 分步指南（`docs/modules/backend.md`）

## 阻塞

- MySQL 服务 Stopped（服务名 `mysql`）
- 模型 Key / Ollama（M1 前准备即可）

## 恢复命令

```powershell
cd D:\AI_learn
git pull
cd frontend; npm run dev   # http://127.0.0.1:5173
```

演示账号 `demo / 123456`（M0 Mock）。

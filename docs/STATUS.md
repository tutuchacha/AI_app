# 项目状态

> 仪表盘：只写当前，≤35 行 / 1.8k 字符。历史去 progress，决策去 ADR。

- 更新时间：2026-10-24
- 阶段：**M0 完成**（前端真实登录 + 后端骨架）
- 代码提交：`f69abdb`（B05）；B06 本次
- 数据库：`127.0.0.1:3306 / ai_app`，Flyway `V1` 已执行
- 鉴权：BCrypt + JWT（HS256，2h）；前端 `api/http.ts` 自动带 token
- 模型：百炼 `qwen-plus` 已验证（Key 在环境变量）
- 远程：`git@github.com:tutuchacha/AI_app.git`（已同步）

## 下一步（M1）

1. `M1-01` 迁移 `conversation` / `message` 表（V2）
2. `M1-02` `AiGateway` 接百炼（`qwen-plus`），统一模型调用
3. `M1-03` SSE 聊天接口 + 前端 `mockTransport` 换真实流

## 已完成

- 前端 8 页、路由守卫、Mock 流式（`b75358b`）
- B01 骨架 / B02 建库 / B03 统一响应 / B04 JWT / B05 Swagger+指标
- B06：真实登录（`api/http.ts`、`api/auth.ts`、store 改造、登录页错误提示）
- 验证：17 后端测试通过；`npm run typecheck` + `build` 通过
- 联调：5173 代理登录 `demo / demo1234` → token → `/api/auth/me` 成功
- 文档：架构图、数据库手册、接口约定、ADR-0003、治理规则

## 阻塞

- 无（启动后端后再登录；Mock 需 `VITE_USE_MOCK=true`）

## 恢复命令

```powershell
cd D:\AI_learn\backend
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=local   # 8080
cd ..\frontend; npm run dev                                    # 5173
```

演示账号 `demo / demo1234`；提交前 `pwsh scripts/doc-check.ps1`。

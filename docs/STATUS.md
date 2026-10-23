# 项目状态

> 仪表盘：只写当前，≤35 行 / 1.8k 字符。历史去 progress，决策去 ADR。

- 更新时间：2026-10-23
- 阶段：**M0 后端进行中（B01 完成）**
- 代码提交：`b75358b`（前端界面）+ B01 后端骨架
- 上下文：最小开工包 ≈1.4k tokens（STATUS + 模块 §0）
- 远程：`git@github.com:tutuchacha/AI_app.git`（已同步）

## 下一步（按顺序）

1. 人工：提供 MySQL `ai_app` 账号密码，写入 `application-local.yml`
2. `M0-B02` Flyway + `V1__init.sql`（`sys_user`），建库后开启 `flyway.enabled` 与 `health.db`
3. 之后 `B03` 统一响应 → `B04` JWT → `B05` Swagger/Actuator → `B06` 前端联调

## 已完成

- 架构、环境、文档规则、JDK 21
- 前端 8 页 + Mock 流式 + 学习提示（`b75358b`）
- 后端 B01：`backend/pom.xml`（Boot 3.5.16 / JDK 21）、`AiApplication`、
  `application.yml`、`SecurityConfig`（临时放行）、`CorsConfig`
- 验证：`mvn test` 通过；`/actuator/health` = UP；`/v3/api-docs` = 200

## 阻塞

- MySQL 有 root 密码未知，`ai_app` 库/账号尚未创建（B02 需要）
- 模型 Key / Ollama（M1 前准备即可）

## 恢复命令

```powershell
cd D:\AI_learn\backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=local   # 8080
cd ..\frontend; npm run dev                                # 5173
```

演示账号 `demo / 123456`（M0 Mock）。

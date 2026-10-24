# 项目状态

> 仪表盘：只写当前，≤35 行 / 1.8k 字符。历史去 progress，决策去 ADR。

- 更新时间：2026-10-24
- 阶段：**M0 后端 B01–B04 完成；模型 Key 已就绪**
- 代码提交：`bb3c827`（B04）
- 数据库：`127.0.0.1:3306 / ai_app`，Flyway `V1` 已执行
- 鉴权：BCrypt + JWT（HS256，2h），`/api/auth/*` 已可用
- 模型：百炼 qwen-plus 已验证；Key 在用户环境变量 `DASHSCOPE_API_KEY`
- 远程：`git@github.com:tutuchacha/AI_app.git`（已同步）

## 下一步（按顺序）

1. `M0-B05` Swagger 接口文档 + Actuator 指标
2. `M0-B06` 前端接真实登录 → M0 收尾
3. M1：会话表、SSE 聊天、`AiGateway` 接百炼

## 已完成

- 前端 8 页 + Mock 流式 + 学习提示（`b75358b`）
- B01 骨架 / B02 建库迁移 / B03 统一响应 / B04 注册登录 JWT
- 测试 13 passed；真实库注册→登录→me 走通
- 模型：qwen-plus 真实调用成功；OCR 模型 `qwen-vl-ocr-2025-11-20` 留 M3
- 文档：架构图、数据库手册、接口约定、ADR-0003、文档治理

## 阻塞

- 无（换终端/IDE 后需重启以读到新环境变量）

## 恢复命令

```powershell
cd D:\AI_learn\backend
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=local   # 8080
cd ..\frontend; npm run dev                                    # 5173
```

提交前：`pwsh scripts/doc-check.ps1`。

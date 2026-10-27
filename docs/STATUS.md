# 项目状态

> 仪表盘：只写当前，≤35 行 / 1.8k 字符。历史去 progress，决策去 ADR。

- 更新时间：2026-10-27
- 阶段：**M2 RAG 进行中**（M1 已完成；M2-01/02 完成）
- 数据库：Flyway `V1`~`V4 rag` 已执行（本机 MySQL 已迁移到 v4）
- 鉴权：BCrypt + JWT（HS256，2h）；`SecurityConfig` 放行 `ASYNC/ERROR` 与 `/ws/**`
- 模型：百炼 `qwen-plus`；`max-tokens` 默认 4096；超时默认 60s
- 远程：`git@github.com:tutuchacha/AI_app.git`

## 下一步（按顺序）

1. **M2-03 Embedding**：百炼向量批量化、回填 `document_chunk.embedding`、重建索引
2. M2-04/05：检索（topK/阈值）+ 聊天 `citation`
3. 装 Docker 后跑 `ops/docker-compose.yml`，确认 Grafana 看板

## 已完成

- M0：前端 8 页 + 真实登录；后端骨架/建库/统一响应/JWT/Swagger/指标
- M1-01~09：落库、SSE/WS（持久 + 心跳重连）、上下文、限流/超时、Prometheus 埋点
- M1-10：容器化部署件（Dockerfile / nginx / compose）— 本机无 Docker，未实跑
- M2-01：`V4__rag.sql` 三张表；`knowledge/` 知识库 CRUD，归属与切片参数校验
- M2-02：文档上传（Tika 解析、≤20MB、sha256 去重）+ 切片落库 + 删除/重建；解析异步，失败标 `FAILED`
- 验证：73 后端测试通过；真库（8081）上传→READY 4 片→重建→删除全链路通过

## 阻塞

- 本机未装 Docker：镜像/compose 与 Grafana 面板未实跑
- 8080 端口有旧的 dev 实例在跑，验证时另起 8081 规避（未动旧进程）

## 恢复命令

```powershell
cd D:\AI_learn\backend
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=local   # 8080，启动即跑 Flyway
cd ..\frontend; npm run dev                                    # 5173
```

演示账号 `demo / demo1234`；提交前 `pwsh scripts/doc-check.ps1`。

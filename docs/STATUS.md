# 项目状态

> 仪表盘：只写当前，≤35 行 / 1.8k 字符。历史去 progress，决策去 ADR。

- 更新时间：2026-10-28
- 阶段：**M3 完成 + 切库验证完成**；M4 待定
- 数据库：VM `192.168.101.137:5432/ai_app`（PG 16 + pgvector），Flyway `V1`~`V7` 全部 success
- 切库结论：**成功**，代码无 MySQL 语法残留；RAG/分析全链路已在 PG 跑通
  （注册→建库→上传→切片→1024 维回填→检索 0.69→FIELDS 分析 READY→删库级联清空）
- 测试：113 通过（H2 + memory 余弦，不覆盖 pgvector 路径）
- 模型：百炼 `qwen-plus`；向量 `text-embedding-v3`（1024 维）；超时 60s
- 远程：`git@github.com:tutuchacha/AI_app.git`

## 下一步（按顺序）

1. 浏览器过一遍前端四流程（切库后回归）
2. 聊天选库增强（解绑、多库同检）待确认
3. M4 内容待确认（`companion` / `support` 文档尚未建）

## 已知限制

- HNSW：纯向量查询命中索引；带 `kb_id` 过滤时 10 万行实测退化为 seq scan，量大再优化
- `embedding_vec` 固定 1024 维，换向量模型需迁移
- VM 磁盘 89%（余 2.1G），Docker build cache 7.5G 可回收
- 回滚库为本机 MySQL 8.0.29 `C:\dev\mysql`（服务已停），**不在 VM**

## 恢复命令

```powershell
cd D:\AI_learn\backend; .\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=local"
cd ..\frontend; npm run dev
ssh root@192.168.101.137 "cd /opt/xingyu && docker compose -f docker-compose.vm.yml up -d"
```

- 测试：`backend` 下 `$env:JAVA_HOME='D:\JDK21'; .\mvnw.cmd test`（113）；前端 `npm run typecheck`

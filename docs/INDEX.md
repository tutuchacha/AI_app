# 文档索引

> 开工路径：`STATUS.md` 全文 → 本表对应行 → 模块文档 `§0 速览`。不要整目录扫描。

| 文档 | 内容 | 什么时候读 | 行数上限 |
| --- | --- | --- | --- |
| `STATUS.md` | 当前进度、下一步、阻塞 | **每次开工必读** | 35 |
| `00-架构方案.md` | 总体架构、技术选型、表结构、路线图 | 只读 §1；改架构再跳读 | 450 |
| `01-环境准备.md` | JDK/MySQL/Git/Ollama/Nginx 安装 | 换机器 / 环境报错 | 220 |
| `04-对标-yu-ai-agent.md` | 目标能力地图、差距、调整路线图 | 规划阶段 / 加 Agent | 200 |
| `modules/frontend.md` | M0 界面、目录职责、F0-01~F0-10 实现步骤 | 做前端 / 接后端 | 120 |
| `modules/backend.md` | M0 后端骨架、B01~B06 步骤、建库命令 | 做后端 / 联调 | 120 |
| `modules/database.md` | 库/账号/表结构/迁移规范/排查 | 改表 / 查数据 / 连库失败 | 120 |
| `ARCHITECTURE.md` | 架构图、页面→文件地图、包结构与关键链路 | 找代码 / 新人接手 | 140 |
| `adr/0001-ai-framework.md` | 为什么选 Spring AI | 质疑选型时 | 60 |
| `adr/0002-streaming-transport.md` | SSE + WebSocket 双通道 | 做 M1 流式时 | 60 |
| `modules/chat.md` | 聊天模块：接口、事件、状态 | 做 M1 时 | 120 |
| `progress/YYYY-MM.md` | 流水账，只追加 | 只读当月最后 3 条 | 单条 8 |

## 计划新增（用到再建，不预建空文件）

| 文档 | 触发时机 |
| --- | --- |
| `modules/rag.md` | M2 开始 |
| `modules/analysis.md` | M3 开始 |
| `modules/companion.md` | M4 开始 |
| `modules/support.md` | M4 开始 |
| `02-接口约定.md` | M0 接口定稿后 |
| `03-本地启动.md` | M0 骨架跑通后 |

## 维护规则

- 新增文档必须在本表加一行，否则视为临时草稿。
- 文档之间用相对链接，不复制内容。
- 超过上限就拆分；拆分后在原文档顶部留“已拆分至 …”。

# AGENTS.md — AI 协作与上下文规则

本文件约束所有 AI/人类协作者。目标：**20k 上下文也能随时接手**，不让文档膨胀拖垮会话。

## 1. 开工必读（按顺序，禁止全量扫描）

| 顺序 | 文件 | 预算 | 说明 |
| --- | --- | --- | --- |
| 1 | `docs/STATUS.md` | ≤ 1k tokens | 唯一进度入口：做到哪、下一步、阻塞 |
| 2 | `docs/INDEX.md` | ≤ 500 tokens | 只挑本次相关的一行 |
| 3 | 对应模块文档 / ADR | ≤ 2k tokens | 例如 `docs/modules/chat.md` |
| 4 | 代码 | 按需 | 用 grep/glob 定位，不整目录读 |

- **不要**默认读 `docs/progress/` 历史、旧 ADR、`docs/00-架构方案.md` 全文。
- 需要历史时，只读 `docs/progress/` **当月文件最后 3 条**。
- 用户说“继续/下一步”时，执行 1 → 2 → 3，然后直接开工。
- 回答保持简短；细节写进文件，不往对话里倒。

## 2. 每一步结束必须落盘（同一个 commit）

“一步”= 一个可验证的小目标（一个接口、一个页面、一次重构）。

1. **重写** `docs/STATUS.md`（不是追加）：更新“现在做到哪 / 下一步 / 阻塞 / 最近提交”。
2. **追加** `docs/progress/YYYY-MM.md`，每条 ≤ 8 行，格式：

```markdown
## 2026-10-21 / M0-02 初始化 Git
- 结果: 本地仓库 + SSH remote，首个 commit
- 验证: git log --oneline -1
- 下一步: 生成后端骨架
- commit: 9382684
```

3. 有新决策 → 新增 `docs/adr/NNNN-短标题.md`（一页，不修改旧 ADR）。
4. 新增文档 → 在 `docs/INDEX.md` 加一行。
5. 提交信息：`<阶段>-<序号> <动词>: <结果>`，例如 `M1-03 feat: SSE 聊天接口`。

## 3. 文档体积硬限制

| 文件 | 上限 | 超限处理 |
| --- | --- | --- |
| `docs/STATUS.md` | 60 行 | 删除过期内容，只留当前 |
| `docs/INDEX.md` | 80 行 | 合并同类文档 |
| `docs/modules/*.md` | 200 行 | 拆子文档，INDEX 登记 |
| `docs/adr/*.md` | 60 行 | 新开 ADR 取代，旧文标记 Superseded |
| `docs/progress/*.md` | 单条 8 行 | 按月分文件，不合并 |
| `README.md` | 80 行 | 细节移到 docs |

原则：**STATUS 是仪表盘，progress 是流水账，ADR 是决策，modules 是手册**，四者不重复，互相链接。

## 4. 代码约定

- 后端包按业务垂直切：`chat / companion / support / knowledge / analysis / meta`。
- 业务代码不直接依赖 Spring AI 具体类，统一走 `AiGateway`；传输层不写业务。
- 数据库变更只走 Flyway，禁止手改表。
- 密钥只进环境变量或 `application-local.yml`（gitignored）。
- 每个阶段结束保证：能启动、有最小测试、可回滚。

## 5. 环境速查

JDK 21（本机 `D:\JDK21`）、Maven 3.6.3、Node 24、MySQL 8（`net start mysql`）、SSH remote
`git@github.com:tutuchacha/AI_app.git`。端口：前端 5173、后端 8080、MySQL 3306、
Prometheus 9090、Grafana 3000、Ollama 11434。详见 `docs/01-环境准备.md`。

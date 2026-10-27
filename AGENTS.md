# AGENTS.md — AI 协作与上下文规则

目标：**20k 上下文也能接手**。文档是交接单，对话不是。

## 0. 会话纪律（省上下文）

- 一个任务一个会话；完成一步就收尾，不在同一会话连续做多步。
- 回答默认 ≤15 行，只贴关键 diff / 命令，不贴全文。
- 读过的文件不重复读；定位代码用 grep/glob，不整目录读。
- 开工只读：`STATUS.md` 全文 + `INDEX.md` 对应行 + 模块文档 `§0 速览`。
- 需要细节时，只读对应步骤的 10–20 行，不读整篇。
- 不读：README、`00-架构方案.md` 全文、`progress/` 历史、旧 ADR（除非用户明确要求）。

## 1. 开工必读（按顺序，禁止全量扫描）

| 顺序 | 文件 | 读多少 | 预算 |
| --- | --- | --- | --- |
| 1 | `docs/STATUS.md` | 全文 | ≤1.8k 字符 |
| 2 | `docs/INDEX.md` | 只挑本次相关行 | ≤2.2k 字符 |
| 3 | 模块文档 `§0 速览` | 10 行左右 | ≤500 tokens |
| 4 | 代码 | grep/glob 定位 | 按需 |

- 用户说“继续/下一步”：执行 1 → 2 → 3，复述现状后直接开工。
- 需要历史：`Get-Content docs/progress/YYYY-MM.md -Tail 30`（约最后 3 条）。

## 2. 每一步结束必须落盘（同一个 commit）

“一步”= 一个可验证的小目标（一个接口、一个页面、一次重构）。

1. **重写** `docs/STATUS.md`（不是追加）：更新阶段、下一步、阻塞、提交号。
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

## 3. 文档体积硬限制（行数 / 字符）

| 文件 | 上限 | 超限处理 |
| --- | --- | --- |
| `AGENTS.md` | 75 行 / 3.4k | 删除重复规则 |
| `README.md` | 80 行 / 3.4k | 细节移到 docs |
| `docs/STATUS.md` | 35 行 / 1.8k | 删除过期内容 |
| `docs/INDEX.md` | 45 行 / 2.2k | 合并同类 |
| `docs/modules/*.md` | 120 行 / 5k | 拆子文档 |
| `docs/adr/*.md` | 60 行 / 4k | 新 ADR 取代 |
| `docs/progress/*.md` | 单条 8 行 / 600 字符 | 按月分文件 |

- 检查：`pwsh scripts/doc-check.ps1`（预算 + 死链 + INDEX 覆盖，提交前跑一次）。
- 文档分层、单一事实来源、改代码同步清单见 `docs/DOC-GOVERNANCE.md`。
- 原则：STATUS 仪表盘、progress 流水账、ADR 决策、modules 手册，不重复。

## 4. 代码约定

- 后端包按业务垂直切：`chat / companion / support / knowledge / analysis / meta`。
- 业务代码不直接依赖 Spring AI 具体类，统一走 `AiGateway`；传输层不写业务。
- 数据库变更只走 Flyway，禁止手改表。
- 密钥只进环境变量或 `application-local.yml`（gitignored）。
- 每个阶段结束保证：能启动、有最小测试、可回滚。

## 5. 环境速查

JDK 21（本机 `D:\JDK21`）、Maven 3.6.3、Node 24、SSH remote `git@github.com:tutuchacha/AI_app.git`。
数据库 **PostgreSQL 16 + pgvector**，跑在 CentOS VM `192.168.101.137:5432`（Docker，`ops/docker-compose.vm.yml`）；
本机不再用 MySQL（旧库保留作回滚，服务已停）。端口：前端 5173、后端 8080、PG 5432、
Prometheus 9090、Grafana 3000、Ollama 11434。详见 `docs/01-环境准备.md`。

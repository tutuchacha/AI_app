# AGENTS.md — 协作与上下文规则

目标：**20k 上下文能接手**；文档是交接单，对话不是。

## 1. 开工（三步，禁止全量扫描）

1. `docs/STATUS.md` 全文（≤1.5k 字符）。
2. `docs/INDEX.md` 只挑本次相关行，再读对应模块文档 `§0 速览`（≤10 行）。
3. 用 grep/glob 定位代码；只读命中的 10–20 行，不整目录读、不重复读。

- 用户说「继续 / 下一步」：走完三步，复述现状后直接开工。
- 需要历史：`Get-Content docs/progress/YYYY-MM.md -Tail 30`（约最后 3 条）。
- 不读：README、`00-架构方案.md` 全文、旧 ADR、进度历史（除非用户明确要求）。
- 不把整篇文档贴进对话；引用只贴关键行。

## 2. 收尾（同一 commit）

「一步」= 一个可验证的小目标（一个接口、一个页面、一次重构）。

1. **重写** `docs/STATUS.md`（不是追加）：阶段、当前能力、下一步、限制。
2. **追加** `docs/progress/YYYY-MM.md` 一条（≤8 行）：结果 / 验证 / 下一步 / commit。
3. 新决策 → 新增 `docs/adr/NNNN-短标题.md`；新文档 → `docs/INDEX.md` 加一行。
4. 跑 `pwsh scripts/doc-check.ps1`；超限先删重复内容，不要加新文档兜底。
5. 提交：`<阶段>-<序号> <动词>: <结果>`，例如 `D-03 feat: 反馈复盘接口`。
6. 给用户的收尾消息 ≤15 行，不粘整段日志或文档。

## 3. 写作上限（字符）

AGENTS 1.4k、STATUS 1.5k、INDEX 1.4k、modules 5k、ADR 4k；单条 progress ≤600。
分层与单一事实来源见 `docs/DOC-GOVERNANCE.md`，预算由 `scripts/doc-budget.ps1` 强制。

## 4. 代码约定

- 垂直包：`chat / knowledge / analysis / support / date / meta`；业务不直接依赖 Spring AI，统一走 `AiGateway`。
- 数据库变更只走 Flyway；密钥只进环境变量或 `application-local.yml`（gitignored）。
- 每步保证：能启动、有最小测试、可回滚。

## 5. 环境

JDK 21（`D:\JDK21`）、Node 24；PG 16 + pgvector `192.168.101.137:5432`；端口 5173/8080。
完整环境、账号与镜像见 `docs/01-环境准备.md`。

# 0006 文档开工包预算

- 日期：2026-10-28
- 状态：生效

## 背景

压缩上下文后，交接摘要仍占大量窗口：开工链路 `AGENTS → STATUS → INDEX → 模块 §0`
加上会话里回贴的整段文档，常把 20k 窗口吃掉近一半。根因不是单个文档太长，
而是**必读链路太长、描述重复、上限太宽**（AGENTS 3.4k、INDEX 2.2k、STATUS 1.8k）。

## 决策

1. 定义「开工包」= `AGENTS.md` + `docs/STATUS.md` + `docs/INDEX.md` + 模块 `§0`，
   目标 **≤1.6k tokens**（约 2.8k 字符），由 `scripts/doc-budget.ps1` 打印实际值。
2. 单文件上限收紧：

| 文件 | 行 | 字符 |
| --- | --- | --- |
| `AGENTS.md` | 40 | 1400 |
| `docs/STATUS.md` | 30 | 1500 |
| `docs/INDEX.md` | 32 | 1400 |

3. `INDEX.md` 不再维护「计划新增」表；ADR 合并为一行列出路径，减少重复说明。
4. `STATUS.md` 只写当前（阶段 / 能力 / 下一步 / 限制），历史细节留在 modules 与 progress。
5. progress 按月分文件；单月超过 40 条时拆成 `YYYY-MMa.md` / `YYYY-MMb.md`，
   `INDEX.md` 只登记通配行，`doc-check` 不查 progress。
6. 收尾回给用户的总结 ≤15 行，不复制日志、不复述文档。

## 后果

- 开工链路约省一半 token；跨会话交接只看 STATUS 即可恢复。
- 细节查询成本转移到 modules（按需读 §0 或指定小节），不再全量读。
- 上限由脚本强制，超限先删重复内容，禁止新增「补充说明」类文档。

## 验证

`pwsh scripts/doc-check.ps1` 输出开工包估算；本 ADR 落地后应 ≤1.6k tokens。

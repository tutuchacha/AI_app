# 文档分析模块（M3）

> 先读 §0。建表见 `backend/.../db/migration/V6__document_analysis.sql`（V7 只改列注释），接口契约见 `02-接口约定.md` §7。
> 分析输入是已切好的 `document_chunk`，不重新解析文件；输出统一 Markdown。

## 0. 速览

- 目标：选 READY 文档 → 摘要 / 结构 / 追问 / 要素 → 后台调模型 → 结果可回看、导出
- 当前：M3 完成（M3-01 三类 + M3-02 要素抽取，真库跑通四类任务）
- 代码：`backend/.../analysis/`（`AnalysisController/Service/Worker/Runner`、`AnalysisType`、`AnalysisProperties`）
- 前端：`views/AnalysisView.vue` + `api/analysis.ts`
- 表：`document_analysis`（一个任务一行，结果存 `content`）
- 配置：`app.analysis.async`（默认 true）、`max-chars`、`max-chunks`、`max-tokens`
- 验收：四类都到 `READY`；文档非 READY 400；别人的文档 403

## 1. 数据模型

| 字段 | 说明 |
| --- | --- |
| `document_id` / `kb_id` | 归属；`kb_id` 冗余便于按库统计 |
| `analysis_type` | `SUMMARY` / `OUTLINE` / `QUESTIONS` / `FIELDS`，枚举名直接落库 |
| `status` | `PENDING → RUNNING → READY / FAILED` |
| `content` | Markdown 结果；失败时为空 |
| `model` / `prompt_tokens` / `completion_tokens` / `latency_ms` | 展示「谁算的、多贵、多快」 |
| `error_msg` | 失败原因，截断 500 字 |

- 索引：`(document_id, id)`、`(document_id, analysis_type, id)`，后者给「同类型在跑」查询用。
- 删除：文档删除走 `ON DELETE CASCADE`，应用层不手工清分析记录（删库 → 文档 → 分析链式清理）。

## 2. 接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/documents/{documentId}/analysis` | body `{type}`；文档须 `READY`，返回任务（默认 `PENDING`） |
| GET | `/api/documents/{documentId}/analysis` | 该文档记录，id 倒序，最多 20 条 |
| GET | `/api/analysis/{id}` | 单任务详情，前端按此轮询 |
| DELETE | `/api/analysis/{id}` | 删除记录，需重新分析 |

- 归属复用 `DocumentService.requireOwned`：文档不存在 404、不属于本人 403。
- `type` 大小写不敏感；为空或未知值 400，且在落库前拒绝。

## 3. 状态机与并发

- 创建：校验归属 → 校验 `READY` → 解析类型 → 查同文档同类型是否已有 `PENDING/RUNNING`。
- 有在跑的任务直接返回它，避免前端连点打两次模型；不新建行。
- 落库后由 `AnalysisRunner`（独立 bean，`@Async("analysisExecutor")`）调 `AnalysisWorker.run`。
- Worker 先把状态改 `RUNNING`，再取切片拼正文、调 `AiGateway.complete`，最后写 `READY` 或 `FAILED`。
- 失败不抛出：只把本任务标 `FAILED` + `error_msg`，线程池不受影响；前端可「重试」再建一条。
- 进程重启后遗留的 `PENDING/RUNNING` 不会自动续跑，需前端重试（M3 已知限制）。

## 4. 提示词

- 统一 system：只依据给定正文作答、不补充外部事实、信息不足要说明、简体中文。
- 再拼接 `AnalysisType.instruction()`：
  - `SUMMARY`：3~6 句，覆盖目的 / 对象 / 结论或数据，不用列表
  - `OUTLINE`：`##` 一级章节 ≤8 个，每章 `-` 2~5 条要点，不编造
  - `QUESTIONS`：6~8 个有序问题，每条下一行「考察点：…」
  - `FIELDS`：Markdown 三列表 `| 要素 | 内容 | 原文依据 |`，覆盖主体、金额数量、时间期限、结论待办，缺项写「未提及」，≤10 行
- user 消息：`文档名 + 正文`；`temperature=0.2`，`maxTokens=app.analysis.max-tokens`。
- 正文来源：按 `chunk_index` 升序拼前 `max-chunks` 片，超过 `max-chars` 截断并附「（正文较长，已截断）」。

## 5. 配置

| 键 | 默认 | 说明 |
| --- | --- | --- |
| `app.analysis.async` | `true` | 测试设 `false`，接口内同步跑完便于断言 |
| `app.analysis.max-chars` | `12000` | 拼进提示词的正文上限 |
| `app.analysis.max-chunks` | `60` | 最多读取切片数 |
| `app.analysis.max-tokens` | `2000` | 单次输出上限 |

- 线程池 `analysisExecutor`：核心 2 / 最大 4 / 队列 50，与 `ragExecutor` 分开，避免解析和模型调用互相排队。

## 6. 前端页面

- 左侧：知识库下拉 → READY 文档下拉 → 四张类型卡片 → 开始分析 → 最近任务列表（可删）。
- 右侧：摘要 / 结构 / 追问 / 要素四 Tab，各显示该类型最新一条；含模型、tokens、耗时、重试、导出 Markdown。
- 轮询：创建后 1.2s 一次 `GET /api/analysis/{id}`，约 2 分钟未完成则提示稍后刷新。
- Markdown：无第三方依赖，`renderMarkdown` 先 HTML 转义再拼标签，经 `v-html` 渲染；支持标题 / 有序 / 无序 / 引用 / 表格 / 粗体 / 行内代码。表格以 `|` 行识别，跳过 `|---|` 分隔行、首行作表头。
- 切换知识库或文档会清空轮询与当前结果，避免串数据。

## 7. 坑

- CORS 只放行 5173：临时 Vite 用 5174 登录会 403，验证必须占 5173。
- 真实模型耗时不均：追问 6~8 条带考察点，实测约 19s；前端提示写 5~20s，不要按同步接口设超时。
- 测试库 `schema-test.sql` 需同步建 `document_analysis`，否则 `@SpringBootTest` 起不来。
- 结果里的「考察点：」是模型自由输出，渲染时按行首匹配，不保证所有模型都严格照做。
- `analysis_type` 注释已在 V6 落库，Flyway 校验不允许改旧迁移，新增类型只能再写一条 `MODIFY COLUMN`（V7）。

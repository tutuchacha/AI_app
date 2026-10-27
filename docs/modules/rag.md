# RAG 模块（M2）

> 先读 §0。表结构见 `db/migration/V4__rag.sql`，接口契约见 `02-接口约定.md` §6。
> 向量先落 MySQL JSON + Java 侧相似度，规模上来再换向量库（M6）。

## 0. 速览

- 目标：知识库 → 文档上传解析 → 切片向量化 → 检索 → 聊天带 `citation` 事件
- 当前：M2-05 完成（聊天带引用，M2 RAG 全部收尾）；前端知识库管理页待 M3
- 代码：`knowledge/`（`KnowledgeBase*`、`Document*`、`TextChunker`、`RetrievalService`、`CosineSimilarity`）
- 聊天接入：`ChatOrchestrator` + `ChatRequest.kbId`，事件与落库见 `chat.md` §4/§5
- 验收：登录后建库 → 列表可见 → 改/删生效；别人的库 403、不存在 404
- 表：`knowledge_base` / `document` / `document_chunk`

## 1. 数据模型

| 表 | 关键字段 | 说明 |
| --- | --- | --- |
| `knowledge_base` | `user_id`、`name`、`embedding_model`、`chunk_size`、`chunk_overlap`、`document_count` | 切片参数按库配置，改 `embedding_model` 需重建索引 |
| `document` | `kb_id`、`filename`、`ext`、`size`、`sha256`、`status`、`chunk_count`、`error_msg` | 状态机 `PENDING → PARSING → INDEXING → READY / FAILED` |
| `document_chunk` | `document_id`、`kb_id`、`chunk_index`、`content`、`embedding`、`token_count`、`metadata_json` | `embedding` 为 JSON 数组（M2-03 起必有值），维度随向量模型 |

- 删除链：库 → 文档 → 切片，全部走外键 `ON DELETE CASCADE`，应用层不手工清子表。
- 同库去重按 `(kb_id, sha256)` 查，重复上传返回已有文档而不是再切一份。

## 2. 接口

| 方法 | 路径 | 说明 | 状态 |
| --- | --- | --- | --- |
| GET | `/api/knowledge-bases` | 当前用户的库，id 倒序，最多 50 | ✅ M2-01 |
| POST | `/api/knowledge-bases` | 新建，`name` 必填 | ✅ M2-01 |
| GET | `/api/knowledge-bases/{id}` | 详情 | ✅ M2-01 |
| PUT | `/api/knowledge-bases/{id}` | 全量覆盖，未传字段回落默认 | ✅ M2-01 |
| DELETE | `/api/knowledge-bases/{id}` | 级联删文档与切片 | ✅ M2-01 |
| POST | `/api/knowledge-bases/{id}/documents` | 上传文档：multipart `file`，≤20MB，返回 `PENDING` |
| GET/DELETE | `/api/documents/{id}` | 详情（含 `status`/`chunkCount`）/ 删除；`POST .../reindex` 重建 |
| POST | `/api/knowledge-bases/{id}/search` | 检索调试，返回 chunk + score | ✅ M2-04 |

- 校验：`chunk_size` 100–4000，`chunk_overlap` 0–2000 且必须小于 `chunk_size`；默认 800 / 120。
- 归属：所有接口只看当前用户，越权一律 403；库不存在 404。

## 3. 分步

| 步骤 | 内容 | 状态 |
| --- | --- | --- |
| M2-01 | `V4` 建表 + 知识库 CRUD | ✅ |
| M2-02 | 上传（pdf/docx/txt/md、≤20MB）+ 解析 + 切片落库 | ✅ |
| M2-03 | Embedding 批量化 + `embedding` 回填 + 重建索引 | ✅ |
| M2-04 | 检索（topK=6、相似度阈值）+ `/search` 调试接口 | ✅ |
| M2-05 | 聊天接 RAG：拼 Prompt、`citation` 事件、持久化与刷新恢复 | ✅ |

## 4. 约束与坑

- 检索：问题向量化后与库内切片算余弦相似度，按 `app.rag.min-score`（默认 0.2）过滤、`app.rag.top-k`（默认 6）截断
- 检索只加载该库最近 5000 片；`embedding` 为空、JSON 损坏或维度与问题向量不一致的切片直接跳过，不让脏数据拖垮请求
- 向量：`AiGateway.embed(List)` 走百炼 `text-embedding-v3`（`app.ai.embedding-model`），按 `app.rag.embedding-batch-size`（默认 10）分批
- 先全部向量化成功再落切片：任一批失败整篇标 `FAILED`，不留下半套切片；重建索引会重算向量
- 文档 `READY` 即「文本已切 + 向量已齐」；无密钥时执行失败并提示 `DASHSCOPE_API_KEY`
- 业务代码不直接依赖 Spring AI 类：向量调用后续统一走 `AiGateway`，`knowledge/` 只依赖自己的端口。
- 解析异步：上传立即返回 `PENDING`，`ragExecutor` 线程池跑 Tika；测试用 `app.rag.async=false` 走同步。
- 解析在 `DocumentParser` 内闭环：失败只把该文档标 `FAILED` + `error_msg`，不抛到接口。
- Tika 抽取；txt/md 强制按 UTF-8（`CONTENT_TYPE` 覆盖），否则中文会被猜成 windows-1252。
- 同库 `sha256` 去重：重复上传直接返回已有文档，不再切片；切片上限 2000 片，超出截断并告警。
- 文件名只做展示，落盘名 `{documentId}.{ext}`（`app.rag.upload-dir`，默认 `./uploads`，已 gitignore）。
- 删除文档显式清切片 + 删源文件，不依赖外键级联；Prompt 侧要求「只依据检索片段回答」。
- 聊天接 RAG（M2-05）：`ChatRequest.kbId` 先校验归属再 `prepare`，避免越权请求留下脏消息；库绑定到 `conversation.kb_id`。
- 提示词：最多 6 片、每片截 500 字拼进系统提示，要求资料不足就说「资料中没有相关信息」；引用卡片用 120 字摘要。
- 引用持久化在 `message.citation_json`（`V5`）；检索异常只降级为无引用回答，不让聊天失败。
- 前端引用卡片（`MessageItem.vue`）已按 `{id,title,snippet,score}` 渲染，M2-05 只补后端契约。

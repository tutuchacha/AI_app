# 对标分析：liyupi/yu-ai-agent

> 用途：把「理想目标」拆成可执行的能力清单。本文补充并调整 `00-架构方案.md` §8 的路线图。
> 仓库：https://github.com/liyupi/yu-ai-agent（master，约 2.7k star，2026-01 最后更新）

## 1. 它到底是什么

一个 **AI 应用教学项目**，不是生产系统。核心交付两样东西：

1. **AI 恋爱大师**：多轮对话 + 记忆 + RAG + 工具调用 + MCP。
2. **YuManus 超级智能体**：ReAct 循环，自主调用搜索/下载/PDF/终端等工具完成任务。

代码量不大（159 个文件，后端约 40 个 Java 类），价值在 **AI 能力链路完整**，而不是业务复杂度。

## 2. 技术栈（实际代码）

| 层 | 选型 |
| --- | --- |
| 后端 | Java 21、Spring Boot 3.4.4、Spring AI 1.0.0 |
| 模型 | 阿里云百炼 DashScope、Ollama 本地模型、LangChain4j 对比 demo |
| RAG | Markdown Reader、Token 切分、关键词增强、查询改写、PgVector |
| 记忆 | `MessageWindowChatMemory`（内存）、Kryo 文件持久化 demo |
| 工具 | 文件、联网搜索、网页抓取、资源下载、终端、PDF 生成、终止 |
| 协议 | MCP Client + 独立 MCP Server（图片搜索，stdio/SSE） |
| 智能体 | `BaseAgent` → `ReActAgent` → `ToolCallAgent` → `YuManus` |
| 流式 | SSE 三种写法：`Flux<String>`、`ServerSentEvent`、`SseEmitter` |
| 前端 | Vue 3.2 + Vite 4 + axios + vue-router（JS，无 TS/Pinia/UI 库） |
| 部署 | 前端 Dockerfile + nginx.conf，后端 Serverless |

## 3. 值得学的设计

| 设计 | 位置 | 价值 |
| --- | --- | --- |
| ChatClient + Advisor 链 | `app/LoveApp`、`advisor/` | 记忆、日志、RAG 都是 Advisor，可插拔 |
| 自定义 Advisor | `MyLoggerAdvisor`、`ReReadingAdvisor` | 理解请求/响应增强与重读技巧 |
| RAG 全链路 | `rag/` | Loader → Splitter → 关键词增强 → 向量库 → 查询改写 → 自定义增强器 |
| 结构化输出 | `LoveApp#doChatWithReport` | `.entity(Record.class)` 直接出对象 |
| 工具注册 | `tools/ToolRegistration` | 工具即 Bean，统一暴露给模型 |
| MCP 双端 | 主项目 + `yu-image-search-mcp-server` | 既当客户端也写服务端 |
| ReAct 状态机 | `agent/BaseAgent` | `IDLE/RUNNING/FINISHED/ERROR` + `maxSteps` 防死循环 |
| SSE 多实现 | `AiController` | 对比 Flux、ServerSentEvent、SseEmitter 的取舍 |
| 模型接入对比 | `demo/invoke/` | HTTP、SDK、Spring AI、LangChain4j 四种方式 |

## 4. 它的局限（不要照搬）

| 问题 | 影响 | 我们的做法 |
| --- | --- | --- |
| 无登录/用户隔离 | `chatId` 由客户端传，可越权读会话 | JWT + 所有表带 `user_id` |
| 无业务数据库 | 会话、报告不落库，重启即失 | PostgreSQL + Flyway |
| 记忆默认在内存 | 多实例/重启丢上下文 | 短期记忆落 `message` 表，长期记忆自研 |
| API Key 写在 yml | 易泄漏 | 环境变量 + 密钥加密 |
| 无配额/限流/成本控制 | 工具与模型可被刷爆 | Bucket4j + 每日 token 配额 |
| 终端/文件工具无沙箱 | 提示词注入可直接危害主机 | 工具白名单 + 目录沙箱 + 人工确认 |
| 无监控 | 出问题只能翻日志 | Micrometer + Prometheus + Grafana |
| 测试以 demo 为主 | 回归靠手点 | 每模块最小集成测试 |
| 版本偏旧 | Boot 3.4.4 / Spring AI 1.0.0 | 我们用 3.5.16 / 1.1.x |
| 未声明 License | 不能直接商用/整段复制 | 只学思路，代码自己写 |

## 5. 你的目标画像

在它的能力之上，补上「能上线」的部分，形成 5 条主线：

```
业务层   chat  |  companion(AI恋爱)  |  support(客服)  |  analysis(文档)  |  agent
能力层   AiGateway | Advisor管道 | RAG | Memory | ToolRegistry | AgentEngine | MCP Client
平台层   Auth/JWT | PostgreSQL+pgvector/Flyway | 限流配额 | 可观测 | Nginx | 配置与密钥
```

- **AI 恋爱** = 角色卡 + 长期记忆 + RAG（情感知识）+ 结构化「恋爱报告」。
- **智能客服** = 知识库绑定 + 引用来源 + 工具（查订单）+ 转人工。
- **文档分析** = 上传解析 + 摘要/追问/字段抽取 + 导出。
- **超级智能体** = 工具调用 + MCP + ReAct，作为跨业务能力，不做成第四个孤岛。

## 6. 调整后的路线图

| 阶段 | 目标 | 对标能力 | 预估 |
| --- | --- | --- | --- |
| M0 | 前后端骨架、登录、Flyway、Swagger、Actuator | 工程化 | 3–5 天 |
| M1 | 聊天：SSE + WS 双通道、会话落库、Markdown | SSE、记忆 | 1–2 周 |
| M2 | RAG：上传、切分、Embedding、引用、检索测试 | RAG 全链路 | 2 周 |
| M2.5 | 工具调用 + MCP（先只读工具：搜索/时间/天气） | Tool、MCP | 1–2 周 |
| M3 | 文档分析：摘要、追问、抽取、异步进度 | 结构化输出 | 1 周 |
| M4 | 恋爱陪伴 + 客服：角色卡、长期记忆、转人工 | Advisor、记忆 | 2 周 |
| M4.5 | ReAct 智能体：状态机、步数上限、SSE 过程展示 | Agent | 2 周 |
| M5 | 可观测与部署：指标、Grafana、Nginx、压测 | 上线 | 1 周 |
| M6 | 评估与调优：评测集、重排、查询改写、成本 | 调优 | 持续 |

顺序原则：**先让一条链路端到端跑通，再横向加能力**；每个 M 结束都能演示、能回滚。

## 7. 每个阶段的可验证产出

- M1：浏览器逐字输出，刷新后历史还在，停止按钮有效。
- M2：上传一份 PDF，问 3 个问题都能给出处。
- M2.5：模型自动调用「时间/搜索」工具，失败有降级。
- M3：一份合同能抽出金额、甲方、期限，JSON 可下载。
- M4：同一角色跨会话记得用户偏好；客服答不出时给转人工入口。
- M4.5：一句「帮我做周末约会计划」，能看到思考/工具/结果三段过程。
- M5：Grafana 能看到 QPS、P95、token、RAG 命中率。

## 8. 和现有方案的关系

- 技术底座不变：Spring AI + Vue 3 + PostgreSQL（+ pgvector）+ Nginx + Prometheus/Grafana。
- 新增两块：**Tool/MCP**（M2.5）与 **ReAct Agent**（M4.5）。
- 向量库已调整：原计划「一期 SimpleVectorStore、二期自研 MySQL」，实际直接落 **PostgreSQL 16 + pgvector**
  （VM Docker，HNSW）；`memory`（Java 余弦）只留给 H2 测试，见 `modules/rag.md`。
- 不照搬它的前端与无鉴权结构；前端继续 TS + Pinia + Element Plus。

## 9. 结论

把它当 **能力地图**，不是 **代码模板**：

- 学它的 Advisor、RAG 调优、工具协议、ReAct 循环、SSE 取舍。
- 用我们的鉴权、数据库、监控、文档与测试把它「产品化」。
- 做完后，简历上可以写：**「支持多模型、RAG、工具调用与 ReAct 智能体的多场景 AI 应用，含可观测与部署」**，
  而不是「跟教程写了一个聊天机器人」。

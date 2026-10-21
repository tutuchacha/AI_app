# ADR-0001 选用 Spring AI 作为主框架

- 状态：Accepted
- 日期：2026-10-21

## 背景

后端是 Spring Boot 技术栈，需要在 Spring AI 与 LangChain4j 之间选择主框架，覆盖聊天、RAG、工具调用与可观测性。

## 决策

采用 **Spring Boot 3.5.16 + Spring AI 1.1.8** 作为主框架；LangChain4j 仅在后续 `ai-lab` 模块做对比实验，不进入主链路。

## 理由

- 与 Spring Boot、Actuator、Micrometer、Security 原生集成，学习成本最低。
- `ChatClient` + Advisor 模型适合 RAG、会话记忆、工具调用的渐进式实现。
- 官方 ETL（Tika/PDF/Markdown Reader、Splitter）和 VectorStore 抽象齐全。
- 中文资料、示例与 Boot 版本匹配度高于 LangChain4j 在本项目的收益。

## 代价

- 强绑定 Spring 生态，独立部署或非 Spring 项目复用性弱。
- 部分高级 RAG 能力（重排、查询改写）需自行补齐。
- 版本升级节奏跟随 Spring，需关注 BOM 兼容性。

## 约束

- 业务代码通过自研 `AiGateway` 访问模型，禁止在 Controller/Service 直接 new Spring AI 类。
- 模型统一走 OpenAI 兼容协议，便于切换 Ollama / DeepSeek / 通义。

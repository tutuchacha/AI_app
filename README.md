# AI App

一个用于学习与实战的 AI 应用仓库，逐步实现：

- AI 通用聊天（流式输出、多模型、会话记忆）
- AI 恋爱陪伴（角色卡、人设、长期记忆）
- 智能客服（知识库问答、引用来源、转人工）
- 文档分析（摘要、追问、结构化抽取）
- RAG 知识库（上传、解析、切分、向量检索）
- 可观测性（Prometheus + Grafana）

## 技术栈

| 层 | 技术 |
| --- | --- |
| 后端 | JDK 21、Spring Boot 3.5、Spring AI 1.1、MyBatis-Plus、Flyway |
| 前端 | Vue 3、TypeScript、Vite、Pinia、Vue Router、Element Plus |
| 数据 | MySQL 8（业务 + 会话），SimpleVectorStore / 自研 MySQL 向量库 |
| 基础设施 | Nginx、Prometheus、Grafana、Ollama / OpenAI 兼容 API |

## 目录

```
backend/    Spring Boot 服务
frontend/   Vue 3 应用
infra/      Nginx / Prometheus / Grafana 配置与脚本
docs/       架构、环境、接口文档
```

## 从任意地方接手

1. 读 `docs/STATUS.md`（当前进度 / 下一步 / 阻塞）
2. 查 `docs/INDEX.md`（只挑相关文档）
3. 按 `AGENTS.md` 的规则推进，每步更新 STATUS 与 progress

远程：`git@github.com:tutuchacha/AI_app.git`（SSH）

## 文档

- [项目状态](docs/STATUS.md)
- [文档索引](docs/INDEX.md)
- [架构方案](docs/00-架构方案.md)
- [环境准备](docs/01-环境准备.md)
- [AI 协作规则](AGENTS.md)

## 快速开始

> 脚手架生成后补充。当前阶段请先按环境准备文档安装 JDK 21、启动 MySQL。

```bash
# 后端
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=local

# 前端
cd frontend
npm install
npm run dev
```

默认端口：前端 5173，后端 8080，MySQL 3306，Prometheus 9090，Grafana 3000。

## 约定

- 密钥、数据库密码只放在本地 `application-local.yml` 或环境变量，禁止提交。
- 数据库变更一律通过 Flyway 脚本。
- 每个阶段保持可启动、可回滚。

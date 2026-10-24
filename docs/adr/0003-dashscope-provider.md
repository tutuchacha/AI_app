# ADR 0003 模型服务选用阿里云百炼

- 状态：已采纳
- 日期：2026-10-24
- 相关：`docs/01-环境准备.md` §4、`docs/.env.example`

## 背景

M0 只做骨架，M1 开始需要真实模型体验。用户已有阿里云百炼（DashScope）账号，
按模型发放免费额度（约 1M tokens/模型），额度用尽可切换同类模型。
本地 Ollama 可作为离线兜底，但 7B 模型效果与云端有差距。

## 决策

1. 统一走 DashScope 的 OpenAI 兼容接口：
   `https://dashscope.aliyuncs.com/compatible-mode/v1`
2. 模型分工：
   - 聊天：`qwen-plus`（默认，可换 `qwen-max` / `qwen-flash`）
   - 图片/文档 OCR：`qwen-vl-ocr-2025-11-20`（M3 文档分析）
   - 向量：`text-embedding-v3`（M2 知识库）
3. 密钥只存环境变量 `DASHSCOPE_API_KEY`（本机已设用户级），仓库内只留占位。
4. 模型名与 base-url 走配置/`ai_model` 表，不写死在业务代码；统一经 `AiGateway`。

## 备选

- Ollama 本地：免费、离线，作为降级方案保留。
- DeepSeek：文本便宜，但无 OCR/Embedding 配套。

## 影响

- M1 引入 Spring AI 时只配 OpenAI 兼容 starter，不绑定厂商 SDK。
- 换模型/换服务商 = 改配置，不改代码。
- 额度按模型独立，需在控制台观察用量；Key 泄露需立即轮换。

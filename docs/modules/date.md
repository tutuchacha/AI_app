# 恋爱助手（date）

> D-01 建档；D-02 AI 建议 + 高德地点；D-03 问答式地点推荐；D-04 执行记录 + 反馈复盘；D-05 时间线。
> 定位是「收集信息 → AI 给选择 → 执行 → 回填反馈 → 逐轮优化」，不是角色陪伴。
> 原则：只优化表达与节奏，不做操控话术、不伪造身份、不收集对方隐私。

## 0. 速览（做 D-06 / 改恋爱助手只读这节）

- 入口：`/date`（`DateView.vue`），导航「恋爱助手」；原 `/companion` 与 `CompanionView.vue` 已删
- 后端包 `com.tutuchacha.ai.date`，前缀 `/api/date`
- 我的档案一人一份（`date_profile`，PUT 整体覆盖，空串=清空）；对象最多 20 个（`date_target`）
- 阶段 `MET / CHATTING / INVITED / DATING / TOGETHER / PAUSED`，缺省 `MET`，非法值 400，`stageLabel` 由后端下发
- 城市是档案字段 `date_profile.city`（前端新增对象默认继承），D-02 地点推荐直接当高德 `city` 用
- 建议：`POST /api/date/advice` 一次模型调用出结构化 JSON（目标 / 3 动作 / 话术 / 禁忌 / 成功判断），
  再按 `city` 查高德 POI 补 1~2 个地点；结果落 `date_advice`，同对象保留最近 5 条
- 高德 key 只在后端（`app.date.amap-key`，本机放 `application-local.yml`），查询失败降级为空地点、不影响建议
- 问答：`POST /api/date/ask` 先让模型解析 `{reply, area, keywords}`；有 area → `geocode` + `around`（3km，按距离），
  无 area → `place/text` 按城市搜；不落库，只返回建议 + 地点（默认 4 家，`ask-spot-limit`）
- 模型输出非 JSON → 降级：整段文本当回复 + 默认关键词（餐厅/公园），不 502；高德失败降级空地点
- 区域只到区县粒度时（如「西湖」落到区政府），改用该区域首个 POI 中心再搜周边
- 执行：`POST/PUT/GET/DELETE /api/date/actions`，记录「要做的事 + 做没做 + 对方反应」；同一对象最多 20 条
- 复盘：`POST /api/date/review` 用最近 5 条执行 + 最近一条建议判断 CONTINUE / ADJUST / SLOW_DOWN / PAUSE，
  并给建议阶段（只升/降一级，PAUSED 可任意互转）；不自动改阶段，前端一键应用
- 越权 403、不存在 404；`name` 必填，`age` 限 18~99
- 时间线：`GET /api/date/timeline?targetId=` 合并建档 / 建议 / 执行反馈 / 阶段变更，倒序最多 40 条；
  阶段真的变化才写 `date_stage_log`（`stageSource=REVIEW` 记复盘应用，缺省 MANUAL），同阶段重复保存不写

## 1. 表（V9 ~ V12）

| 表 | 关键字段 | 说明 |
| --- | --- | --- |
| `date_profile` | `user_id`(唯一)、`nickname`、`city`、`hobbies`、`personality`、`goal`、`notes` | 一个用户一行，FK → `sys_user` 级联删 |
| `date_target` | `user_id`、`name`、`channel`、`stage`、`interests`、`taboos`、`last_contact_at`、`notes` | 可多行，索引 `(user_id, id DESC)` |
| `date_advice` | `target_id`、`stage`、`city`、`goal`、`content`、`spots`、`model`、token/延迟 | 一次建议一行；`content`/`spots` 为 JSON；删对象级联删 |
| `date_action` | `target_id`、`advice_id`、`title`、`planned_at`、`status`、`result`、`note`、`done_at` | 执行 + 反馈合一；`advice_id` 删建议时置空 |
| `date_stage_log` | `target_id`、`from_stage`、`to_stage`、`source` | 阶段变更流水，只在阶段变化时写；时间线据此显示「刚认识 → 聊天中」 |

`taboos`（雷点）与 `last_contact_at` 都会进 D-02 提示词；`advice.stage` 是出建议时的阶段快照。

## 2. 接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/api/date/profile` | 未建档返回 `data=null` |
| `PUT` | `/api/date/profile` | 整体覆盖，未传字段即清空 |
| `GET` | `/api/date/targets` | 我的对象，id 倒序最多 20 个 |
| `POST` | `/api/date/targets` | 新建，超过 20 个 400 |
| `PUT` | `/api/date/targets/{id}` | 更新，非本人 403 |
| `DELETE` | `/api/date/targets/{id}` | 删除，非本人 403 |
| `POST` | `/api/date/advice` | 生成建议：`{"targetId":1}`；模型输出非法 JSON → 502 |
| `GET` | `/api/date/advice?targetId=1` | 最近 5 条，倒序；非本人 403 |
| `POST` | `/api/date/ask` | 问答：`{"question":"…","area":"西湖","targetId":1}`；`area`/`targetId` 可选 |
| `POST` | `/api/date/actions` | 新建执行：`{"targetId","title","detail?","adviceId?","plannedAt?"}` |
| `GET` | `/api/date/actions?targetId=1` | 最近 20 条，新在前；非本人 403 |
| `PUT` | `/api/date/actions/{id}` | 回填 `status` / `result` / `note`，只改传了的字段 |
| `DELETE` | `/api/date/actions/{id}` | 删除，非本人 403 |
| `POST` | `/api/date/review` | 复盘：`{"targetId":1}`；无执行记录 400，模型非 JSON 502 |
| `GET` | `/api/date/timeline?targetId=1` | 时间线：TARGET / ADVICE / ACTION / STAGE，倒序最多 40 条；非本人 403 |

`POST /advice` 返回 `goal`、`actions[{title,detail,timing}]`、`scripts[{scene,text}]`、`avoid[]`、`success`、
`spots[{name,address,category,location,rating,distance}]`、模型与 token 元信息；前端按 `location` 拼高德 URI。

`POST /ask` 返回 `reply`、`area`、`city`、`keywords[]`、`spots[]`（带 `distance`）、模型与 token 元信息；
不落库；`question` 空 → 400；模型输出非 JSON 时降级为纯文本回复。

## 3. 前端结构

- `AI 下一步`：目标 + 3 动作（时机）+ 话术（复制）+ 禁忌 + 成功判断 + 地点卡（点开高德）+ 元信息 + 历史
- `问问助手`：快捷问题 + 输入框（问题 + 可选区域）+ 气泡内嵌地点卡；切换对象清空，问答不落库
- `执行与反馈`：动作旁「+ 待办」→ 标完成/跳过、选反应、填备注；底部「复盘」出判定卡，
  阶段不同时给「更新阶段」（`PUT /targets/{id}` + `stageSource=REVIEW`）
- `时间线`：四类事件合流倒序，圆点按类型配色；增删改后自动重拉
- 表单：阶段 `stageOptions`、城市 `CITY_OPTIONS`（可搜索可自填）、日期 `YYYY-MM-DD`；阶段标签按小写类名配色

## 4. 验证记录（2026-10-28）

- `DateAssistantTest` 4/4：档案 upsert 与按人隔离、对象 CRUD + 越权 403、非法阶段 400、空称呼 400
- `DateAdviceTest` 4/4：结构化字段 + 地点补全、越权 403/缺失 404、空 targetId 400、模型输出非 JSON → 502
- `DateAskTest` 5/5：区域走周边（带距离、不调城市搜索）、无区域走城市搜索、空问题 400、越权 403、
  模型输出非 JSON 降级为纯文本 + 默认关键词
- `DateActionTest` 6/6：执行 CRUD + 完成/反馈 label、越权 403/缺失 404、空标题与非法枚举 400、
  复盘判定 + 建议阶段、跳级被忽略（DATING→TOGETHER 回退当前阶段）、无记录 400 / 非 JSON 502
- 真实问答：`demo` 问「今天去哪里玩，晚上吃个饭」+ 区域「西湖」，qwen-plus 2.6~3.1s，返回 4 家 3km 内地点
- `DateTimelineTest` 3/3：阶段变化才写流水（同阶段重复保存不写）、REVIEW / MANUAL 来源、时间线四类事件倒序、
  越权 403 / 缺失 404
- 全量后端 **144/144**；`npm run typecheck` 通过
- 高德 Web API 已验证可用（`place/text`、`geocode`、`place/around` 均 `status=1`），key 只在 `application-local.yml`，未入库
- 浏览器实测（demo / 小周）：动作「+ 待办」→ 完成 → 反应「反应不错」→ 备注 → 复盘（CONTINUE，2.4s）
  → 阶段 MET→CHATTING 一键应用；DB 核对 `date_action` DONE/GOOD + `date_target.stage=CHATTING`，Flyway V11 success
- 时间线实测：同一对象 5 条（4 建议 + 1 执行反馈）+ 建档，倒序正确；V12 后新产生的阶段变更会多一条 STAGE

## 5. 下一步

- 可选：问答结果加「换一批 / 就选这家」，把选择直接写进执行记录
- 可选：建议卡直接「全部加入待办」；阶段变更在时间线里支持回滚

# 恋爱助手（date）

> D-01 建档；D-02 AI 建议 + 高德地点；D-03 问答式地点推荐；D-04 执行记录 + 反馈复盘。
> 定位是「收集信息 → AI 给选择 → 执行 → 回填反馈 → 逐轮优化」，不是角色陪伴。
> 原则：只优化表达与节奏，不做操控话术、不伪造身份、不收集对方隐私。

## 0. 速览（做 D-05 / 改恋爱助手只读这节）

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
- 区域定位：地理编码只到区县/城市粒度时（如「西湖」落到区政府），改用该区域首个 POI 中心再搜周边
- 执行：`POST/PUT/GET/DELETE /api/date/actions`，记录「要做的事 + 做没做 + 对方反应」；同一对象最多 20 条
- 复盘：`POST /api/date/review` 用最近 5 条执行 + 最近一条建议判断 CONTINUE / ADJUST / SLOW_DOWN / PAUSE，
  并给建议阶段（只升/降一级，PAUSED 可任意互转）；不自动改阶段，前端一键应用
- 越权 403、不存在 404；`name` 必填，`age` 限 18~99

## 1. 表（V9 ~ V11）

| 表 | 关键字段 | 说明 |
| --- | --- | --- |
| `date_profile` | `user_id`(唯一)、`nickname`、`city`、`hobbies`、`personality`、`goal`、`notes` | 一个用户一行，FK → `sys_user` 级联删 |
| `date_target` | `user_id`、`name`、`channel`、`stage`、`interests`、`taboos`、`last_contact_at`、`notes` | 可多行，索引 `(user_id, id DESC)` |
| `date_advice` | `target_id`、`stage`、`city`、`goal`、`content`、`spots`、`model`、token/延迟 | 一次建议一行；`content`/`spots` 为 JSON；删对象级联删 |
| `date_action` | `target_id`、`advice_id`、`title`、`planned_at`、`status`、`result`、`note`、`done_at` | 执行 + 反馈合一；`advice_id` 删建议时置空 |

`taboos` 是雷点（不要聊的话题），`last_contact_at` 是最近一次联系日期，二者都会进 D-02 提示词。
`stage` 是出建议时的阶段快照，D-03 复盘时据此判断「推进还是降速」。

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

`POST /advice` 的响应字段：`goal`、`actions[{title,detail,timing}]`、`scripts[{scene,text}]`、
`avoid[]`、`success`、`spots[{name,address,category,location,rating,distance}]`、`model`、`promptTokens`、
`completionTokens`、`latencyMs`、`createdAt`。前端按 `location` 拼高德 URI 打开地图。

`POST /ask` 的响应字段：`reply`（≤120 字建议）、`area`、`city`、`keywords[]`、`spots[]`（带 `distance`）、
`model`、token 与 `latencyMs`。不落库；`question` 空 → 400；模型输出非 JSON 时降级为纯文本回复。

## 3. 前端结构

- 左栏：我的档案卡（昵称/年龄/城市/兴趣/目标，未建档显示引导）+ 对象列表（点选切换）+ 使用流程
- 右栏：选中对象的阶段、联系途径、最近联系、兴趣、雷点、备注
- `AI 下一步`：目标块 + 3 个动作（编号 + 时机）+ 话术（一键复制）+ 禁忌 chips + 成功判断 +
  地点卡（名称/类别/地址/评分，点开高德）+ 模型与 token 元信息 + 历史下拉
- `问问助手`：快捷问题 + 输入框（问题 + 可选区域）+ 消息气泡；助手气泡内嵌地点卡（含距离），
  切换对象时清空对话；问答不写库，刷新即丢
- `执行与反馈`：动作旁「+ 待办」→ 列表可标完成/跳过、选反应（好/一般/冷/没回复）、填备注；
  底部「复盘」出判定卡，阶段不同时给「更新阶段」按钮（走 `PUT /targets/{id}`）
- 两个 `el-dialog`：档案表单、对象表单（阶段下拉用 `stageOptions`，日期用 `value-format="YYYY-MM-DD"`）
- 阶段标签按 `stage` 小写类名配色（`chatting/invited/dating/together/paused`）

## 4. 验证记录（2026-10-28）

- `DateAssistantTest` 4/4：档案 upsert 与按人隔离、对象 CRUD + 越权 403、非法阶段 400、空称呼 400
- `DateAdviceTest` 4/4：结构化字段 + 地点补全、越权 403/缺失 404、空 targetId 400、模型输出非 JSON → 502
- `DateAskTest` 5/5：区域走周边（带距离、不调城市搜索）、无区域走城市搜索、空问题 400、越权 403、
  模型输出非 JSON 降级为纯文本 + 默认关键词
- `DateActionTest` 6/6：执行 CRUD + 完成/反馈 label、越权 403/缺失 404、空标题与非法枚举 400、
  复盘判定 + 建议阶段、跳级被忽略（DATING→TOGETHER 回退当前阶段）、无记录 400 / 非 JSON 502
- 真实问答：`demo` 问「今天去哪里玩，晚上吃个饭」+ 区域「西湖」，qwen-plus 65~73 tok / 2.6~3.1s，
  回复含散步 + 晚饭安排，返回 4 家 3km 内地点（日料 / 杭帮菜 / 展览），距离 573~2513m，前端气泡渲染正常
- 全量后端 **141/141**；`npm run typecheck` 通过
- 高德 Web API 已验证可用（`place/text`、`geocode`、`place/around` 均 `status=1`），key 只在 `application-local.yml`，未入库
- **未完成**：D-04 的浏览器链路实测（VM `192.168.101.137` 停机，8080 起不来），恢复后补测复盘卡

## 5. 下一步

- D-05：右侧时间线（建档 → 建议 → 执行 → 反馈）与阶段变更记录
- 恢复 VM 后：浏览器补测「+ 待办 → 完成/反馈 → 复盘 → 更新阶段」整条链路
- 可选：问答结果加「换一批 / 就选这家」，把选择直接写进执行记录

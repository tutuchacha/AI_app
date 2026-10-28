# 恋爱助手（date）

> D-01：建档（我的画像 + 对象档案）。定位是「收集信息 → AI 给选择 → 执行 → 回填反馈 → 逐轮优化」，
> 不是角色陪伴。策略在 D-02，反馈闭环 D-03，地点推荐随 D-02 接高德。
> 原则：只优化表达与节奏，不做操控话术、不伪造身份、不收集对方隐私。

## 0. 速览（做 D-02 / 改恋爱助手只读这节）

- 入口：`/date`（`DateView.vue`），导航「恋爱助手」；原 `/companion` 与 `CompanionView.vue` 已删
- 后端包 `com.tutuchacha.ai.date`，前缀 `/api/date`
- 我的档案一人一份（`date_profile`，PUT 整体覆盖，空串=清空）；对象最多 20 个（`date_target`）
- 阶段 `MET / CHATTING / INVITED / DATING / TOGETHER / PAUSED`，缺省 `MET`，非法值 400，`stageLabel` 由后端下发
- 城市是档案字段 `date_profile.city`（前端新增对象默认继承），D-02 地点推荐直接当高德 `city` 用
- 越权 403、不存在 404；`name` 必填，`age` 限 18~99

## 1. 表（V9）

| 表 | 关键字段 | 说明 |
| --- | --- | --- |
| `date_profile` | `user_id`(唯一)、`nickname`、`city`、`hobbies`、`personality`、`goal`、`notes` | 一个用户一行，FK → `sys_user` 级联删 |
| `date_target` | `user_id`、`name`、`channel`、`stage`、`interests`、`taboos`、`last_contact_at`、`notes` | 可多行，索引 `(user_id, id DESC)` |

`taboos` 是雷点（不要聊的话题），`last_contact_at` 是最近一次联系日期，二者都会进 D-02 提示词。

## 2. 接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/api/date/profile` | 未建档返回 `data=null` |
| `PUT` | `/api/date/profile` | 整体覆盖，未传字段即清空 |
| `GET` | `/api/date/targets` | 我的对象，id 倒序最多 20 个 |
| `POST` | `/api/date/targets` | 新建，超过 20 个 400 |
| `PUT` | `/api/date/targets/{id}` | 更新，非本人 403 |
| `DELETE` | `/api/date/targets/{id}` | 删除，非本人 403 |

## 3. 前端结构

- 左栏：我的档案卡（昵称/年龄/城市/兴趣/目标，未建档显示引导）+ 对象列表（点选切换）+ 使用流程
- 右栏：选中对象的阶段、联系途径、最近联系、兴趣、雷点、备注；`AI 下一步` 卡为 D-02 占位
- 两个 `el-dialog`：档案表单、对象表单（阶段下拉用 `stageOptions`，日期用 `value-format="YYYY-MM-DD"`）
- 阶段标签按 `stage` 小写类名配色（`chatting/invited/dating/together/paused`）

## 4. 验证记录（2026-10-28）

- `DateAssistantTest` 4/4：档案 upsert 与按人隔离、对象 CRUD + 越权 403、非法阶段 400、空称呼 400
- `npm run typecheck` 通过；浏览器 `/date` 建档 → 新增对象（城市默认继承）→ 切选 → 删除并刷新计数均正常
- 高德 Web API 已验证可用（`place/text` 杭州咖啡 `status=1`、600 条），key 只放后端配置

## 5. 下一步

- D-02：`POST /api/date/advice` + `date_advice` 表，结构化输出「本阶段目标 / 3 个动作 / 话术 / 禁忌 / 成功判断」，
  并按 `city` 调高德 POI 附 1–2 个约会地点（`app.date.amap-key`，前端不接触 key）
- D-03：`date_action` + `date_feedback`，复盘「继续 / 换策略 / 降速」并推进或回退阶段
- D-04：右侧时间线（建档 → 建议 → 执行 → 反馈）与阶段变更记录

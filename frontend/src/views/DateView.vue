<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Aim, Calendar, ChatDotRound, CopyDocument, Delete, Edit, Location, MagicStick, Plus, Position, Refresh, Star } from '@element-plus/icons-vue'
import LearningNote from '@/components/LearningNote.vue'
import {
  askDateAssistant,
  createDateAction,
  createDateAdvice,
  createDateTarget,
  deleteDateAction,
  deleteDateTarget,
  getDateProfile,
  listDateActions,
  listDateAdvice,
  listDateTargets,
  reviewDate,
  saveDateProfile,
  stageOptions,
  updateDateAction,
  updateDateTarget,
} from '@/api/date'
import type {
  AdviceAction,
  DateAction,
  DateActionPatch,
  DateActionResult,
  DateActionStatus,
  DateAdvice,
  DateProfile,
  DateReview,
  DateSpot,
  DateTarget,
  DateTargetInput,
} from '@/api/date'

const profile = ref<DateProfile | null>(null)
const targets = ref<DateTarget[]>([])
const selectedId = ref<number | null>(null)
const loading = ref(false)
const loadError = ref('')
const saving = ref(false)

const profileDialog = ref(false)
const targetDialog = ref(false)
const editingTargetId = ref<number | null>(null)
const advices = ref<DateAdvice[]>([])
const activeAdviceId = ref<number | null>(null)
const adviceLoading = ref(false)
const actions = ref<DateAction[]>([])
const reviewed = ref<DateReview | null>(null)
const reviewLoading = ref(false)

const RESULT_OPTIONS: { value: DateActionResult; label: string }[] = [
  { value: 'UNKNOWN', label: '未回填' },
  { value: 'GOOD', label: '反应不错' },
  { value: 'NEUTRAL', label: '一般' },
  { value: 'COLD', label: '有点冷' },
  { value: 'NO_REPLY', label: '没回复' },
]

const profileForm = reactive({
  nickname: '',
  gender: '',
  age: null as number | null,
  city: '',
  occupation: '',
  hobbies: '',
  personality: '',
  goal: '',
  notes: '',
})

const targetForm = reactive<DateTargetInput>({
  name: '',
  channel: '',
  stage: 'MET',
  age: null,
  city: '',
  interests: '',
  taboos: '',
  notes: '',
  lastContactAt: '',
})

const selected = computed(() => targets.value.find((item) => item.id === selectedId.value) ?? null)
const profileReady = computed(() => !!profile.value?.nickname)
const fmtAge = (age: number | null) => (age ? `${age} 岁` : '未填')
const activeAdvice = computed(
  () => advices.value.find((item) => item.id === activeAdviceId.value) ?? advices.value[0] ?? null,
)
const adviceTime = (iso: string) => (iso ? iso.slice(5, 16).replace('T', ' ') : '')

const subtitle = computed(() => {
  if (!profileReady.value) return '先写下自己的档案，AI 才能按你的性格和城市给建议'
  return `${profile.value?.nickname} · ${profile.value?.city || '未填城市'} · 跟进 ${targets.value.length} 个对象`
})

function message(err: unknown, fallback: string) {
  return err instanceof Error ? err.message : fallback
}

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    const [mine, list] = await Promise.all([getDateProfile(), listDateTargets()])
    profile.value = mine
    targets.value = list
    if (!list.some((item) => item.id === selectedId.value)) {
      selectedId.value = list[0]?.id ?? null
    }
  } catch (err) {
    loadError.value = message(err, '加载恋爱助手数据失败')
  } finally {
    loading.value = false
  }
}

function openProfileDialog() {
  Object.assign(profileForm, {
    nickname: profile.value?.nickname ?? '',
    gender: profile.value?.gender ?? '',
    age: profile.value?.age ?? null,
    city: profile.value?.city ?? '',
    occupation: profile.value?.occupation ?? '',
    hobbies: profile.value?.hobbies ?? '',
    personality: profile.value?.personality ?? '',
    goal: profile.value?.goal ?? '',
    notes: profile.value?.notes ?? '',
  })
  profileDialog.value = true
}

async function submitProfile() {
  saving.value = true
  try {
    profile.value = await saveDateProfile({ ...profileForm })
    ElMessage.success('档案已保存')
    profileDialog.value = false
  } catch (err) {
    ElMessage.error(message(err, '保存失败'))
  } finally {
    saving.value = false
  }
}

function openTargetDialog(target?: DateTarget) {
  editingTargetId.value = target?.id ?? null
  Object.assign(targetForm, {
    name: target?.name ?? '',
    channel: target?.channel ?? '',
    stage: target?.stage ?? 'MET',
    age: target?.age ?? null,
    city: target?.city ?? profile.value?.city ?? '',
    interests: target?.interests ?? '',
    taboos: target?.taboos ?? '',
    notes: target?.notes ?? '',
    lastContactAt: target?.lastContactAt ?? '',
  })
  targetDialog.value = true
}

async function submitTarget() {
  if (!targetForm.name.trim()) {
    ElMessage.warning('先给对方起个称呼')
    return
  }
  saving.value = true
  try {
    const saved = editingTargetId.value
      ? await updateDateTarget(editingTargetId.value, { ...targetForm })
      : await createDateTarget({ ...targetForm })
    ElMessage.success(editingTargetId.value ? '档案已更新' : '已新建对象档案')
    targetDialog.value = false
    await load()
    selectedId.value = saved.id
  } catch (err) {
    ElMessage.error(message(err, '保存失败'))
  } finally {
    saving.value = false
  }
}

async function removeTarget(target: DateTarget) {
  try {
    await ElMessageBox.confirm(`删除「${target.name}」的档案？建议记录会一并消失。`, '确认删除', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  try {
    await deleteDateTarget(target.id)
    ElMessage.success('已删除')
    await load()
  } catch (err) {
    ElMessage.error(message(err, '删除失败'))
  }
}

async function loadAdvice(targetId: number | null) {
  advices.value = []
  activeAdviceId.value = null
  if (!targetId) return
  try {
    advices.value = await listDateAdvice(targetId)
    activeAdviceId.value = advices.value[0]?.id ?? null
  } catch {
    advices.value = []
  }
}

async function generateAdvice() {
  if (!selected.value || adviceLoading.value) return
  adviceLoading.value = true
  try {
    const advice = await createDateAdvice(selected.value.id)
    advices.value = [advice, ...advices.value.filter((item) => item.id !== advice.id)].slice(0, 5)
    activeAdviceId.value = advice.id
    ElMessage.success('建议已生成')
  } catch (err) {
    ElMessage.error(message(err, '生成失败，请重试'))
  } finally {
    adviceLoading.value = false
  }
}

async function copyText(text: string) {
  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success('话术已复制')
  } catch {
    ElMessage.warning('复制失败，请手动选择')
  }
}

function amapLink(location: string, name: string) {
  return location
    ? `https://uri.amap.com/marker?position=${location}&name=${encodeURIComponent(name)}`
    : ''
}

interface AskMessage {
  role: 'user' | 'assistant'
  text: string
  spots?: DateSpot[]
  meta?: string
}

const QUICK_ASKS = [
  '今天去哪里玩，晚上吃个饭',
  '周末想约她出去，安排半天',
  '第一次见面去哪比较自然',
  '想找家安静的餐厅聊天',
]

const messages = ref<AskMessage[]>([])
const question = ref('')
const area = ref('')
const asking = ref(false)
const msgsEl = ref<HTMLElement | null>(null)
const lastArea = ref('')

async function scrollBottom() {
  await nextTick()
  if (msgsEl.value) msgsEl.value.scrollTop = msgsEl.value.scrollHeight
}

async function ask(text?: string) {
  const q = (text ?? question.value).trim()
  if (!q || asking.value) return
  messages.value.push({ role: 'user', text: q })
  question.value = ''
  asking.value = true
  await scrollBottom()
  try {
    const res = await askDateAssistant({
      question: q,
      area: area.value.trim() || undefined,
      targetId: selected.value?.id,
    })
    lastArea.value = res.area
    messages.value.push({
      role: 'assistant',
      text: res.reply || '这次没想好，换个说法再问一次？',
      spots: res.spots,
      meta: [res.city || '未填城市', res.area ? `周边 ${res.area}` : '全城', (res.latencyMs / 1000).toFixed(1) + 's']
        .filter(Boolean)
        .join(' · '),
    })
  } catch (err) {
    messages.value.push({ role: 'assistant', text: message(err, '推荐失败，请稍后再问一次') })
  } finally {
    asking.value = false
    await scrollBottom()
  }
}

async function loadActions(targetId: number | null) {
  actions.value = []
  reviewed.value = null
  if (!targetId) return
  try {
    actions.value = await listDateActions(targetId)
  } catch {
    actions.value = []
  }
}

async function addAction(act: AdviceAction) {
  if (!selected.value) return
  try {
    const created = await createDateAction({
      targetId: selected.value.id,
      adviceId: activeAdvice.value?.id ?? null,
      title: act.title,
      detail: act.detail,
    })
    actions.value = [created, ...actions.value]
    reviewed.value = null
    ElMessage.success('已加入待办')
  } catch (err) {
    ElMessage.error(message(err, '加入待办失败'))
  }
}

async function patchAction(action: DateAction, patch: DateActionPatch) {
  try {
    const updated = await updateDateAction(action.id, patch)
    actions.value = actions.value.map((item) => (item.id === updated.id ? updated : item))
    reviewed.value = null
  } catch (err) {
    ElMessage.error(message(err, '更新失败，请重试'))
  }
}

function setActionStatus(action: DateAction, status: DateActionStatus) {
  void patchAction(action, { status })
}

function onResultChange(action: DateAction, value: string) {
  void patchAction(action, { result: value as DateActionResult })
}

function onNoteChange(action: DateAction, value: string) {
  void patchAction(action, { note: value })
}

async function removeAction(action: DateAction) {
  try {
    await deleteDateAction(action.id)
    actions.value = actions.value.filter((item) => item.id !== action.id)
    reviewed.value = null
  } catch (err) {
    ElMessage.error(message(err, '删除失败'))
  }
}

async function runReview() {
  if (!selected.value || reviewLoading.value || !actions.value.length) return
  reviewLoading.value = true
  try {
    reviewed.value = await reviewDate(selected.value.id)
  } catch (err) {
    ElMessage.error(message(err, '复盘失败，请重试'))
  } finally {
    reviewLoading.value = false
  }
}

async function applyStage() {
  const target = selected.value
  const review = reviewed.value
  if (!target || !review) return
  try {
    const updated = await updateDateTarget(target.id, {
      name: target.name,
      channel: target.channel,
      stage: review.suggestedStage,
      age: target.age,
      city: target.city,
      interests: target.interests,
      taboos: target.taboos,
      notes: target.notes,
      lastContactAt: target.lastContactAt,
    })
    targets.value = targets.value.map((item) => (item.id === updated.id ? updated : item))
    reviewed.value = null
    ElMessage.success(`阶段已更新为「${updated.stageLabel}」`)
  } catch (err) {
    ElMessage.error(message(err, '更新阶段失败'))
  }
}

watch(selectedId, (id) => {
  void loadAdvice(id)
  void loadActions(id)
  messages.value = []
  lastArea.value = ''
})
onMounted(load)
</script>

<template>
  <div class="date-page">
    <LearningNote
      title="恋爱助手 = 建档 + 策略 + 反馈"
      :points="['先写清自己的画像与城市', '每个对象单独建档，按阶段给建议', '执行后回填反应，AI 再调整']"
      next="D-04 已接通：执行记录 + 复盘；下一步时间线"
    />

    <div class="page-head">
      <div>
        <h1 class="page-title">恋爱助手</h1>
        <p class="page-subtitle">{{ subtitle }}</p>
      </div>
      <span class="pill mint"><el-icon><Aim /></el-icon> 目标：牵手成功</span>
    </div>

    <div v-if="loadError" class="error-bar card">
      <span>{{ loadError }}</span>
      <el-button size="small" round :loading="loading" @click="load">重试</el-button>
    </div>

    <div class="date-layout">
      <aside class="side">
        <div class="mine card">
          <div class="card-top">
            <strong>我的档案</strong>
            <el-button text size="small" @click="openProfileDialog">
              <el-icon><Edit /></el-icon>
              {{ profileReady ? '编辑' : '去建档' }}
            </el-button>
          </div>
          <template v-if="profileReady">
            <div class="mine-name">
              <span class="avatar">{{ profile?.nickname?.slice(0, 1) }}</span>
              <div>
                <b>{{ profile?.nickname }}</b>
                <span>{{ [profile?.gender, fmtAge(profile?.age ?? null)].filter((v) => v && v !== '未填').join(' · ') || '未填基本信息' }}</span>
              </div>
            </div>
            <p class="mine-line">
              <el-icon><Location /></el-icon>
              {{ profile?.city || '未填城市，地点推荐会受限' }}
            </p>
            <p v-if="profile?.hobbies" class="mine-line">
              <el-icon><Star /></el-icon>
              {{ profile.hobbies }}
            </p>
            <p v-if="profile?.goal" class="mine-goal">目标：{{ profile.goal }}</p>
          </template>
          <p v-else class="mine-empty">
            还没有档案。写下性格、城市和目标，AI 才能给出贴合你的建议。
          </p>
        </div>

        <div class="targets card">
          <div class="card-top">
            <strong>对象档案</strong>
            <el-button text size="small" @click="openTargetDialog()">
              <el-icon><Plus /></el-icon>
              新增
            </el-button>
          </div>
          <p v-if="!targets.length" class="targets-empty">
            还没有对象。先把正在接触的人建个档，再从「AI 下一步」开始。
          </p>
          <ul v-else>
            <li
              v-for="item in targets"
              :key="item.id"
              :class="{ active: item.id === selectedId }"
              @click="selectedId = item.id"
            >
              <span class="avatar small">{{ item.name.slice(0, 1) }}</span>
              <div class="tg-main">
                <b>{{ item.name }}</b>
                <span>{{ item.channel || '未填途径' }} · {{ item.city || '未填城市' }}</span>
              </div>
              <span class="stage" :class="item.stage.toLowerCase()">{{ item.stageLabel }}</span>
            </li>
          </ul>
        </div>

        <div class="flow card">
          <h3>使用流程</h3>
          <ol>
            <li><i>1</i> 建档：我的画像 + 对方信息</li>
            <li><i>2</i> 出策略：本阶段下一步</li>
            <li><i>3</i> 执行后回填对方反应</li>
            <li><i>4</i> 复盘调整节奏与阶段</li>
          </ol>
        </div>
      </aside>

      <section class="detail">
        <div v-if="!selected" class="empty card">
          <el-icon :size="26"><Star /></el-icon>
          <h3>选一个对象开始</h3>
          <p>左侧新增对象档案后，这里会显示阶段、喜好和 AI 建议。</p>
          <el-button type="primary" round @click="openTargetDialog()">新建对象档案</el-button>
        </div>

        <template v-else>
          <div class="profile card">
            <div class="profile-head">
              <div class="avatar">{{ selected.name.slice(0, 1) }}</div>
              <div class="profile-id">
                <div class="profile-name">
                  <h2>{{ selected.name }}</h2>
                  <span class="stage" :class="selected.stage.toLowerCase()">{{ selected.stageLabel }}</span>
                </div>
                <p>
                  {{ [selected.channel, fmtAge(selected.age), selected.city].filter((v) => v && v !== '未填').join(' · ') || '信息待补充' }}
                </p>
              </div>
              <div class="profile-actions">
                <el-button size="small" round @click="openTargetDialog(selected)">
                  <el-icon><Edit /></el-icon> 编辑
                </el-button>
                <el-button size="small" round text type="danger" @click="removeTarget(selected)">
                  <el-icon><Delete /></el-icon>
                </el-button>
              </div>
            </div>

            <div class="facts">
              <div>
                <span>最近联系</span>
                <b>{{ selected.lastContactAt || '未记录' }}</b>
              </div>
              <div>
                <span>兴趣/在意的点</span>
                <b>{{ selected.interests || '未填' }}</b>
              </div>
              <div>
                <span>雷点</span>
                <b>{{ selected.taboos || '未填' }}</b>
              </div>
            </div>

            <p v-if="selected.notes" class="notes">
              <el-icon><Calendar /></el-icon>
              {{ selected.notes }}
            </p>
          </div>

          <div class="ask card">
            <div class="card-top">
              <strong><el-icon><ChatDotRound /></el-icon> 问问助手</strong>
              <span class="ask-hint">按城市与区域找地方</span>
            </div>

            <div v-if="!messages.length" class="ask-quick">
              <button v-for="q in QUICK_ASKS" :key="q" @click="ask(q)">{{ q }}</button>
            </div>

            <div v-else ref="msgsEl" class="msgs">
              <div v-for="(m, i) in messages" :key="i" class="msg" :class="m.role">
                <div class="bubble">
                  <p>{{ m.text }}</p>
                  <div v-if="m.spots && m.spots.length" class="spot-list">
                    <a
                      v-for="spot in m.spots"
                      :key="spot.name"
                      class="spot mini"
                      :href="amapLink(spot.location, spot.name)"
                      target="_blank"
                      rel="noreferrer"
                    >
                      <div class="spot-top">
                        <b>{{ spot.name }}</b>
                        <span v-if="spot.rating" class="rating">{{ spot.rating }}</span>
                      </div>
                      <p>{{ spot.address || '地址未提供' }}</p>
                      <span class="spot-cat">
                        {{ spot.category || '地点' }}<template v-if="spot.distance"> · {{ spot.distance }}m</template> · 高德打开
                      </span>
                    </a>
                  </div>
                  <span v-if="m.meta" class="msg-meta">{{ m.meta }}</span>
                </div>
              </div>
              <div v-if="asking" class="msg assistant">
                <div class="bubble typing">正在按区域查附近地点…</div>
              </div>
            </div>

            <div class="ask-bar">
              <el-input
                v-model="question"
                size="small"
                maxlength="200"
                placeholder="今天去哪里玩，晚上吃个饭"
                @keyup.enter="ask()"
              />
              <el-input
                v-model="area"
                size="small"
                class="ask-area"
                maxlength="20"
                placeholder="区域（可选）西湖"
                @keyup.enter="ask()"
              />
              <el-button size="small" type="primary" round :loading="asking" @click="ask()">发送</el-button>
            </div>
            <p v-if="lastArea" class="ask-foot">本次按「{{ lastArea }}」周边 3km 找的地方</p>
          </div>

          <div class="advice card">
            <div class="card-top">
              <strong>AI 下一步</strong>
              <div class="advice-actions">
                <span v-if="activeAdvice" class="tag-next">
                  {{ activeAdvice.stageLabel }} · {{ adviceTime(activeAdvice.createdAt) }}
                </span>
                <el-button
                  size="small"
                  round
                  type="primary"
                  :loading="adviceLoading"
                  @click="generateAdvice"
                >
                  <el-icon><Refresh /></el-icon>
                  {{ activeAdvice ? '重新生成' : '生成建议' }}
                </el-button>
              </div>
            </div>

            <template v-if="activeAdvice">
              <p class="advice-goal">{{ activeAdvice.goal }}</p>

              <div v-if="activeAdvice.actions.length" class="advice-block">
                <h4>接下来 3 个动作</h4>
                <ol class="acts">
                  <li v-for="(act, i) in activeAdvice.actions" :key="act.title + i">
                    <i>{{ i + 1 }}</i>
                    <div>
                      <div class="act-top">
                        <b>{{ act.title }}</b>
                        <span v-if="act.timing">{{ act.timing }}</span>
                        <el-button text size="small" class="act-add" @click="addAction(act)">+ 待办</el-button>
                      </div>
                      <p>{{ act.detail }}</p>
                    </div>
                  </li>
                </ol>
              </div>

              <div v-if="activeAdvice.scripts.length" class="advice-block">
                <h4>参考话术</h4>
                <div v-for="s in activeAdvice.scripts" :key="s.text" class="script">
                  <span class="scene">{{ s.scene }}</span>
                  <p>{{ s.text }}</p>
                  <el-button text size="small" @click="copyText(s.text)">
                    <el-icon><CopyDocument /></el-icon>
                  </el-button>
                </div>
              </div>

              <div v-if="activeAdvice.avoid.length" class="advice-block">
                <h4>别做这些</h4>
                <div class="chips">
                  <span v-for="item in activeAdvice.avoid" :key="item" class="chip avoid">{{ item }}</span>
                </div>
              </div>

              <div v-if="activeAdvice.success" class="advice-block">
                <h4>成功判断</h4>
                <p class="advice-success">{{ activeAdvice.success }}</p>
              </div>

              <div v-if="activeAdvice.spots.length" class="advice-block">
                <h4>约会地点 · {{ activeAdvice.city || '未填城市' }}</h4>
                <div class="spots">
                  <a
                    v-for="spot in activeAdvice.spots"
                    :key="spot.name"
                    class="spot"
                    :href="amapLink(spot.location, spot.name)"
                    target="_blank"
                    rel="noreferrer"
                  >
                    <div class="spot-top">
                      <b>{{ spot.name }}</b>
                      <span v-if="spot.rating" class="rating">{{ spot.rating }}</span>
                    </div>
                    <p>{{ spot.address || '地址未提供' }}</p>
                    <span class="spot-cat">
                      {{ spot.category || '地点' }} · 高德打开
                      <el-icon><Position /></el-icon>
                    </span>
                  </a>
                </div>
              </div>

              <div class="advice-meta">
                {{ activeAdvice.model }} · {{ activeAdvice.completionTokens }} tok ·
                {{ (activeAdvice.latencyMs / 1000).toFixed(1) }}s
              </div>

              <div v-if="advices.length > 1" class="history">
                <span>历史</span>
                <button
                  v-for="item in advices"
                  :key="item.id"
                  :class="{ on: item.id === activeAdvice.id }"
                  @click="activeAdviceId = item.id"
                >
                  {{ adviceTime(item.createdAt) }}
                </button>
              </div>
            </template>

            <template v-else>
              <p class="advice-empty">
                结合你的画像、对方阶段与城市，给出「阶段目标 / 3 个动作 / 参考话术 / 禁忌 / 成功判断」，
                并按城市附 1–2 个高德推荐的约会地点。
              </p>
              <el-button type="primary" round :loading="adviceLoading" @click="generateAdvice">
                生成建议
              </el-button>
            </template>
          </div>

          <div class="actions card">
            <div class="card-top">
              <strong>执行与反馈</strong>
              <div class="advice-actions">
                <span class="ask-hint">{{ actions.length ? `${actions.length} 条记录` : '先记一条' }}</span>
                <el-button
                  size="small"
                  round
                  type="primary"
                  :loading="reviewLoading"
                  :disabled="!actions.length"
                  @click="runReview"
                >
                  <el-icon><MagicStick /></el-icon>
                  复盘
                </el-button>
              </div>
            </div>

            <p v-if="!actions.length" class="advice-empty">
              点「AI 下一步」里任意动作的「+ 待办」，做完回填对方反应，再让助手判断继续、换策略还是降速。
            </p>

            <ul v-else class="act-list">
              <li v-for="a in actions" :key="a.id">
                <div class="act-head">
                  <span class="act-status" :class="a.status.toLowerCase()">{{ a.statusLabel }}</span>
                  <b>{{ a.title }}</b>
                  <span v-if="a.plannedAt" class="act-date">{{ a.plannedAt.slice(5) }}</span>
                  <el-button text size="small" type="danger" @click="removeAction(a)">
                    <el-icon><Delete /></el-icon>
                  </el-button>
                </div>
                <p v-if="a.detail" class="act-detail">{{ a.detail }}</p>
                <div class="act-tools">
                  <el-button
                    v-if="a.status !== 'DONE'"
                    size="small"
                    round
                    @click="setActionStatus(a, 'DONE')"
                  >
                    完成
                  </el-button>
                  <el-button
                    v-if="a.status !== 'SKIPPED'"
                    size="small"
                    round
                    text
                    @click="setActionStatus(a, 'SKIPPED')"
                  >
                    跳过
                  </el-button>
                  <el-select
                    :model-value="a.result"
                    size="small"
                    class="result-select"
                    @change="onResultChange(a, $event)"
                  >
                    <el-option
                      v-for="opt in RESULT_OPTIONS"
                      :key="opt.value"
                      :label="opt.label"
                      :value="opt.value"
                    />
                  </el-select>
                  <el-input
                    :model-value="a.note"
                    size="small"
                    class="note-input"
                    maxlength="200"
                    placeholder="对方怎么反应（可选）"
                    @change="onNoteChange(a, $event)"
                  />
                </div>
              </li>
            </ul>

            <div v-if="reviewed" class="review" :class="reviewed.verdict.toLowerCase()">
              <div class="review-top">
                <b>{{ reviewed.verdictLabel }}</b>
                <span>建议阶段：{{ reviewed.suggestedStageLabel }}</span>
              </div>
              <p>{{ reviewed.reason }}</p>
              <p class="review-next">下一步：{{ reviewed.nextStep }}</p>
              <el-button
                v-if="reviewed.stageChanged"
                size="small"
                round
                type="primary"
                @click="applyStage"
              >
                更新阶段为「{{ reviewed.suggestedStageLabel }}」
              </el-button>
            </div>
          </div>
        </template>
      </section>
    </div>

    <el-dialog v-model="profileDialog" title="我的档案" width="560px" append-to-body>
      <el-form label-width="92px" label-position="left">
        <el-form-item label="昵称">
          <el-input v-model="profileForm.nickname" maxlength="40" placeholder="AI 对你的称呼" />
        </el-form-item>
        <el-form-item label="性别">
          <el-input v-model="profileForm.gender" maxlength="16" placeholder="自填，可留空" />
        </el-form-item>
        <el-form-item label="年龄">
          <el-input-number v-model="profileForm.age" :min="18" :max="99" controls-position="right" />
        </el-form-item>
        <el-form-item label="城市">
          <el-input v-model="profileForm.city" maxlength="40" placeholder="杭州" />
        </el-form-item>
        <el-form-item label="职业">
          <el-input v-model="profileForm.occupation" maxlength="60" />
        </el-form-item>
        <el-form-item label="兴趣日常">
          <el-input
            v-model="profileForm.hobbies"
            type="textarea"
            :rows="2"
            maxlength="500"
            placeholder="跑步、咖啡、看展……"
          />
        </el-form-item>
        <el-form-item label="性格沟通">
          <el-input
            v-model="profileForm.personality"
            type="textarea"
            :rows="2"
            maxlength="500"
            placeholder="慢热，文字比当面自然"
          />
        </el-form-item>
        <el-form-item label="关系目标">
          <el-input
            v-model="profileForm.goal"
            type="textarea"
            :rows="2"
            maxlength="500"
            placeholder="找长期关系，希望三个月内稳定"
          />
        </el-form-item>
        <el-form-item label="补充">
          <el-input v-model="profileForm.notes" type="textarea" :rows="2" maxlength="1000" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="profileDialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitProfile">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="targetDialog"
      :title="editingTargetId ? '编辑对象档案' : '新建对象档案'"
      width="560px"
      append-to-body
    >
      <el-form label-width="92px" label-position="left">
        <el-form-item label="称呼" required>
          <el-input v-model="targetForm.name" maxlength="40" placeholder="昵称即可，别填真实全名" />
        </el-form-item>
        <el-form-item label="阶段">
          <el-select v-model="targetForm.stage" style="width: 100%">
            <el-option v-for="opt in stageOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="认识途径">
          <el-input v-model="targetForm.channel" maxlength="60" placeholder="同事 / 朋友介绍 / 社交软件" />
        </el-form-item>
        <el-form-item label="年龄">
          <el-input-number v-model="targetForm.age" :min="18" :max="99" controls-position="right" />
        </el-form-item>
        <el-form-item label="城市">
          <el-input v-model="targetForm.city" maxlength="40" />
        </el-form-item>
        <el-form-item label="最近联系">
          <el-date-picker
            v-model="targetForm.lastContactAt"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="选日期"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="兴趣在意点">
          <el-input
            v-model="targetForm.interests"
            type="textarea"
            :rows="2"
            maxlength="500"
            placeholder="露营、猫、日料"
          />
        </el-form-item>
        <el-form-item label="雷点">
          <el-input
            v-model="targetForm.taboos"
            type="textarea"
            :rows="2"
            maxlength="500"
            placeholder="不喜欢被追问前任"
          />
        </el-form-item>
        <el-form-item label="备注">
          <el-input
            v-model="targetForm.notes"
            type="textarea"
            :rows="3"
            maxlength="1000"
            placeholder="最近聊了什么、回消息频率、约过的地点"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="targetDialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitTarget">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.date-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
  height: calc(100vh - 60px - 54px);
  min-height: 560px;
}

.date-layout {
  display: flex;
  flex: 1;
  gap: 16px;
  min-height: 0;
}

.side {
  display: flex;
  flex-direction: column;
  gap: 10px;
  width: 300px;
  flex: 0 0 300px;
  overflow-y: auto;
}

.mine,
.targets,
.flow {
  padding: 13px 14px;
}

.card-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 9px;
  font-size: 12px;
}

.card-top strong {
  font-weight: 700;
}

.mine-name {
  display: flex;
  align-items: center;
  gap: 9px;
}

.mine-name b {
  display: block;
  font-size: 13px;
}

.mine-name span {
  color: var(--xy-muted);
  font-size: 10.5px;
}

.avatar {
  display: grid;
  place-items: center;
  width: 34px;
  height: 34px;
  flex: 0 0 34px;
  border-radius: 11px;
  background: var(--xy-violet-soft);
  color: var(--xy-violet);
  font-size: 14px;
  font-weight: 700;
}

.avatar.small {
  width: 26px;
  height: 26px;
  flex: 0 0 26px;
  border-radius: 9px;
  font-size: 11.5px;
}

.mine-line {
  display: flex;
  align-items: center;
  gap: 5px;
  margin-top: 8px;
  color: #5f6377;
  font-size: 11px;
}

.mine-goal {
  margin-top: 8px;
  padding: 6px 8px;
  border-radius: 8px;
  background: var(--xy-mint-soft);
  color: #4d5a62;
  font-size: 10.5px;
  line-height: 1.6;
}

.mine-empty,
.targets-empty {
  color: var(--xy-muted);
  font-size: 10.5px;
  line-height: 1.7;
}

.targets ul {
  display: flex;
  flex-direction: column;
  gap: 6px;
  max-height: 268px;
  margin: 0;
  padding: 0;
  overflow-y: auto;
  list-style: none;
}

.targets li {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 9px;
  border: 1px solid var(--xy-line);
  border-radius: 9px;
  background: #fbfbfe;
  cursor: pointer;
  transition: border-color 0.15s ease, background 0.15s ease;
}

.targets li:hover,
.targets li.active {
  border-color: #cfc4ff;
  background: #f7f4ff;
}

.tg-main {
  min-width: 0;
  flex: 1;
}

.tg-main b {
  display: block;
  font-size: 12px;
}

.tg-main span {
  color: var(--xy-muted);
  font-size: 9.5px;
}

.stage {
  padding: 1px 7px;
  border-radius: 6px;
  background: #f0f1f6;
  color: #6a6e80;
  font-size: 9px;
  white-space: nowrap;
}

.stage.chatting {
  background: var(--xy-violet-soft);
  color: var(--xy-violet);
}

.stage.invited {
  background: #fff2e4;
  color: #c4762b;
}

.stage.dating {
  background: #ffeaf0;
  color: #c14b6b;
}

.stage.together {
  background: var(--xy-mint-soft);
  color: var(--xy-mint);
}

.stage.paused {
  background: #f0f1f6;
  color: var(--xy-muted);
}

.flow h3 {
  margin-bottom: 8px;
  font-size: 12px;
  font-weight: 700;
}

.flow ol {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin: 0;
  padding: 0;
  list-style: none;
  color: #5f6377;
  font-size: 10.5px;
}

.flow li {
  display: flex;
  align-items: center;
  gap: 7px;
}

.flow i {
  display: grid;
  place-items: center;
  width: 16px;
  height: 16px;
  border-radius: 5px;
  background: var(--xy-violet-soft);
  color: var(--xy-violet);
  font-size: 9px;
  font-style: normal;
  font-weight: 700;
}

.detail {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 12px;
  min-width: 0;
  overflow-y: auto;
}

.empty {
  display: flex;
  flex: 1;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 40px;
  color: var(--xy-muted);
  text-align: center;
}

.empty h3 {
  color: var(--xy-ink);
  font-size: 15px;
}

.empty p {
  max-width: 300px;
  font-size: 11.5px;
  line-height: 1.7;
}

.profile {
  padding: 16px;
}

.profile-head {
  display: flex;
  align-items: center;
  gap: 11px;
}

.profile-id {
  flex: 1;
  min-width: 0;
}

.profile-name {
  display: flex;
  align-items: center;
  gap: 8px;
}

.profile-name h2 {
  font-size: 17px;
  font-weight: 700;
}

.profile-id p {
  margin-top: 3px;
  color: var(--xy-muted);
  font-size: 11px;
}

.profile-actions {
  display: flex;
  gap: 4px;
}

.facts {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
  margin-top: 14px;
}

.facts div {
  padding: 8px 10px;
  border-radius: 9px;
  background: #fafafd;
}

.facts span {
  display: block;
  color: var(--xy-muted);
  font-size: 9.5px;
}

.facts b {
  display: block;
  margin-top: 3px;
  color: #4d5060;
  font-size: 11.5px;
  font-weight: 600;
  line-height: 1.5;
  word-break: break-word;
}

.notes {
  display: flex;
  gap: 5px;
  margin-top: 10px;
  color: #5f6377;
  font-size: 11px;
  line-height: 1.7;
}

.ask {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 16px;
}

.ask .card-top {
  gap: 8px;
}

.ask-hint {
  color: var(--xy-muted);
  font-size: 9.5px;
}

.ask-quick {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.ask-quick button {
  padding: 4px 10px;
  border: 1px solid var(--xy-line);
  border-radius: 999px;
  background: #fbfbfe;
  color: #5a5e70;
  font-size: 10.5px;
  cursor: pointer;
  transition: border-color 0.15s ease, color 0.15s ease;
}

.ask-quick button:hover {
  border-color: #cfc4ff;
  color: var(--xy-violet);
}

.msgs {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: 320px;
  overflow-y: auto;
}

.msg {
  display: flex;
}

.msg.user {
  justify-content: flex-end;
}

.bubble {
  max-width: 86%;
  padding: 8px 11px;
  border-radius: 12px;
  background: #fbfbfe;
  border: 1px solid var(--xy-line);
}

.msg.user .bubble {
  border-color: transparent;
  background: var(--xy-violet-soft);
}

.bubble p {
  color: #4d5060;
  font-size: 11.5px;
  line-height: 1.75;
  white-space: pre-wrap;
}

.msg.assistant .bubble {
  max-width: 94%;
}

.bubble.typing {
  color: var(--xy-muted);
  font-size: 10.5px;
}

.spot-list {
  display: grid;
  gap: 6px;
  margin-top: 8px;
}

.spot.mini {
  padding: 7px 9px;
  background: #fff;
}

.spot.mini .spot-top b {
  font-size: 11px;
}

.spot.mini .spot-cat {
  margin-top: 3px;
}

.msg-meta {
  display: block;
  margin-top: 6px;
  color: #a7aab8;
  font-size: 9px;
}

.ask-bar {
  display: flex;
  align-items: center;
  gap: 6px;
}

.ask-bar :deep(.el-input) {
  flex: 1;
  min-width: 0;
}

.ask-bar .ask-area {
  flex: 0 0 132px;
}

.ask-foot {
  color: #a7aab8;
  font-size: 9px;
}

.advice {
  display: flex;
  flex-direction: column;
  align-items: stretch;
  gap: 10px;
  padding: 16px;
}

.advice-actions {
  display: flex;
  align-items: center;
  gap: 6px;
}

.tag-next {
  padding: 1px 7px;
  border-radius: 6px;
  background: #fff2e4;
  color: #c4762b;
  font-size: 9px;
}

.advice-goal {
  padding: 9px 11px;
  border-radius: 10px;
  background: linear-gradient(115deg, #f6f2ff, #fff7f2);
  color: #3f4254;
  font-size: 12.5px;
  font-weight: 600;
  line-height: 1.7;
}

.advice-block h4 {
  margin-bottom: 7px;
  color: var(--xy-muted);
  font-size: 9.5px;
  font-weight: 700;
  letter-spacing: 0.05em;
}

.acts {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.acts li {
  display: flex;
  gap: 9px;
}

.acts i {
  display: grid;
  place-items: center;
  width: 18px;
  height: 18px;
  flex: 0 0 18px;
  border-radius: 6px;
  background: var(--xy-violet-soft);
  color: var(--xy-violet);
  font-size: 9.5px;
  font-style: normal;
  font-weight: 700;
}

.act-top {
  display: flex;
  align-items: baseline;
  gap: 7px;
}

.act-top b {
  font-size: 12px;
}

.act-top span {
  color: #b8842f;
  font-size: 9.5px;
}

.acts p {
  margin-top: 2px;
  color: #6a6e80;
  font-size: 10.5px;
  line-height: 1.65;
}

.script {
  display: flex;
  align-items: flex-start;
  gap: 7px;
  padding: 8px 9px;
  border: 1px solid var(--xy-line);
  border-radius: 9px;
  background: #fbfbfe;
}

.script + .script {
  margin-top: 6px;
}

.scene {
  flex: 0 0 auto;
  padding: 1px 6px;
  border-radius: 5px;
  background: var(--xy-mint-soft);
  color: var(--xy-mint);
  font-size: 9px;
}

.script p {
  flex: 1;
  color: #4d5060;
  font-size: 11px;
  line-height: 1.7;
}

.chips {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.chip {
  padding: 2px 8px;
  border-radius: 7px;
  font-size: 10px;
}

.chip.avoid {
  background: #fff1f1;
  color: #b45151;
}

.advice-success {
  color: #4d5060;
  font-size: 11px;
  line-height: 1.7;
}

.spots {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(190px, 1fr));
  gap: 8px;
}

.spot {
  display: block;
  padding: 9px 10px;
  border: 1px solid var(--xy-line);
  border-radius: 10px;
  background: #fbfbfe;
  color: inherit;
  text-decoration: none;
  transition: border-color 0.15s ease, transform 0.15s ease;
}

.spot:hover {
  border-color: #cfc4ff;
  transform: translateY(-1px);
}

.spot-top {
  display: flex;
  align-items: center;
  gap: 6px;
}

.spot-top b {
  min-width: 0;
  flex: 1;
  overflow: hidden;
  font-size: 11.5px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.rating {
  color: #c4762b;
  font-size: 9.5px;
}

.spot p {
  margin-top: 3px;
  color: var(--xy-muted);
  font-size: 9.5px;
  line-height: 1.55;
}

.spot-cat {
  display: flex;
  align-items: center;
  gap: 2px;
  margin-top: 5px;
  color: var(--xy-violet);
  font-size: 9px;
}

.advice-meta {
  color: #a7aab8;
  font-size: 9px;
}

.history {
  display: flex;
  align-items: center;
  gap: 6px;
  padding-top: 8px;
  border-top: 1px dashed var(--xy-line);
  color: var(--xy-muted);
  font-size: 9.5px;
}

.history button {
  padding: 2px 7px;
  border: 1px solid var(--xy-line);
  border-radius: 6px;
  background: #fff;
  color: #6a6e80;
  font-size: 9.5px;
  cursor: pointer;
}

.history button.on {
  border-color: #cfc4ff;
  background: #f7f4ff;
  color: var(--xy-violet);
}

.act-add {
  margin-left: auto;
  flex: 0 0 auto;
}

.act-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.act-list li {
  padding: 9px 10px;
  border: 1px solid var(--xy-line);
  border-radius: 10px;
  background: #fbfbfe;
}

.act-head {
  display: flex;
  align-items: center;
  gap: 6px;
}

.act-head b {
  min-width: 0;
  flex: 1;
  font-size: 11.5px;
}

.act-status {
  flex: 0 0 auto;
  padding: 1px 6px;
  border-radius: 5px;
  font-size: 9px;
}

.act-status.planned {
  background: #fff7e8;
  color: #a97a2c;
}

.act-status.done {
  background: var(--xy-mint-soft);
  color: var(--xy-mint);
}

.act-status.skipped {
  background: #f2f2f6;
  color: #8b8f9e;
}

.act-date {
  color: var(--xy-muted);
  font-size: 9px;
}

.act-detail {
  margin-top: 4px;
  color: #6a6e80;
  font-size: 10.5px;
  line-height: 1.6;
}

.act-tools {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  margin-top: 6px;
}

.result-select {
  width: 96px;
}

.note-input {
  min-width: 120px;
  flex: 1;
}

.review {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 10px 12px;
  border: 1px solid var(--xy-line);
  border-left: 3px solid var(--xy-violet);
  border-radius: 10px;
  background: #f9f8ff;
}

.review.continue {
  border-left-color: var(--xy-mint);
}

.review.slow_down,
.review.pause {
  border-left-color: #e0a04a;
}

.review.adjust {
  border-left-color: #cf8a5c;
}

.review-top {
  display: flex;
  align-items: baseline;
  gap: 8px;
}

.review-top b {
  font-size: 12px;
}

.review-top span {
  color: var(--xy-muted);
  font-size: 9.5px;
}

.review p {
  color: #4d5060;
  font-size: 11px;
  line-height: 1.7;
}

.review-next {
  color: #3f4254 !important;
  font-weight: 600;
}

.review .el-button {
  align-self: flex-start;
}

.advice-empty {
  color: var(--xy-muted);
  font-size: 11.5px;
  line-height: 1.75;
}
</style>

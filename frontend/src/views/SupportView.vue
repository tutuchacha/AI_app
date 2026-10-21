<script setup lang="ts">
import { ref } from 'vue'
import { Collection, Service } from '@element-plus/icons-vue'
import LearningNote from '@/components/LearningNote.vue'
import ChatPanel from '@/components/chat/ChatPanel.vue'

const panelRef = ref<InstanceType<typeof ChatPanel>>()

const faqs = [
  '退款一般多久到账？',
  '订单已发货还能退吗？',
  '超过 7 天怎么处理？',
]

const suggestions = [
  '退款一般多久到账？',
  '订单已发货，我不想要了怎么办？',
  '怎么修改收货地址？',
]

function ask(text: string) {
  panelRef.value?.send(text)
}
</script>

<template>
  <div class="support-page">
    <LearningNote
      title="客服 = RAG + 引用 + 兜底"
      :points="['先检索知识库，再生成回答', '每条答案带 citation', '答不出来时转人工']"
      next="M4：接入知识库绑定、引用溯源和转人工工单"
    />

    <div class="page-head">
      <div>
        <h1 class="page-title">智能客服</h1>
        <p class="page-subtitle">绑定知识库后回答，并展示引用来源。当前使用 Mock 售后文档。</p>
      </div>
      <span class="pill mint"><el-icon><Service /></el-icon> M4 规划</span>
    </div>

    <div class="support-layout">
      <aside class="side">
        <div class="kb card">
          <div class="kb-top">
            <div class="kb-icon"><el-icon :size="18"><Collection /></el-icon></div>
            <div>
              <strong>售后手册 v1</strong>
              <span>已绑定 · 12 个文档</span>
            </div>
          </div>
          <div class="kb-stats">
            <div><b>36</b><span>切片</span></div>
            <div><b>0.82</b><span>平均命中</span></div>
            <div><b>Mock</b><span>数据源</span></div>
          </div>
        </div>

        <div class="faq card">
          <h3>常见问题</h3>
          <button v-for="item in faqs" :key="item" @click="ask(item)">
            {{ item }}
            <span>→</span>
          </button>
        </div>

        <div class="flow card">
          <h3>回答流程</h3>
          <ol>
            <li><i>1</i> 检索知识库 TopK</li>
            <li><i>2</i> 组装上下文与约束</li>
            <li><i>3</i> 流式生成 + 引用</li>
            <li><i>4</i> 低置信度转人工</li>
          </ol>
        </div>
      </aside>

      <ChatPanel
        ref="panelRef"
        class="chat"
        mode="support"
        title="星屿客服"
        subtitle="售后手册 v1 · 回答会附带引用来源"
        greeting="你好，我是星屿客服。可以问我退款、物流、售后相关的问题，我会先查知识库再回答。"
        :suggestions="suggestions"
      />
    </div>
  </div>
</template>

<style scoped>
.support-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
  height: calc(100vh - 60px - 54px);
  min-height: 560px;
}

.support-layout {
  display: flex;
  flex: 1;
  gap: 16px;
  min-height: 0;
}

.side {
  display: flex;
  flex-direction: column;
  gap: 10px;
  width: 290px;
  flex: 0 0 290px;
  overflow-y: auto;
}

.kb {
  padding: 14px;
}

.kb-top {
  display: flex;
  align-items: center;
  gap: 10px;
}

.kb-icon {
  display: grid;
  place-items: center;
  width: 38px;
  height: 38px;
  border-radius: 11px;
  background: var(--xy-mint-soft);
  color: var(--xy-mint);
}

.kb-top strong {
  display: block;
  font-size: 13px;
}

.kb-top span {
  color: var(--xy-muted);
  font-size: 10.5px;
}

.kb-stats {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 6px;
  margin-top: 12px;
  padding-top: 10px;
  border-top: 1px dashed var(--xy-line);
}

.kb-stats div {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.kb-stats b {
  font-size: 13px;
}

.kb-stats span {
  color: var(--xy-muted);
  font-size: 9.5px;
}

.faq,
.flow {
  padding: 13px 14px;
}

.faq h3,
.flow h3 {
  margin-bottom: 8px;
  font-size: 12px;
  font-weight: 700;
}

.faq button {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  padding: 7px 0;
  border: 0;
  border-bottom: 1px dashed #f0f1f6;
  background: transparent;
  color: #63677d;
  font-size: 11.5px;
  text-align: left;
  cursor: pointer;
}

.faq button:last-child {
  border-bottom: 0;
}

.faq button:hover {
  color: var(--xy-coral);
}

.faq button span {
  color: #c3c6d4;
}

.flow ol {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin: 0;
  padding: 0;
  list-style: none;
  color: #6f7387;
  font-size: 11px;
}

.flow li {
  display: flex;
  align-items: center;
  gap: 8px;
}

.flow i {
  display: grid;
  place-items: center;
  width: 17px;
  height: 17px;
  border-radius: 50%;
  background: var(--xy-violet-soft);
  color: var(--xy-violet);
  font-size: 9px;
  font-style: normal;
  font-weight: 700;
}

.chat {
  flex: 1;
  min-width: 0;
}

@media (max-width: 980px) {
  .support-page {
    height: auto;
  }
  .support-layout {
    flex-direction: column;
  }
  .side {
    width: 100%;
    flex-basis: auto;
    flex-direction: row;
    overflow-x: auto;
  }
  .side > * {
    min-width: 240px;
  }
  .kb {
    min-width: 260px;
  }
  .chat {
    height: 620px;
  }
}
</style>

import type { ChatHandlers, ChatRequest, ChatTransport, Citation } from '@/types/chat'

function abortError() {
  return new DOMException('The operation was aborted.', 'AbortError')
}

function wait(ms: number, signal: AbortSignal): Promise<void> {
  return new Promise((resolve, reject) => {
    if (signal.aborted) {
      reject(abortError())
      return
    }
    const timer = window.setTimeout(() => {
      signal.removeEventListener('abort', onAbort)
      resolve()
    }, ms)
    function onAbort() {
      window.clearTimeout(timer)
      reject(abortError())
    }
    signal.addEventListener('abort', onAbort, { once: true })
  })
}

function chunkText(text: string): string[] {
  const chunks: string[] = []
  for (let index = 0; index < text.length; index += 4) {
    chunks.push(text.slice(index, index + 4))
  }
  return chunks
}

interface MockReply {
  text: string
  citations: Citation[]
}

function buildReply(request: ChatRequest): MockReply {
  const quote = request.content.trim().slice(0, 28)

  if (request.mode === 'support') {
    return {
      text: `关于「${quote}」，我在知识库里找到了这些信息：\n\n· 退货申请提交后，通常 24 小时内完成审核，退款 1–3 个工作日到账。\n· 订单已发货时，建议先拒收，或签收后再发起退货。\n· 超过 7 天的订单需要人工客服协助处理。\n\n如果这些还没有解决你的问题，M4 版本会提供“转人工”入口。当前是 Mock 数据，只用于界面预览。`,
      citations: [
        {
          id: 'doc-refund',
          title: '售后手册 v1 · 退款时效',
          snippet: '审核 24 小时内完成，退款原路返回，通常 1–3 个工作日到账。',
          score: 0.91,
        },
        {
          id: 'doc-logistics',
          title: '物流 FAQ · 已发货订单',
          snippet: '已发货订单可拒收，或签收后 7 天内申请无理由退货。',
          score: 0.84,
        },
      ],
    }
  }

  if (request.mode === 'companion') {
    return {
      text: `听到你说「${quote}」，我先不急着给建议。\n\n可以试着回答我三个问题：\n· 这件事里，你最在意的部分是什么？\n· 对方当时的反应，和你期待的有哪里不一样？\n· 如果只做一个很小的改变，你愿意先试哪一个？\n\n这是 M0 的 Mock 回复，用来验证角色化的界面和流式输出。M4 会接入角色卡、长期记忆和情感知识库。`,
      citations: [],
    }
  }

  if (request.kbId) {
    return {
      text: `我在你选择的知识库里找到了这段资料，先按它回答「${quote}」：\n\n· 入职满一年享有 5 天带薪年假，满三年 10 天，满五年 15 天。\n· 年假需提前三个工作日在系统提交申请，由直属主管审批。\n\n这是 Mock 通道的示例引用，用来验证「选库 → citation 卡片 → 回答」这条链路；接入真实后端后，片段和分数都来自检索结果。`,
      citations: [
        {
          id: 'chunk-demo',
          title: '员工手册.txt',
          snippet: '员工年假规定：入职满一年享有 5 天带薪年假，满三年 10 天，满五年 15 天。',
          score: 0.78,
        },
      ],
    }
  }

  return {
    text: `我把「${quote}」拆成三步来看：\n\n1. 先确认目标：你希望得到一个答案，还是一个可以执行的下一步？\n2. 再补上下文：现在有哪些已知条件，哪些只是猜测？\n3. 最后做小实验：选一个成本最低的动作，验证完再调整。\n\n这是 M0 的 Mock 回复，重点不是答案本身，而是演示 meta → delta → done 的流式事件。M1 接入 Spring AI 后，由后端产生同样的事件流，这个页面不需要改。`,
    citations: [],
  }
}

export class MockChatTransport implements ChatTransport {
  readonly name = 'mock'

  async send(request: ChatRequest, handlers: ChatHandlers, signal: AbortSignal): Promise<void> {
    const startedAt = Date.now()
    const reply = buildReply(request)

    handlers.onMeta?.({
      messageId: `mock-${startedAt}`,
      conversationId: request.conversationId ?? '',
      model: 'mock-stream',
    })

    await wait(220, signal)

    for (const citation of reply.citations) {
      handlers.onCitation?.(citation)
      await wait(40, signal)
    }

    for (const chunk of chunkText(reply.text)) {
      handlers.onDelta(chunk)
      await wait(18, signal)
    }

    handlers.onUsage?.({
      promptTokens: 128,
      completionTokens: Math.ceil(reply.text.length / 1.6),
    })

    handlers.onDone?.({
      finishReason: 'stop',
      latencyMs: Date.now() - startedAt,
    })
  }
}

export const mockTransport = new MockChatTransport()

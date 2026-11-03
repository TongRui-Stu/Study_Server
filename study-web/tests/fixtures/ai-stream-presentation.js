// Dev-only browser fixture: real views/API parser, isolated data and fake SSE.
// Open /tests/fixtures/ai-stream-presentation.html with Vite. No provider or DB calls.
import { createApp, h, ref } from 'vue'
import { createRouter, createMemoryHistory, RouterView } from 'vue-router'
import request from '../../src/utils/request'
import HomeView from '../../src/views/home/HomeView.vue'
import PracticeView from '../../src/views/practice/PracticeView.vue'
import StatisticsView from '../../src/views/statistics/StatisticsView.vue'
import '../../src/style.css'
import 'element-plus/theme-chalk/dark/css-vars.css'

const mode = ref('normal')
const requests = ref(0)
const text = '# 流式回归示例\n\n' + ('这是平滑输出与长回答滚动验证，包含 **重点** 和公式 $2^6=64$。😀\n\n').repeat(24)
  + '```js\nconst safe = "<script>alert(1)</script>"\n\nconsole.log(safe)\n```\n\n'
  + '| 指标 | 数值 |\n| --- | --- |\n| 正确率 | 80% |\n\n'
  + '<img src=x onerror="alert(1)">\n\n[危险链接](javascript:alert(1))\n\n结束标记：完整回复。'

request.defaults.adapter = async config => {
  let data
  if (config.url === '/subjects') data = [{ id: 1, name: '隔离测试科目', description: '不访问真实数据库' }]
  else if (config.url === '/practice/questions') data = {
    source: 'KNOWLEDGE_POINT', knowledgePointName: '隔离测试知识点',
    questions: [1, 2].map(id => ({ id, content: `测试题 ${id}：2 的平方是多少？`, type: 'SINGLE', difficulty: 1,
      options: [{ optionKey: 'A', content: '4' }, { optionKey: 'B', content: '8' }] }))
  }
  else if (config.url === '/practice/submit') data = { answerRecordId: 1, isCorrect: true, correctAnswer: 'A', userAnswer: 'A', analysis: '测试解析', favorited: false }
  else if (config.url === '/statistics/overview') data = {
    summary: { totalAnswerCount: 100, correctAnswerCount: 80, incorrectAnswerCount: 20, accuracyRate: 80, answeredQuestionCount: 30, wrongQuestionCount: 8, favoriteCount: 2, studyDays: 7 },
    subjectStatistics: [], knowledgePointStatistics: [], weakKnowledgePoints: [], dailyTrend: []
  }
  else throw new Error(`Fixture blocked unexpected request: ${config.url}`)
  return { data: { code: 200, data }, status: 200, statusText: 'OK', headers: {}, config }
}

window.fetch = async (url, { signal } = {}) => {
  if (!/^\/api\/ai\/(chat|question-chat|learning-analysis)\/stream$/.test(url)) throw new Error(`Fixture blocked fetch: ${url}`)
  requests.value++
  const selectedMode = mode.value
  const encoder = new TextEncoder()
  const requestId = `fixture-${requests.value}`
  let timer
  let stopped = false
  const body = new ReadableStream({
    start(controller) {
      const emit = (type, payload = {}) => controller.enqueue(encoder.encode(`event:${type}\ndata:${JSON.stringify({ requestId, ...payload })}\n\n`))
      const abort = () => {
        if (stopped) return
        stopped = true
        clearTimeout(timer)
        controller.error(new DOMException('Aborted', 'AbortError'))
      }
      signal?.addEventListener('abort', abort, { once: true })
      if (signal?.aborted) return abort()
      emit('start')
      emit('source', { provider: 'qwen', model: 'qwen3.8-flash' })
      let cursor = 0
      const next = () => {
        if (stopped) return
        if (selectedMode === 'empty') { timer = setTimeout(next, 1000); return }
        // Uneven upstream packets exercise the display queue, not a fake typewriter.
        const size = [90, 7, 140, 30][Math.floor(cursor / 30) % 4]
        emit('delta', { delta: text.slice(cursor, cursor + size) })
        cursor += size
        if (selectedMode === 'error' && cursor > 200) {
          emit('error', { message: '夹具模拟断流，已有内容已保留' })
          stopped = true
          controller.close()
        } else if (cursor >= text.length) {
          emit('done')
          stopped = true
          controller.close()
        } else timer = setTimeout(next, selectedMode === 'slow' ? 600 : 90)
      }
      timer = setTimeout(next, 200)
    },
    cancel() { stopped = true; clearTimeout(timer) }
  })
  return new Response(body, { status: 200, headers: { 'Content-Type': 'text/event-stream' } })
}

const router = createRouter({ history: createMemoryHistory(), routes: [
  { path: '/', component: HomeView },
  { path: '/practice/:knowledgePointId', component: PracticeView },
  { path: '/statistics', component: StatisticsView }
] })
await router.push('/')
const Root = { setup: () => () => h('div', [
  h('nav', { style: 'position:sticky;top:0;z-index:100;background:#fff;padding:12px;display:flex;gap:12px;border-bottom:1px solid #888' }, [
    h('strong', '隔离回归夹具（不调用真实模型）'),
    ...[['首页', '/'], ['题目辅导', '/practice/1'], ['学习诊断', '/statistics']].map(([label, path]) => h('button', { onClick: () => router.push(path) }, label)),
    h('select', { 'aria-label': '模拟响应模式', value: mode.value, onChange: event => { mode.value = event.target.value } },
      [['normal', '完整长回答'], ['slow', '慢速长回答（滚动验证）'], ['empty', '首段前等待'], ['error', '部分回复后断流']].map(([value, label]) => h('option', { value }, label))),
    h('span', `请求数 ${requests.value}`)
  ]), h(RouterView)
]) }
createApp(Root).use(router).mount('#app')

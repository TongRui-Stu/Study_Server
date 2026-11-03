import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import { dirname, resolve } from 'node:path'
import { consumeSseResponse, createSseParser } from '../src/utils/sse.js'
import { createAiStreamSession, finalizeInterruptedAiTurn } from '../src/utils/aiStreamSession.js'

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..')

test('SSE parser handles protocol fields split across network chunks', () => {
  const events = []
  const parser = createSseParser(event => events.push(event))
  for (const chunk of [
    'event: sta',
    'rt\r\ndata: {"requestId":"req-1"}\r',
    '\n\r\nevent: delta\ndata: {"requestId":"req-1","delta":"你"}\n\n',
    'event: done\ndata: {"requestId":"req-1"}\n\n'
  ]) parser.feed(chunk)
  parser.finish()

  assert.deepEqual(events, [
    { type: 'start', requestId: 'req-1' },
    { type: 'delta', requestId: 'req-1', delta: '你' },
    { type: 'done', requestId: 'req-1' }
  ])
})

test('stream response preserves source and concatenates buffered deltas at done', async () => {
  const events = []
  const response = responseFromChunks([
    'event:start\ndata:{"requestId":"req-2"}\n\n',
    'event:source\ndata:{"requestId":"req-2","provider":"qwen","model":"qwen3.8-flash"}\n\n',
    'event:delta\ndata:{"requestId":"req-2","delta":"第一段"}\n\n',
    'event:delta\ndata:{"requestId":"req-2","delta":"第二段"}\n\n',
    'event:done\ndata:{"requestId":"req-2"}\n\n'
  ])

  const terminal = await consumeSseResponse(response, event => events.push(event))

  assert.equal(terminal, 'done')
  assert.equal(events.filter(event => event.type === 'delta').map(event => event.delta).join(''), '第一段第二段')
  assert.deepEqual(events.find(event => event.type === 'source'), {
    type: 'source', requestId: 'req-2', provider: 'qwen', model: 'qwen3.8-flash'
  })
})

test('stream response accepts error as terminal and rejects interrupted response', async () => {
  const errorEvents = []
  const terminal = await consumeSseResponse(responseFromChunks([
    'event:start\ndata:{"requestId":"req-3"}\n\n',
    'event:error\ndata:{"requestId":"req-3","message":"生成中断，请重新生成"}\n\n'
  ]), event => errorEvents.push(event))
  assert.equal(terminal, 'error')
  assert.equal(errorEvents.at(-1).message, '生成中断，请重新生成')

  await assert.rejects(
    consumeSseResponse(responseFromChunks([
      'event:start\ndata:{"requestId":"req-4"}\n\n',
      'event:delta\ndata:{"requestId":"req-4","delta":"未结束"}\n\n'
    ]), () => {}),
    /意外中断/
  )
})

test('session ignores stale request ids, batches deltas and records final source', () => {
  const scheduled = []
  const output = { content: '', provider: '', model: '', done: false }
  const stream = createAiStreamSession({
    schedule: callback => {
      scheduled.push(callback)
      return scheduled.length
    },
    cancelSchedule: () => {},
    reduceMotion: true
  })
  const stale = stream.begin()
  const current = stream.begin({
    onSource: event => Object.assign(output, { provider: event.provider, model: event.model }),
    onDelta: delta => { output.content += delta },
    onDone: () => { output.done = true }
  })
  assert.equal(stale.signal.aborted, true)

  stale.handleEvent({ type: 'start', requestId: 'old' })
  stale.handleEvent({ type: 'delta', requestId: 'old', delta: '污染' })
  current.handleEvent({ type: 'start', requestId: 'new' })
  current.handleEvent({ type: 'source', requestId: 'old', provider: 'deepseek', model: 'wrong' })
  current.handleEvent({ type: 'source', requestId: 'new', provider: 'qwen', model: 'qwen3.8-flash' })
  current.handleEvent({ type: 'delta', requestId: 'new', delta: '连续' })
  current.handleEvent({ type: 'delta', requestId: 'new', delta: '内容' })
  assert.equal(output.content, '')
  current.handleEvent({ type: 'done', requestId: 'new' })

  assert.deepEqual(output, {
    content: '连续内容', provider: 'qwen', model: 'qwen3.8-flash', done: true
  })
})

test('session cancellation aborts the current fetch signal', () => {
  let reason = ''
  const stream = createAiStreamSession()
  const session = stream.begin({ onCancel: value => { reason = value } })

  session.cancel('user')

  assert.equal(session.signal.aborted, true)
  assert.equal(reason, 'user')
  assert.equal(stream.hasActive(), false)
})

test('interrupted turn removes an unanswered question but keeps partial output', () => {
  const unanswered = [
    { role: 'assistant', content: '欢迎' },
    { role: 'user', content: '第一个问题' },
    { role: 'assistant', content: '', streaming: true }
  ]
  assert.equal(finalizeInterruptedAiTurn(unanswered, 2), false)
  assert.deepEqual(unanswered, [{ role: 'assistant', content: '欢迎' }])

  const partial = [
    { role: 'user', content: '第二个问题' },
    { role: 'assistant', content: '已经生成的内容', streaming: true }
  ]
  assert.equal(finalizeInterruptedAiTurn(partial, 1), true)
  assert.deepEqual(partial, [
    { role: 'user', content: '第二个问题' },
    { role: 'assistant', content: '已经生成的内容', streaming: false }
  ])
})

test('all three AI pages use streaming APIs, source labels and generation guards', async () => {
  const pages = [
    ['src/views/home/HomeView.vue', 'streamAiChat'],
    ['src/views/practice/PracticeView.vue', 'streamQuestionAiChat'],
    ['src/views/statistics/StatisticsView.vue', 'streamLearningAnalysisMessage']
  ]
  for (const [file, apiName] of pages) {
    const source = await readFile(resolve(root, file), 'utf8')
    assert.match(source, new RegExp(apiName))
    assert.match(source, /AiMessageSource/)
    assert.match(source, /streaming: true/)
    assert.match(source, /finalizeInterruptedAiTurn/)
    assert.match(source, /取消生成/)
  }
  const statistics = await readFile(resolve(root, 'src/views/statistics/StatisticsView.vue'), 'utf8')
  assert.match(statistics, /!canExportAnalysisConversation \|\| analysisLoading/)
})

function responseFromChunks(chunks) {
  const encoder = new TextEncoder()
  return new Response(new ReadableStream({
    start(controller) {
      chunks.forEach(chunk => controller.enqueue(encoder.encode(chunk)))
      controller.close()
    }
  }), { status: 200, headers: { 'Content-Type': 'text/event-stream' } })
}

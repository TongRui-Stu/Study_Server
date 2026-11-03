import test from 'node:test'
import assert from 'node:assert/strict'
import { createAiStreamSession } from '../src/utils/aiStreamSession.js'
import { createStreamingMarkdownBlocks } from '../src/utils/streamingMarkdown.js'
import { createAiAutoScroll } from '../src/utils/aiAutoScroll.js'

function clock() {
  let time = 0
  let id = 0
  const tasks = new Map()
  const add = (callback, delay) => { tasks.set(++id, { callback, at: time + delay }); return id }
  return {
    options: {
      now: () => time,
      schedule: callback => add(callback, 16), cancelSchedule: handle => tasks.delete(handle),
      setTimer: add, clearTimer: handle => tasks.delete(handle), reduceMotion: false
    },
    advance(ms) {
      const end = time + ms
      while (true) {
        const next = [...tasks].sort((a, b) => a[1].at - b[1].at).find(([, task]) => task.at <= end)
        if (!next) break
        const [handle, task] = next
        tasks.delete(handle)
        time = task.at
        task.callback()
      }
      time = end
    },
    get size() { return tasks.size }
  }
}

function begin(clock, handlers = {}) {
  const stream = createAiStreamSession(clock.options)
  const session = stream.begin(handlers)
  session.handleEvent({ type: 'start', requestId: 'r' })
  return { stream, session, emit: (type, extra) => session.handleEvent({ type, requestId: 'r', ...extra }) }
}

test('bursty SSE text is spread across frames with exact Unicode and bounded completion', () => {
  const timer = clock()
  const pieces = []
  let done = 0
  const { stream, emit } = begin(timer, { onDelta: text => pieces.push(text), onDone: () => done++ })
  const text = '中文😀e\u0301'.repeat(60)
  emit('delta', { delta: text })
  timer.advance(16)
  assert.ok(pieces[0].length > 0 && pieces[0].length < text.length)
  emit('done')
  assert.equal(stream.hasActive(), true)
  timer.advance(200)
  assert.equal(pieces.join(''), text)
  assert.ok(pieces.length > 5)
  assert.ok(pieces.every(piece => !/[\uD800-\uDBFF]$/.test(piece) && !/^[\uDC00-\uDFFF]/.test(piece)))
  assert.equal(done, 1)
  assert.equal(timer.size, 0)
  assert.equal(stream.hasActive(), false)
})

test('completion releases loading even when background frames never run', () => {
  let finish
  let output = ''
  let done = false
  const stream = createAiStreamSession({ schedule: () => 1, cancelSchedule: () => {}, setTimer: callback => { finish = callback; return 2 }, clearTimer: () => {}, reduceMotion: false })
  const session = stream.begin({ onDelta: text => { output += text }, onDone: () => { done = true } })
  for (const event of [{ type: 'start' }, { type: 'delta', delta: '后台回复' }, { type: 'done' }]) session.handleEvent({ requestId: 'r', ...event })
  finish()
  assert.equal(output, '后台回复')
  assert.equal(done, true)
})

test('reading pace softens a burst without delaying the first frame or losing the ending', () => {
  const timer = clock()
  let output = ''
  let done = false
  const { emit } = begin(timer, { onDelta: text => { output += text }, onDone: () => { done = true } })
  const text = '读'.repeat(1000)
  emit('delta', { delta: text })
  timer.advance(16)
  assert.ok(output.length > 0, 'first frame should still show text')
  timer.advance(64)
  // A fast burst used to reveal over half its text in 80ms; now about 43%.
  assert.ok(output.length >= 400 && output.length <= 480, `revealed ${output.length}`)
  emit('done')
  timer.advance(128)
  assert.equal(done, false, 'do not rush the last pending text into the old completion window')
  timer.advance(112)
  assert.equal(done, true)
  assert.equal(output, text)
  assert.equal(timer.size, 0)
})

test('stop after transport done retains pending text and cancels delayed completion', () => {
  const timer = clock()
  let output = ''
  let done = 0
  let cancelled = ''
  const { session, emit } = begin(timer, { onDelta: text => { output += text }, onDone: () => done++, onCancel: reason => { cancelled = reason } })
  emit('delta', { delta: '已收到但还在显示队列中的文字' })
  emit('done')
  assert.equal(session.isCurrent(), true)
  session.cancel('user')
  timer.advance(1000)
  assert.equal(output, '已收到但还在显示队列中的文字')
  assert.equal(cancelled, 'user')
  assert.equal(done, 0)
  assert.equal(timer.size, 0)
})

test('replacement and context change discard stale queued frames and terminal callbacks', () => {
  const timer = clock()
  let output = ''
  let done = 0
  const { stream, emit } = begin(timer, { onDelta: text => { output += text }, onDone: () => done++ })
  emit('delta', { delta: '旧题的残留' })
  emit('done')
  stream.begin()
  timer.advance(1000)
  assert.equal(output, '')
  assert.equal(done, 0)
  stream.cancel('question-changed')
  assert.equal(timer.size, 0)
})

test('error flushes received text once; duplicate terminal and stale deltas are ignored', () => {
  const timer = clock()
  let output = ''
  let errors = 0
  const { emit } = begin(timer, { onDelta: text => { output += text }, onError: () => errors++ })
  emit('delta', { delta: '保留已有回复' })
  emit('error', { message: '断流' })
  emit('done')
  emit('delta', { delta: '污染' })
  timer.advance(1000)
  assert.equal(output, '保留已有回复')
  assert.equal(errors, 1)
  assert.equal(timer.size, 0)
})

test('reduced motion skips artificial pacing and keeps final content exact', () => {
  const timer = clock()
  timer.options.reduceMotion = true
  let output = ''
  let done = false
  const { emit } = begin(timer, { onDelta: text => { output += text }, onDone: () => { done = true } })
  emit('delta', { delta: '完整正文' })
  emit('done')
  assert.equal(output, '完整正文')
  assert.equal(done, true)
  assert.equal(timer.size, 0)
})

test('streaming Markdown renders completed blocks once instead of every character', () => {
  const calls = []
  const parser = createStreamingMarkdownBlocks(text => { calls.push(text); return `rendered:${text}` })
  const text = '# 标题\n\n第一段正文。\n\n' + '较长的未完成段落'.repeat(200)
  let state
  for (let i = 1; i <= text.length; i++) state = parser.update(text.slice(0, i))
  assert.equal(calls.length, 2)
  assert.equal(state.blocks.map(block => block.source).join('') + state.tail, text)
  assert.equal(state.blocks[0].html, 'rendered:# 标题\n\n')
  state = parser.update('重新生成\n\n')
  assert.equal(state.blocks.length, 1)
  assert.equal(state.blocks[0].source, '重新生成\n\n')
})

test('blank lines inside code and display formulas are not premature render boundaries', () => {
  for (const text of [
    '```js\nconst x = 1\n\nconst y = 2\n```\n\n尾段',
    '~~~~\n```\n\n文字\n~~~~\n\n尾段',
    '$$\nx^2\n\n+ y^2\n$$\n\n尾段',
    '\\[\nx^2\n\n+ y^2\n\\]\n\n尾段'
  ]) {
    const parser = createStreamingMarkdownBlocks(value => value)
    let state
    for (let i = 1; i <= text.length; i++) state = parser.update(text.slice(0, i))
    assert.equal(state.blocks.length, 1, text)
    assert.equal(state.tail, '尾段')
    assert.equal(state.blocks[0].source + state.tail, text)
  }
})

test('auto scroll coalesces DOM reads and respects a reader who scrolls away', async () => {
  const timer = clock()
  const element = { scrollTop: 400, clientHeight: 100, scrollHeight: 500 }
  const scroller = createAiAutoScroll(() => element, timer.options)
  scroller.request()
  scroller.request()
  await Promise.resolve()
  assert.equal(timer.size, 1)
  element.scrollHeight = 600
  timer.advance(16)
  assert.equal(element.scrollTop, 600)
  element.scrollTop = 100
  scroller.onScroll()
  await scroller.request()
  timer.advance(16)
  assert.equal(element.scrollTop, 100)
  element.scrollTop = 500
  scroller.onScroll()
  await scroller.request()
  element.scrollHeight = 650
  timer.advance(16)
  assert.equal(element.scrollTop, 650)
})

test('queued scroll rechecks intent and disposal cancels pending work', async () => {
  const timer = clock()
  const element = { scrollTop: 400, clientHeight: 100, scrollHeight: 500 }
  const scroller = createAiAutoScroll(() => element, timer.options)
  await scroller.request()
  element.scrollTop = 0
  scroller.onScroll()
  timer.advance(16)
  assert.equal(element.scrollTop, 0)
  await scroller.request(true)
  scroller.dispose()
  timer.advance(16)
  assert.equal(element.scrollTop, 0)
  const second = createAiAutoScroll(() => element, timer.options)
  const pending = second.request(true)
  second.dispose()
  await pending
  assert.equal(timer.size, 0)
})

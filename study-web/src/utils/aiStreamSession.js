const defaultSchedule = callback => typeof requestAnimationFrame === 'function'
  ? requestAnimationFrame(callback) : setTimeout(callback, 16)
const defaultCancelSchedule = handle => typeof cancelAnimationFrame === 'function'
  ? cancelAnimationFrame(handle) : clearTimeout(handle)
// Shared reading pace for all three entry points, measured in UTF-16 units.
const BASE_UNITS_PER_SECOND = 75
const CATCH_UP_MS = 150
const FINISH_FALLBACK_MS = 240

export function createAiStreamSession(options = {}) {
  const schedule = options.schedule || defaultSchedule
  const cancelSchedule = options.cancelSchedule || defaultCancelSchedule
  const now = options.now || (() => performance.now())
  const setTimer = options.setTimer || setTimeout
  const clearTimer = options.clearTimer || clearTimeout
  const reduceMotion = options.reduceMotion ?? globalThis.matchMedia?.('(prefers-reduced-motion: reduce)').matches ?? false
  let generation = 0
  let active = null

  function cancel(reason = 'cancelled') {
    if (!active) return false
    const session = active
    if (session.flushHandle !== null) cancelSchedule(session.flushHandle)
    if (session.finishTimer !== null) clearTimer(session.finishTimer)
    // Keep already received text on a user stop or a broken connection.
    // A replaced/unmounted context must never receive another update.
    if ((reason === 'user' || reason === 'failed') && session.buffer) {
      session.handlers.onDelta?.(session.buffer)
    }
    session.buffer = ''
    active = null
    session.controller.abort(reason)
    session.handlers.onCancel?.(reason)
    return true
  }

  function begin(handlers = {}) {
    cancel('replaced')
    const session = {
      generation: ++generation,
      controller: new AbortController(),
      requestId: '',
      buffer: '',
      flushHandle: null,
      finishTimer: null,
      terminal: null,
      lastFrame: now(),
      credit: 0,
      handlers
    }
    active = session

    function isCurrent() {
      return active === session
    }

    function settle() {
      if (!isCurrent() || !session.terminal) return
      if (session.finishTimer !== null) clearTimer(session.finishTimer)
      const { type, event } = session.terminal
      active = null
      if (type === 'done') handlers.onDone?.(event)
      else handlers.onError?.(event.message || '生成中断，请重新生成', event)
    }

    function flush(all = false) {
      session.flushHandle = null
      if (!isCurrent()) return
      const timestamp = now()
      const elapsed = Math.min(50, Math.max(0, timestamp - session.lastFrame))
      session.lastFrame = timestamp
      // Adapt to bursts without leaving a long artificial typing backlog.
      const catchUpWindow = session.terminal ? Math.max(16, session.terminal.deadline - timestamp) : CATCH_UP_MS
      session.credit += elapsed * Math.max(BASE_UNITS_PER_SECOND / 1000, session.buffer.length / catchUpWindow)
      let count = all || reduceMotion || (session.terminal && timestamp >= session.terminal.deadline)
        ? session.buffer.length : Math.floor(session.credit)
      count = Math.min(count, session.buffer.length)
      // Never split a UTF-16 surrogate pair between visual frames.
      if (count > 0 && count < session.buffer.length && /[\uD800-\uDBFF]/.test(session.buffer[count - 1])) count++
      if (count > 0) {
        const content = session.buffer.slice(0, count)
        session.buffer = session.buffer.slice(count)
        session.credit = Math.max(0, session.credit - count)
        handlers.onDelta?.(content)
      }
      if (!isCurrent()) return
      if (session.buffer) queueFlush()
      else if (session.terminal) settle()
    }

    function queueFlush() {
      if (session.flushHandle === null) session.flushHandle = schedule(() => flush())
    }

    function finish(type, event) {
      session.terminal = { type, event, deadline: now() + CATCH_UP_MS }
      if (type === 'error' || reduceMotion || !session.buffer) {
        if (session.flushHandle !== null) cancelSchedule(session.flushHandle)
        flush(true)
      } else {
        // rAF pauses in background tabs. Completion must still release loading.
        session.finishTimer = setTimer(() => {
          if (session.flushHandle !== null) cancelSchedule(session.flushHandle)
          flush(true)
        }, FINISH_FALLBACK_MS)
        queueFlush()
      }
    }

    return {
      signal: session.controller.signal,
      isCurrent,
      handleEvent(event) {
        if (!isCurrent() || session.terminal || !event || typeof event !== 'object') return
        if (event.type === 'start') {
          if (!session.requestId && event.requestId) {
            session.requestId = event.requestId
            handlers.onStart?.(event)
          }
          return
        }
        if (!session.requestId || event.requestId !== session.requestId) return
        if (event.type === 'source') handlers.onSource?.(event)
        if (event.type === 'delta' && typeof event.delta === 'string' && event.delta) {
          if (!session.buffer) {
            session.lastFrame = now()
            session.credit = 0
          }
          session.buffer += event.delta
          queueFlush()
        }
        if (event.type === 'done' || event.type === 'error') finish(event.type, event)
      },
      cancel(reason) {
        if (isCurrent()) cancel(reason)
      }
    }
  }

  return { begin, cancel, hasActive: () => Boolean(active) }
}

export function isAbortError(error) {
  return error?.name === 'AbortError'
}

export function finalizeInterruptedAiTurn(messages, assistantIndex) {
  if (!Array.isArray(messages)) return false
  const assistant = messages[assistantIndex]
  if (!assistant || assistant.role !== 'assistant') return false

  if (typeof assistant.content === 'string' && assistant.content.trim()) {
    messages[assistantIndex] = { ...assistant, streaming: false }
    return true
  }

  const userIndex = assistantIndex - 1
  if (messages[userIndex]?.role === 'user') {
    messages.splice(userIndex, 2)
  } else {
    messages.splice(assistantIndex, 1)
  }
  return false
}

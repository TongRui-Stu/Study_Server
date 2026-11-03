export function createSseParser(onEvent) {
  let buffer = ''
  let eventName = ''
  let dataLines = []

  function dispatch() {
    if (!eventName && dataLines.length === 0) return
    const dataText = dataLines.join('\n')
    const type = eventName || 'message'
    eventName = ''
    dataLines = []
    if (!dataText) return
    let data
    try {
      data = JSON.parse(dataText)
    } catch {
      throw new Error('流式响应格式异常')
    }
    onEvent({ type, ...data })
  }

  function processLine(rawLine) {
    const line = rawLine.endsWith('\r') ? rawLine.slice(0, -1) : rawLine
    if (line === '') {
      dispatch()
      return
    }
    if (line.startsWith(':')) return
    const colon = line.indexOf(':')
    const field = colon < 0 ? line : line.slice(0, colon)
    let value = colon < 0 ? '' : line.slice(colon + 1)
    if (value.startsWith(' ')) value = value.slice(1)
    if (field === 'event') eventName = value
    if (field === 'data') dataLines.push(value)
  }

  return {
    feed(chunk) {
      buffer += chunk
      let newlineIndex
      while ((newlineIndex = buffer.indexOf('\n')) >= 0) {
        processLine(buffer.slice(0, newlineIndex))
        buffer = buffer.slice(newlineIndex + 1)
      }
    },
    finish() {
      if (buffer) processLine(buffer)
      buffer = ''
      dispatch()
    }
  }
}

export async function consumeSseResponse(response, onEvent) {
  if (!response.body) throw new Error('浏览器不支持流式响应')
  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let terminalEvent = ''
  const parser = createSseParser(event => {
    if (event.type === 'done' || event.type === 'error') terminalEvent = event.type
    onEvent(event)
  })

  while (true) {
    const { done, value } = await reader.read()
    if (done) break
    parser.feed(decoder.decode(value, { stream: true }))
  }
  parser.feed(decoder.decode())
  parser.finish()
  if (!terminalEvent) throw new Error('流式响应意外中断，请重新生成')
  return terminalEvent
}

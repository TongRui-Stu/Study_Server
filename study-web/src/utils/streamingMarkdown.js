// Render completed paragraphs once. The unfinished block stays escaped Vue text;
// completion uses the canonical full-document Markdown renderer (e.g. references).
export function createStreamingMarkdownBlocks(render) {
  let source = ''
  let offset = 0
  let lineStart = 0
  let blockStart = 0
  let fence = null
  let math = null
  let blocks = []

  function reset() {
    source = ''
    offset = lineStart = blockStart = 0
    fence = math = null
    blocks = []
  }

  function update(content) {
    if (!content.startsWith(source)) reset()
    source = content
    for (; offset < source.length; offset++) {
      if (source[offset] !== '\n') continue
      const line = source.slice(lineStart, offset).replace(/\r$/, '')
      lineStart = offset + 1
      const marker = line.match(/^ {0,3}(`{3,}|~{3,})(.*)$/)
      if (fence) {
        if (marker && marker[1][0] === fence.char && marker[1].length >= fence.length && !marker[2].trim()) fence = null
      } else if (marker && !math) {
        fence = { char: marker[1][0], length: marker[1].length }
      } else {
        for (const token of line.matchAll(/(?<!\\)(?:\\\\)*?(\$\$|\\\[|\\\])/g)) {
          const delimiter = token[1]
          if (delimiter === '$$') math = math === '$$' ? null : math || '$$'
          else if (delimiter === '\\[' && !math) math = '\\['
          else if (delimiter === '\\]' && math === '\\[') math = null
        }
        if (!line.trim() && !math && offset + 1 > blockStart) {
          const text = source.slice(blockStart, offset + 1)
          blocks.push({ key: blockStart, source: text, html: render(text) })
          blockStart = offset + 1
        }
      }
    }
    return { blocks: blocks.slice(), tail: source.slice(blockStart) }
  }
  return { update, reset }
}

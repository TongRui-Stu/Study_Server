export function createAiAutoScroll(getElement, options = {}) {
  const afterUpdate = options.afterUpdate || (() => Promise.resolve())
  const schedule = options.schedule || (callback => requestAnimationFrame(callback))
  const cancelSchedule = options.cancelSchedule || (handle => cancelAnimationFrame(handle))
  let following = true
  let pending = false
  let disposed = false
  let frame = null

  function onScroll() {
    const element = getElement()
    if (element) following = element.scrollHeight - element.clientHeight - element.scrollTop <= 48
  }

  async function request(force = false) {
    if (disposed) return
    if (force) following = true
    if (!following || pending) return
    pending = true
    await afterUpdate()
    if (disposed) return
    frame = schedule(() => {
      pending = false
      frame = null
      const element = getElement()
      if (following && element) element.scrollTop = element.scrollHeight
    })
  }

  function dispose() {
    disposed = true
    if (frame !== null) cancelSchedule(frame)
  }
  return { request, onScroll, dispose }
}

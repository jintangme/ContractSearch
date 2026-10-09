import { onBeforeUnmount, ref } from 'vue'

const LAB_LAYOUT_KEY = 'contractLabLayoutV1'

export function useLabLayout(scheduleEditorLayout) {
  const workspaceWidth = ref(240)
  const resultPanelWidth = ref(360)
  const dockHeight = ref(280)
  const resizeState = ref(null)

  function clamp(value, min, max) {
    return Math.min(max, Math.max(min, value))
  }

  function persistLayout() {
    localStorage.setItem(LAB_LAYOUT_KEY, JSON.stringify({
      workspaceWidth: workspaceWidth.value,
      resultPanelWidth: resultPanelWidth.value,
      dockHeight: dockHeight.value
    }))
  }

  function restoreLayout() {
    try {
      const raw = localStorage.getItem(LAB_LAYOUT_KEY)
      if (!raw) return
      const saved = JSON.parse(raw)
      workspaceWidth.value = clamp(Number(saved.workspaceWidth) || 240, 220, 360)
      resultPanelWidth.value = clamp(Number(saved.resultPanelWidth) || 360, 260, 460)
      dockHeight.value = clamp(Number(saved.dockHeight) || 280, 180, 360)
    } catch (err) {
      localStorage.removeItem(LAB_LAYOUT_KEY)
    }
  }

  function startResize(type, event) {
    event.preventDefault()
    resizeState.value = {
      type,
      startX: event.clientX,
      startY: event.clientY,
      workspaceWidth: workspaceWidth.value,
      resultPanelWidth: resultPanelWidth.value,
      dockHeight: dockHeight.value
    }
    document.body.classList.add('contract-lab-resizing')
    window.addEventListener('mousemove', handleResizeMove)
    window.addEventListener('mouseup', stopResize)
  }

  function handleResizeMove(event) {
    const state = resizeState.value
    if (!state) return

    if (state.type === 'left') {
      workspaceWidth.value = clamp(state.workspaceWidth + event.clientX - state.startX, 220, 360)
    } else if (state.type === 'right') {
      resultPanelWidth.value = clamp(state.resultPanelWidth - (event.clientX - state.startX), 260, 460)
    } else if (state.type === 'dock') {
      dockHeight.value = clamp(state.dockHeight - (event.clientY - state.startY), 180, 360)
    }

    scheduleEditorLayout()
  }

  function stopResize() {
    resizeState.value = null
    document.body.classList.remove('contract-lab-resizing')
    window.removeEventListener('mousemove', handleResizeMove)
    window.removeEventListener('mouseup', stopResize)
    persistLayout()
    scheduleEditorLayout()
  }

  onBeforeUnmount(() => {
    window.removeEventListener('mousemove', handleResizeMove)
    window.removeEventListener('mouseup', stopResize)
    document.body.classList.remove('contract-lab-resizing')
  })

  return { workspaceWidth, resultPanelWidth, dockHeight, persistLayout, restoreLayout, startResize }
}

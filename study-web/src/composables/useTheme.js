import { computed, readonly, ref } from 'vue'

const STORAGE_KEY = 'study-system-theme'
const LIGHT_THEME = 'light'
const DARK_THEME = 'dark'

const theme = ref(LIGHT_THEME)

function normalizeTheme(value) {
  return value === DARK_THEME ? DARK_THEME : LIGHT_THEME
}

function applyTheme(value) {
  const nextTheme = normalizeTheme(value)
  theme.value = nextTheme

  if (typeof document !== 'undefined') {
    const root = document.documentElement
    root.classList.toggle('dark', nextTheme === DARK_THEME)
    root.dataset.theme = nextTheme
    root.style.colorScheme = nextTheme
  }

  return nextTheme
}

function persistTheme(value) {
  try {
    window.localStorage.setItem(STORAGE_KEY, value)
  } catch {
    // Storage may be unavailable in privacy-restricted environments.
  }
}

export function initializeTheme() {
  let storedTheme = null

  try {
    storedTheme = window.localStorage.getItem(STORAGE_KEY)
  } catch {
    storedTheme = null
  }

  const nextTheme = applyTheme(storedTheme)
  if (storedTheme !== null && storedTheme !== LIGHT_THEME && storedTheme !== DARK_THEME) {
    persistTheme(nextTheme)
  }

  return nextTheme
}

export function setTheme(value) {
  const nextTheme = applyTheme(value)
  persistTheme(nextTheme)
}

export function toggleTheme() {
  setTheme(theme.value === DARK_THEME ? LIGHT_THEME : DARK_THEME)
}

export function useTheme() {
  return {
    theme: readonly(theme),
    isDark: computed(() => theme.value === DARK_THEME),
    setTheme,
    toggleTheme,
  }
}

export { STORAGE_KEY as THEME_STORAGE_KEY }

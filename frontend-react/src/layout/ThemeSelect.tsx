import { useState } from 'react'

const THEMES = [
  { value: 'orange', label: '🟠 Orange' },
  { value: 'light', label: '☀️ Light' },
  { value: 'dark', label: '🌙 Dark' },
  { value: 'blue', label: '🔵 Blue' },
  { value: 'green', label: '💚 Green' },
  { value: 'red', label: '❤️ Red' },
]

function savedTheme() {
  try {
    return localStorage.getItem('theme') || 'orange'
  } catch {
    return 'orange'
  }
}

export function ThemeSelect() {
  const [theme, setTheme] = useState(savedTheme)

  const change = (value: string) => {
    setTheme(value)
    document.documentElement.setAttribute('data-theme', value)
    try {
      localStorage.setItem('theme', value)
    } catch {
      /* the choice just won't be remembered */
    }
  }

  return (
    <select className="theme-dropdown" value={theme} onChange={(e) => change(e.target.value)} aria-label="Colour theme">
      {THEMES.map((t) => (
        <option key={t.value} value={t.value}>
          {t.label}
        </option>
      ))}
    </select>
  )
}

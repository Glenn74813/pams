import { createContext, useContext } from 'react'

export type ToastKind = 'success' | 'error' | 'info'

export const ToastContext = createContext<(message: string, kind?: ToastKind) => void>(() => {})

export function useToast() {
  return useContext(ToastContext)
}

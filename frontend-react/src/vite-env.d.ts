/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** Where the Vue app still serves modules that have not moved yet. */
  readonly VITE_LEGACY_APP_URL?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}

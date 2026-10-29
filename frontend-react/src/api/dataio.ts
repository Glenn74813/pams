import { api, download } from './client'
import type { Paged } from './types'

export type ImportEntityKey = 'items' | 'suppliers' | 'departments' | 'users'
export type ImportStatus = 'validated' | 'committed' | 'aborted'
export type RowStatus = 'ok' | 'duplicate' | 'incomplete' | 'invalid' | 'aborted'

export interface ImportColumn {
  name: string
  required: boolean
  hint: string
  aliases: string[]
}

export interface ImportEntityInfo {
  key: ImportEntityKey
  label: string
  columns: ImportColumn[]
}

export interface ImportCounts {
  total: number
  ok: number
  duplicate: number
  incomplete: number
  invalid: number
  imported: number
}

export interface ImportBatch {
  id: number
  entity: ImportEntityKey
  file_name: string
  status: ImportStatus
  counts: ImportCounts
  options: Record<string, unknown>
  error_message: string | null
  created_by: { id: number | null; name: string } | null
  committed_by: { id: number; name: string } | null
  created_at: string
  committed_at: string | null
  aborted_at: string | null
}

export interface ImportRowResult {
  row: number | null
  status: RowStatus
  key: string | null
  messages: string[]
  data: Record<string, string | null>
}

export interface ValidationResult {
  batch: ImportBatch
  ignored_columns: string[]
  exceptions: ImportRowResult[]
  preview: ImportRowResult[]
}

export interface TemporaryPassword {
  row: number
  name: string
  email: string
  temporary_password: string
}

export interface CommitResult {
  message: string
  imported: number
  categories_created: number
  created: { row: number; id: number; key: string }[]
  temporary_passwords?: TemporaryPassword[]
  exceptions: ImportRowResult[]
  batch: ImportBatch
}

export interface ImportBatchDetail extends ImportBatch {
  exceptions: ImportRowResult[]
}

export const dataioApi = {
  exportAll: () => download('/data/export', {}, 'pams_export.zip'),
  entities: () => api<{ entities: ImportEntityInfo[]; max_rows: number }>('GET', '/data/import/entities'),
  template: (entity: ImportEntityKey) => download(`/data/import/templates/${entity}`, {}, `${entity}_template.csv`),
  validate: (entity: ImportEntityKey, file: File, createMissingCategories: boolean) => {
    const fd = new FormData()
    fd.append('entity', entity)
    fd.append('file', file)
    if (entity === 'items') fd.append('create_missing_categories', String(createMissingCategories))
    return api<ValidationResult>('POST', '/data/import/validate', fd)
  },
  commit: (id: number) => api<CommitResult>('POST', `/data/import/${id}/commit`),
  batches: (params: { page?: number; entity?: string; status?: string }) => api<Paged<ImportBatch>>('GET', '/data/import/batches', undefined, { params }),
  batch: (id: number) => api<ImportBatchDetail>('GET', `/data/import/batches/${id}`),
  exceptionsCsv: (id: number) => download(`/data/import/batches/${id}/exceptions`, {}, `import_${id}_exceptions.csv`),
}

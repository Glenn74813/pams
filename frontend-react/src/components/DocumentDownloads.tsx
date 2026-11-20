import { ExcelButton } from './ExcelButton'

export interface DocumentLink {
  /** API path of the form's export, e.g. `/rfq/12/noa/export`. */
  path: string
  /** File name of the Excel download; the PDF takes the same name. */
  name: string
  label: string
}

/**
 * The documents a record sends out (RFQ, PO, notices, contracts…), each as Excel and PDF. Signatures appear on the files
 * only once each signatory has approved, issued or signed the record.
 */
export function DocumentDownloads({ docs }: { docs: (DocumentLink | false | null | undefined)[] }) {
  const shown = docs.filter((d): d is DocumentLink => !!d)
  if (shown.length === 0) return null
  return (
    <div className="document-downloads">
      <span className="muted small">Documents</span>
      {shown.map((d) => (
        <span key={d.path} className="document-download">
          <ExcelButton path={d.path} name={d.name} label={d.label} />
        </span>
      ))}
    </div>
  )
}

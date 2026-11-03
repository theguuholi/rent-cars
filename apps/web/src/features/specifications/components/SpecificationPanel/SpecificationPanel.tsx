import { Link } from '@tanstack/react-router'
import type { Specification, SpecificationInput } from '../../specifications-api'
import { SpecificationEditor } from '../SpecificationEditor/SpecificationEditor'

type Props = {
  id?: string
  item?: Specification
  deleting: boolean
  catalogLoaded: boolean
  busy: boolean
  onCancel: () => void
  onDelete: (id: string) => void
  onSave: (input: SpecificationInput) => void
}

export function SpecificationPanel({
  id,
  item,
  deleting,
  catalogLoaded,
  busy,
  onCancel,
  onDelete,
  onSave,
}: Props) {
  return (
    <aside
      aria-label="Specification details"
      className="rounded-lg border border-slate-200 p-5 lg:col-start-2 lg:row-start-1"
    >
      {id !== undefined && !item ? (
        <div>
          {catalogLoaded && <p role="alert">Specification not found.</p>}
          <Link to="/specifications">Back to specifications</Link>
        </div>
      ) : deleting && item ? (
        <div>
          <h3 className="wrap-anywhere text-xl font-bold">Delete {item.name}?</h3>
          <p>This removes the specification from the catalog.</p>
          <div className="mt-4 flex flex-wrap gap-2">
            <button className="btn-danger" disabled={busy} onClick={() => onDelete(item.id)}>
              Confirm deletion
            </button>
            <button className="btn-secondary" autoFocus disabled={busy} onClick={onCancel}>
              Cancel
            </button>
          </div>
        </div>
      ) : (
        <SpecificationEditor item={item} busy={busy} onCancel={onCancel} onSave={onSave} />
      )}
    </aside>
  )
}

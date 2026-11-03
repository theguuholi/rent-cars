import { PageSection } from '../../../../components/PageSection/PageSection'
import { Link, useMatchRoute, useNavigate, useLocation } from '@tanstack/react-router'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { SpecificationEditor } from '../../components/SpecificationEditor/SpecificationEditor'
import {
  deleteSpecification,
  listSpecifications,
  saveSpecification,
} from '../../specifications-api'
import type { SpecificationInput } from '../../specifications-api'

declare module '@tanstack/react-router' {
  interface HistoryState {
    specificationNotice?: string
  }
}
type Change =
  | { type: 'save'; input: SpecificationInput; id?: string }
  | { type: 'delete'; id: string }
const queryKey = ['specifications']

export function SpecificationsPage() {
  const match = useMatchRoute()
  const navigate = useNavigate()
  const creating = !!match({ to: '/specifications/new' })
  const edit = match({ to: '/specifications/$id/edit' })
  const deleting = match({ to: '/specifications/$id/delete' })
  const selected = edit || deleting
  const id = selected ? selected.id : undefined
  const panelOpen = creating || !!selected
  const notice = useLocation({ select: (location) => location.state.specificationNotice })
  const client = useQueryClient()
  const catalog = useQuery({
    queryKey,
    queryFn: ({ signal }) => listSpecifications(signal),
    staleTime: 30_000,
  })
  const item = catalog.data?.find((entry) => entry.id === id)
  const change = useMutation({
    mutationFn: async (command: Change) => {
      switch (command.type) {
        case 'save':
          await saveSpecification(command.input, command.id)
          break
        case 'delete':
          await deleteSpecification(command.id)
          break
      }
    },
    onSuccess: async (_, command) => {
      await navigate({
        to: '/specifications',
        replace: true,
        state: {
          specificationNotice:
            command.type === 'delete' ? 'Specification deleted.' : 'Specification saved.',
        },
      })
      await client.invalidateQueries({ queryKey })
    },
  })

  function close() {
    if (change.isError) void client.invalidateQueries({ queryKey })
    change.reset()
    void navigate({ to: '/specifications', replace: true })
  }
  return (
    <PageSection
      eyebrow="Catalog"
      title="Vehicle specifications"
      description="Manage the features and equipment available on rental vehicles."
      action={
        !panelOpen && (
          <Link className="btn-primary" to="/specifications/new">
            New specification
          </Link>
        )
      }
    >
      {notice && (
        <p role="status" className="mb-4 text-brand">
          {notice}
        </p>
      )}
      {change.isError && <p role="alert">{change.error.message}</p>}
      <div
        className={
          panelOpen ? 'grid items-start gap-6 lg:grid-cols-[minmax(0,1fr)_22rem]' : undefined
        }
      >
        {panelOpen && (
          <aside
            aria-label="Specification details"
            className="rounded-lg border border-slate-200 p-5 lg:col-start-2 lg:row-start-1"
          >
            {selected && !item ? (
              <div>
                {catalog.isSuccess && <p role="alert">Specification not found.</p>}
                <Link to="/specifications">Back to specifications</Link>
              </div>
            ) : deleting && item ? (
              <div>
                <h3 className="wrap-anywhere text-xl font-bold">Delete {item.name}?</h3>
                <p>This removes the specification from the catalog.</p>
                <div className="mt-4 flex flex-wrap gap-2">
                  <button
                    className="btn-danger"
                    disabled={change.isPending}
                    onClick={() => change.mutate({ type: 'delete', id: item.id })}
                  >
                    Confirm deletion
                  </button>
                  <button
                    className="btn-secondary"
                    autoFocus
                    disabled={change.isPending}
                    onClick={close}
                  >
                    Cancel
                  </button>
                </div>
              </div>
            ) : (
              <SpecificationEditor
                item={item}
                key={id}
                busy={change.isPending}
                onCancel={close}
                onSave={(input) => change.mutate({ type: 'save', input, id })}
              />
            )}
          </aside>
        )}
        <div className="min-w-0 lg:col-start-1 lg:row-start-1">
          {catalog.isPending ? (
            <p role="status">Loading specifications…</p>
          ) : catalog.isError ? (
            <div role="alert">
              <p>Could not load specifications.</p>
              <button className="btn-secondary" onClick={() => void catalog.refetch()}>
                Try again
              </button>
            </div>
          ) : catalog.data.length === 0 ? (
            <p>No specifications yet.</p>
          ) : (
            <table className="catalog-table" aria-label="Specifications">
              <thead>
                <tr>
                  <th>Name</th>
                  <th>Description</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {catalog.data.map((item) => (
                  <tr key={item.id} data-selected={id === item.id}>
                    <th scope="row">{item.name}</th>
                    <td>{item.description}</td>
                    <td>
                      <div className="flex flex-wrap gap-3">
                        <Link
                          className="text-brand"
                          disabled={panelOpen || change.isPending}
                          aria-label={`Edit ${item.name}`}
                          to="/specifications/$id/edit"
                          params={{ id: item.id }}
                        >
                          Edit
                        </Link>
                        <Link
                          className="text-red-700"
                          disabled={panelOpen || change.isPending}
                          aria-label={`Delete ${item.name}`}
                          to="/specifications/$id/delete"
                          params={{ id: item.id }}
                        >
                          Delete
                        </Link>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      </div>
    </PageSection>
  )
}

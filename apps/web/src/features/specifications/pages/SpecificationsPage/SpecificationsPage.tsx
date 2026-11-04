import { StaleEdit } from '../../../../edit-conflict'
import { useCurrentAction } from '../../../../routes/use-current-action'
import { AccessError } from '../../../auth/protected-fetch'
import { PageSection } from '../../../../components/PageSection/PageSection'
import { Link, useMatchRoute, useNavigate, useLocation } from '@tanstack/react-router'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { SpecificationPanel } from '../../components/SpecificationPanel/SpecificationPanel'
import {
  deleteSpecification,
  listSpecifications,
  saveSpecification,
} from '../../specifications-api'
import type { SpecificationEdit } from '../../specifications-api'

declare module '@tanstack/react-router' {
  interface HistoryState {
    specificationNotice?: string
  }
}
type Change =
  | { type: 'save'; input: SpecificationEdit; id?: string }
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
  const begin = useCurrentAction()
  const change = useMutation({
    onMutate: begin,
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
    onError: async (error) => {
      if (error instanceof StaleEdit) await client.invalidateQueries({ queryKey })
    },
    onSuccess: async (_, command, current) => {
      if (current())
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
          <SpecificationPanel
            key={id}
            id={id}
            item={item}
            deleting={!!deleting}
            catalogLoaded={catalog.isSuccess}
            busy={change.isPending}
            onCancel={close}
            onDelete={(id) => change.mutate({ type: 'delete', id })}
            onSave={(input) => change.mutate({ type: 'save', input, id })}
          />
        )}
        <div className="min-w-0 lg:col-start-1 lg:row-start-1">
          {catalog.isPending ? (
            <p role="status">Loading specifications…</p>
          ) : catalog.isError ? (
            <div role="alert">
              <p>
                {catalog.error instanceof AccessError
                  ? catalog.error.message
                  : 'Could not load specifications.'}
              </p>
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

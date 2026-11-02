import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { SpecificationEditor } from './SpecificationEditor'
import { deleteSpecification, listSpecifications, saveSpecification } from './specifications-api'
import type { Specification, SpecificationInput } from './specifications-api'
import './specifications.css'

type Action = { type: 'create' } | { type: 'edit' | 'delete'; item: Specification }
type Change =
  | { type: 'save'; input: SpecificationInput; id?: string }
  | { type: 'delete'; id: string }
const queryKey = ['specifications']

export function Specifications() {
  const [action, setAction] = useState<Action | null>(null)
  const [notice, setNotice] = useState('')
  const client = useQueryClient()
  const catalog = useQuery({ queryKey, queryFn: ({ signal }) => listSpecifications(signal) })
  const change = useMutation({
    mutationFn: async (command: Change) => {
      if (command.type === 'delete') await deleteSpecification(command.id)
      else await saveSpecification(command.input, command.id)
    },
    onSuccess: async (_, command) => {
      setAction(null)
      setNotice(command.type === 'delete' ? 'Specification deleted.' : 'Specification saved.')
      await client.invalidateQueries({ queryKey })
    },
  })

  function open(next: Action | null) {
    if (change.isError && next === null) void client.invalidateQueries({ queryKey })
    change.reset()
    setNotice('')
    setAction(next)
  }

  return (
    <section className="specifications" aria-labelledby="specs-title">
      <h2 id="specs-title">Vehicle specifications</h2>
      <p>Manage the features and equipment available on rental vehicles.</p>
      {notice && <p role="status">{notice}</p>}
      {change.isError && <p role="alert">{change.error.message}</p>}
      {action?.type === 'delete' ? (
        <div className="confirmation">
          <h3>Delete {action.item.name}?</h3>
          <p>This removes the specification from the catalog.</p>
          <div className="actions">
            <button
              disabled={change.isPending}
              onClick={() => change.mutate({ type: 'delete', id: action.item.id })}
            >
              Confirm deletion
            </button>
            <button disabled={change.isPending} onClick={() => open(null)}>
              Cancel
            </button>
          </div>
        </div>
      ) : action ? (
        <SpecificationEditor
          item={action.type === 'edit' ? action.item : undefined}
          busy={change.isPending}
          onCancel={() => open(null)}
          onSave={(input) =>
            change.mutate({
              type: 'save',
              input,
              id: action.type === 'edit' ? action.item.id : undefined,
            })
          }
        />
      ) : (
        <button disabled={change.isPending} onClick={() => open({ type: 'create' })}>
          New specification
        </button>
      )}
      {catalog.isPending ? (
        <p role="status">Loading specifications…</p>
      ) : catalog.isError ? (
        <div role="alert">
          <p>Could not load specifications.</p>
          <button onClick={() => void catalog.refetch()}>Try again</button>
        </div>
      ) : catalog.data.length === 0 ? (
        <p>No specifications yet.</p>
      ) : (
        <ul aria-label="Specifications">
          {catalog.data.map((item) => (
            <li key={item.id}>
              <div>
                <strong>{item.name}</strong>
                <p>{item.description}</p>
              </div>
              <div className="actions">
                <button
                  disabled={!!action || change.isPending}
                  aria-label={`Edit ${item.name}`}
                  onClick={() => open({ type: 'edit', item })}
                >
                  Edit
                </button>
                <button
                  disabled={!!action || change.isPending}
                  aria-label={`Delete ${item.name}`}
                  onClick={() => open({ type: 'delete', item })}
                >
                  Delete
                </button>
              </div>
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}

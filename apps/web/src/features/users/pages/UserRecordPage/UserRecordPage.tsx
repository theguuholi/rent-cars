import { Button } from '../../../../components/Button/Button'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, useNavigate } from '@tanstack/react-router'
import { PageSection } from '../../../../components/PageSection/PageSection'
import { deleteUser, getUser } from '../../users-api'
import { UserFormPage } from '../UserFormPage/UserFormPage'

export function UserRecordPage({ id, mode }: { id: string; mode: 'detail' | 'edit' | 'delete' }) {
  const query = useQuery({
    queryKey: ['managedUser', id],
    queryFn: ({ signal }) => getUser(id, signal),
  })
  const client = useQueryClient()
  const navigate = useNavigate()
  const remove = useMutation({
    mutationFn: () => deleteUser(id),
    onSuccess: async () => {
      await client.invalidateQueries({ queryKey: ['managedUsers'] })
      client.removeQueries({ queryKey: ['managedUser', id] })
      await navigate({ to: '/users', replace: true })
    },
  })
  if (query.isPending)
    return (
      <PageSection eyebrow="Administration" title="User details">
        <p role="status">Loading user…</p>
      </PageSection>
    )
  if (query.isError)
    return (
      <PageSection eyebrow="Administration" title="User details">
        <p role="alert">{query.error.message}</p>
        <Button onClick={() => void query.refetch()}>Try again</Button>
      </PageSection>
    )
  const user = query.data
  if (mode === 'edit') return <UserFormPage key={user.id} user={user} />
  if (mode === 'delete')
    return (
      <PageSection
        eyebrow="Administration"
        title={`Delete ${user.email}?`}
        description="Accounts with rental history cannot be deleted."
      >
        {remove.isError && <p role="alert">{remove.error.message}</p>}
        <Button variant="danger" disabled={remove.isPending} onClick={() => remove.mutate()}>
          Confirm deletion
        </Button>{' '}
        <Link to="/users/$id" params={{ id }}>
          Cancel
        </Link>
      </PageSection>
    )
  return (
    <PageSection
      eyebrow="Administration"
      title={user.firstName ? `${user.firstName} ${user.lastName}` : user.email}
    >
      <dl className="max-w-xl space-y-2 rounded border border-line bg-white p-6">
        <dt>Email</dt>
        <dd>{user.email}</dd>
        <dt>Role</dt>
        <dd>{user.role}</dd>
        {user.userName && (
          <>
            <dt>Username</dt>
            <dd>{user.userName}</dd>
            <dt>CNH number</dt>
            <dd>{user.driveLicense}</dd>
          </>
        )}
      </dl>
      <div className="mt-4 flex gap-4">
        <Link className="action" to="/users/$id/edit" params={{ id }}>
          Edit user
        </Link>
        <Link className="action" data-variant="secondary" to="/users/$id/delete" params={{ id }}>
          Delete user
        </Link>
        <Link className="action" data-variant="secondary" to="/users">
          Back to users
        </Link>
      </div>
    </PageSection>
  )
}

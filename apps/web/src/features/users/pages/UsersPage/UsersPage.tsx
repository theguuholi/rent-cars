import { useQuery } from '@tanstack/react-query'
import { Link } from '@tanstack/react-router'
import { PageSection } from '../../../../components/PageSection/PageSection'
import { listUsers } from '../../users-api'

export function UsersPage() {
  const users = useQuery({ queryKey: ['managedUsers'], queryFn: ({ signal }) => listUsers(signal) })
  return (
    <PageSection
      eyebrow="Administration"
      title="Users"
      description="Manage driver and administrator accounts."
    >
      <Link className="btn-primary inline-block" to="/users/new">
        New user
      </Link>
      {users.isPending && <p role="status">Loading users…</p>}
      {users.isError && (
        <p role="alert">
          {users.error.message} <button onClick={() => void users.refetch()}>Try again</button>
        </p>
      )}
      {users.isSuccess && users.data.length === 0 && <p>No users found.</p>}
      {users.isSuccess && users.data.length > 0 && (
        <div className="overflow-x-auto">
          <table className="mt-6 w-full text-left">
            <thead>
              <tr>
                <th>Name</th>
                <th>Email</th>
                <th>Role</th>
                <th>Action</th>
              </tr>
            </thead>
            <tbody>
              {users.data.map((user) => (
                <tr key={user.id}>
                  <td>{[user.firstName, user.lastName].filter(Boolean).join(' ') || '—'}</td>
                  <td>{user.email}</td>
                  <td>{user.role}</td>
                  <td>
                    <Link
                      to="/users/$id"
                      params={{ id: user.id }}
                      aria-label={`View ${user.email}`}
                    >
                      View
                    </Link>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </PageSection>
  )
}

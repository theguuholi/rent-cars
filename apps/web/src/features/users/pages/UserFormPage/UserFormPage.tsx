import { useState } from 'react'
import { useForm } from '@tanstack/react-form'
import { useQueryClient } from '@tanstack/react-query'
import { Link, useNavigate } from '@tanstack/react-router'
import { PageSection } from '../../../../components/PageSection/PageSection'
import { UserTextField } from '../../components/UserTextField/UserTextField'
import { passwordHint } from '../../../auth/password-schema'
import { createUser, updateUser, type ManagedUser } from '../../users-api'
import { editedUserSchema, newUserSchema, type UserDraft } from './user-form-schema'

export function UserFormPage({ user }: { user?: ManagedUser }) {
  const [error, setError] = useState('')
  const [fieldErrors, setFieldErrors] = useState<Partial<Record<keyof UserDraft, string>>>({})
  const navigate = useNavigate()
  const client = useQueryClient()
  const showValidationErrors = (issues: { path: PropertyKey[]; message: string }[]) => {
    const errors: Partial<Record<keyof UserDraft, string>> = {}
    for (const issue of issues) errors[issue.path[0] as keyof UserDraft] = issue.message
    setFieldErrors(errors)
  }
  const form = useForm({
    defaultValues: {
      firstName: user?.firstName ?? '',
      lastName: user?.lastName ?? '',
      userName: user?.userName ?? '',
      driveLicense: user?.driveLicense ?? '',
      email: user?.email ?? '',
      role: user?.role ?? ('USER' as const),
      password: '',
      passwordConfirmation: '',
    } satisfies UserDraft,
    onSubmit: async ({ value }) => {
      if (user) {
        const parsed = editedUserSchema.safeParse({
          email: value.email,
          role: value.role,
          firstName: user.firstName === null ? null : value.firstName,
          lastName: user.lastName === null ? null : value.lastName,
          userName: user.userName === null ? null : value.userName,
          driveLicense: user.driveLicense === null ? null : value.driveLicense,
        })
        if (!parsed.success) return showValidationErrors(parsed.error.issues)
        setFieldErrors({})
        setError('')
        try {
          const saved = await updateUser(user.id, parsed.data)
          await client.invalidateQueries({ queryKey: ['managedUsers'] })
          client.setQueryData(['managedUser', user.id], saved)
          await navigate({ to: '/users/$id', params: { id: user.id } })
        } catch (failure) {
          setError(failure instanceof Error ? failure.message : 'Could not save this user.')
        }
        return
      }
      const parsed = newUserSchema.safeParse(value)
      if (!parsed.success) return showValidationErrors(parsed.error.issues)
      setFieldErrors({})
      setError('')
      try {
        await createUser(parsed.data, parsed.data.role)
        await client.invalidateQueries({ queryKey: ['managedUsers'] })
        await navigate({ to: '/users' })
      } catch (failure) {
        setError(failure instanceof Error ? failure.message : 'Could not save this user.')
      }
    },
  })

  return (
    <PageSection eyebrow="Administration" title={user ? 'Edit user' : 'New user'}>
      <form
        className="grid max-w-md gap-4"
        noValidate
        onSubmit={(event) => {
          event.preventDefault()
          void form.handleSubmit()
        }}
      >
        {(!user || user.firstName !== null) && (
          <>
            <form.Field name="firstName">
              {(field) => (
                <UserTextField
                  label="First name"
                  error={fieldErrors.firstName}
                  input={{
                    required: true,
                    maxLength: 80,
                    value: field.state.value,
                    onChange: (e) => field.handleChange(e.target.value),
                  }}
                />
              )}
            </form.Field>
            <form.Field name="lastName">
              {(field) => (
                <UserTextField
                  label="Last name"
                  error={fieldErrors.lastName}
                  input={{
                    required: true,
                    maxLength: 80,
                    value: field.state.value,
                    onChange: (e) => field.handleChange(e.target.value),
                  }}
                />
              )}
            </form.Field>
            <form.Field name="userName">
              {(field) => (
                <UserTextField
                  label="Username"
                  error={fieldErrors.userName}
                  input={{
                    required: true,
                    minLength: 3,
                    maxLength: 40,
                    value: field.state.value,
                    onChange: (e) => field.handleChange(e.target.value),
                  }}
                />
              )}
            </form.Field>
            <form.Field name="driveLicense">
              {(field) => (
                <UserTextField
                  label="CNH number"
                  error={fieldErrors.driveLicense}
                  input={{
                    required: true,
                    inputMode: 'numeric',
                    minLength: 11,
                    maxLength: 11,
                    value: field.state.value,
                    onChange: (e) => field.handleChange(e.target.value),
                  }}
                />
              )}
            </form.Field>
          </>
        )}
        <form.Field name="email">
          {(field) => (
            <UserTextField
              label="Email"
              error={fieldErrors.email}
              input={{
                type: 'email',
                required: true,
                maxLength: 254,
                value: field.state.value,
                onChange: (e) => field.handleChange(e.target.value),
              }}
            />
          )}
        </form.Field>
        <form.Field name="role">
          {(field) => (
            <label>
              Role
              <select
                className="control"
                value={field.state.value}
                onChange={(e) => field.handleChange(e.target.value as UserDraft['role'])}
              >
                <option value="USER">Driver</option>
                <option value="ADMIN">Administrator</option>
              </select>
            </label>
          )}
        </form.Field>
        {!user && (
          <>
            <form.Field name="password">
              {(field) => (
                <UserTextField
                  label="Password"
                  error={fieldErrors.password}
                  input={{
                    type: 'password',
                    required: true,
                    minLength: 12,
                    maxLength: 72,
                    value: field.state.value,
                    onChange: (e) => field.handleChange(e.target.value),
                  }}
                />
              )}
            </form.Field>
            <p>{passwordHint}</p>
            <form.Field name="passwordConfirmation">
              {(field) => (
                <UserTextField
                  label="Confirm password"
                  error={fieldErrors.passwordConfirmation}
                  input={{
                    type: 'password',
                    required: true,
                    value: field.state.value,
                    onChange: (e) => field.handleChange(e.target.value),
                  }}
                />
              )}
            </form.Field>
          </>
        )}
        {error && <p role="alert">{error}</p>}
        <form.Subscribe selector={(state) => state.isSubmitting}>
          {(busy) => (
            <button className="btn-primary" type="submit" disabled={busy}>
              {user ? 'Save user' : 'Create user'}
            </button>
          )}
        </form.Subscribe>
        <Link to={user ? '/users/$id' : '/users'} params={user ? { id: user.id } : undefined}>
          Cancel
        </Link>
      </form>
    </PageSection>
  )
}

import { useState } from 'react'
import { useForm } from '@tanstack/react-form'
import { useQueryClient } from '@tanstack/react-query'
import { Link, useNavigate } from '@tanstack/react-router'
import { PageSection } from '../../../../components/PageSection/PageSection'
import { passwordHint } from '../../../auth/password-schema'
import { createUser, updateUser, type ManagedUser } from '../../users-api'
import { editedUserSchema, newUserSchema } from './user-form-schema'

type Draft = {
  firstName: string
  lastName: string
  userName: string
  driveLicense: string
  email: string
  role: 'USER' | 'ADMIN'
  password: string
  passwordConfirmation: string
}

export function UserFormPage({ user }: { user?: ManagedUser }) {
  const [error, setError] = useState('')
  const [fieldErrors, setFieldErrors] = useState<Partial<Record<keyof Draft, string>>>({})
  const navigate = useNavigate()
  const client = useQueryClient()
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
    } as Draft,
    onSubmit: async ({ value }) => {
      const input = user
        ? {
            email: value.email,
            role: value.role,
            firstName: user.firstName === null ? null : value.firstName,
            lastName: user.lastName === null ? null : value.lastName,
            userName: user.userName === null ? null : value.userName,
            driveLicense: user.driveLicense === null ? null : value.driveLicense,
          }
        : value
      const parsed = user ? editedUserSchema.safeParse(input) : newUserSchema.safeParse(input)
      if (!parsed.success) {
        const errors: Partial<Record<keyof Draft, string>> = {}
        for (const issue of parsed.error.issues)
          errors[issue.path[0] as keyof Draft] = issue.message
        setFieldErrors(errors)
        return
      }
      setFieldErrors({})
      setError('')
      try {
        if (user) {
          const saved = await updateUser(user.id, editedUserSchema.parse(input))
          await client.invalidateQueries({ queryKey: ['managedUsers'] })
          client.setQueryData(['managedUser', user.id], saved)
          await navigate({ to: '/users/$id', params: { id: user.id } })
        } else {
          const created = newUserSchema.parse(input)
          await createUser(created, created.role)
          await client.invalidateQueries({ queryKey: ['managedUsers'] })
          await navigate({ to: '/users' })
        }
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
                <label>
                  First name
                  <input
                    aria-label="First name"
                    className="control"
                    required
                    maxLength={80}
                    value={field.state.value}
                    aria-invalid={!!fieldErrors.firstName}
                    onChange={(e) => field.handleChange(e.target.value)}
                  />
                  {fieldErrors.firstName && <span role="alert">{fieldErrors.firstName}</span>}
                </label>
              )}
            </form.Field>
            <form.Field name="lastName">
              {(field) => (
                <label>
                  Last name
                  <input
                    aria-label="Last name"
                    className="control"
                    required
                    maxLength={80}
                    value={field.state.value}
                    aria-invalid={!!fieldErrors.lastName}
                    onChange={(e) => field.handleChange(e.target.value)}
                  />
                  {fieldErrors.lastName && <span role="alert">{fieldErrors.lastName}</span>}
                </label>
              )}
            </form.Field>
            <form.Field name="userName">
              {(field) => (
                <label>
                  Username
                  <input
                    aria-label="Username"
                    className="control"
                    required
                    minLength={3}
                    maxLength={40}
                    value={field.state.value}
                    aria-invalid={!!fieldErrors.userName}
                    onChange={(e) => field.handleChange(e.target.value)}
                  />
                  {fieldErrors.userName && <span role="alert">{fieldErrors.userName}</span>}
                </label>
              )}
            </form.Field>
            <form.Field name="driveLicense">
              {(field) => (
                <label>
                  CNH number
                  <input
                    aria-label="CNH number"
                    className="control"
                    required
                    inputMode="numeric"
                    minLength={11}
                    maxLength={11}
                    value={field.state.value}
                    aria-invalid={!!fieldErrors.driveLicense}
                    onChange={(e) => field.handleChange(e.target.value)}
                  />
                  {fieldErrors.driveLicense && <span role="alert">{fieldErrors.driveLicense}</span>}
                </label>
              )}
            </form.Field>
          </>
        )}
        <form.Field name="email">
          {(field) => (
            <label>
              Email
              <input
                aria-label="Email"
                className="control"
                type="email"
                required
                maxLength={254}
                value={field.state.value}
                aria-invalid={!!fieldErrors.email}
                onChange={(e) => field.handleChange(e.target.value)}
              />
              {fieldErrors.email && <span role="alert">{fieldErrors.email}</span>}
            </label>
          )}
        </form.Field>
        <form.Field name="role">
          {(field) => (
            <label>
              Role
              <select
                className="control"
                value={field.state.value}
                onChange={(e) => field.handleChange(e.target.value as Draft['role'])}
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
                <label>
                  Password
                  <input
                    aria-label="Password"
                    className="control"
                    type="password"
                    required
                    minLength={12}
                    maxLength={72}
                    value={field.state.value}
                    aria-invalid={!!fieldErrors.password}
                    onChange={(e) => field.handleChange(e.target.value)}
                  />
                  {fieldErrors.password && <span role="alert">{fieldErrors.password}</span>}
                </label>
              )}
            </form.Field>
            <p>{passwordHint}</p>
            <form.Field name="passwordConfirmation">
              {(field) => (
                <label>
                  Confirm password
                  <input
                    aria-label="Confirm password"
                    className="control"
                    type="password"
                    required
                    value={field.state.value}
                    aria-invalid={!!fieldErrors.passwordConfirmation}
                    onChange={(e) => field.handleChange(e.target.value)}
                  />
                  {fieldErrors.passwordConfirmation && (
                    <span role="alert">{fieldErrors.passwordConfirmation}</span>
                  )}
                </label>
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

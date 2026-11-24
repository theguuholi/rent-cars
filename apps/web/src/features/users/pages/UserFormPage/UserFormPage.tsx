import { useState } from 'react'
import { useForm, type StandardSchemaV1 } from '@tanstack/react-form'
import { useQueryClient } from '@tanstack/react-query'
import { Link, useNavigate } from '@tanstack/react-router'
import { SubmitButton } from '../../../../components/SubmitButton/SubmitButton'
import { PageSection } from '../../../../components/PageSection/PageSection'
import { TextField } from '../../../../components/TextField/TextField'
import { passwordHint } from '../../../auth/password-schema'
import { createUser, updateUser, type ManagedUser } from '../../users-api'
import { userEditDraftSchema, newUserSchema, type UserDraft } from './user-form-schema'

export function UserFormPage({ user }: { user?: ManagedUser }) {
  const [error, setError] = useState('')
  const navigate = useNavigate()
  const client = useQueryClient()
  const validationSchema: StandardSchemaV1<UserDraft, unknown> = user
    ? userEditDraftSchema(user)
    : newUserSchema
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
    validators: { onSubmit: validationSchema },
    onSubmit: async ({ value }) => {
      if (user) {
        const input = userEditDraftSchema(user).parse(value)
        setError('')
        try {
          const saved = await updateUser(user.id, input)
          await client.invalidateQueries({ queryKey: ['managedUsers'] })
          client.setQueryData(['managedUser', user.id], saved)
          await navigate({ to: '/users/$id', params: { id: user.id } })
        } catch (failure) {
          setError(failure instanceof Error ? failure.message : 'Could not save this user.')
        }
        return
      }
      const input = newUserSchema.parse(value)
      setError('')
      try {
        await createUser(input, input.role)
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
        className="grid max-w-xl gap-5 rounded-3xl bg-white shadow-sm p-6 sm:p-8"
        noValidate
        onSubmit={(event) => {
          event.preventDefault()
          void form.handleSubmit()
        }}
      >
        {(!user || user.firstName !== null) && (
          <>
            <TextField form={form} name="firstName" required />
            <TextField form={form} name="lastName" required />
            <TextField
              form={form}
              name="userName"
              label="Username"
              required
              minLength={3}
              maxLength={40}
            />
            <TextField
              form={form}
              name="driveLicense"
              label="CNH number"
              required
              inputMode="numeric"
              minLength={11}
              maxLength={11}
            />
          </>
        )}
        <TextField form={form} name="email" type="email" required maxLength={254} />
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
            <TextField
              form={form}
              name="password"
              type="password"
              required
              minLength={12}
              maxLength={72}
            />
            <p>{passwordHint}</p>
            <TextField
              form={form}
              name="passwordConfirmation"
              label="Confirm password"
              type="password"
              required
              maxLength={72}
            />
          </>
        )}
        {error && <p role="alert">{error}</p>}
        <SubmitButton form={form}>{user ? 'Save user' : 'Create user'}</SubmitButton>
        <Link to={user ? '/users/$id' : '/users'} params={user ? { id: user.id } : undefined}>
          Cancel
        </Link>
      </form>
    </PageSection>
  )
}

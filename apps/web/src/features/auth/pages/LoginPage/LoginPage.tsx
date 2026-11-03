import { useState } from 'react'
import { useForm } from '@tanstack/react-form'
import { useNavigate, useLocation } from '@tanstack/react-router'
import { PageSection } from '../../../../components/PageSection/PageSection'
import { signIn } from '../../auth-api'
import { session } from '../../session'

import { loginDestination } from '../../access'
export function LoginPage() {
  const afterLogin = useLocation({ select: (location) => location.state.afterLogin })
  const [error, setError] = useState('')
  const navigate = useNavigate()
  const form = useForm({
    defaultValues: { email: '', password: '' },
    onSubmit: async ({ value }) => {
      setError('')
      try {
        const identity = await signIn(value.email, value.password)
        session.set(identity)
        await navigate({ to: loginDestination(afterLogin, identity.user.role), replace: true })
      } catch (failure) {
        setError(failure instanceof Error ? failure.message : 'Could not sign in.')
      }
    },
  })
  return (
    <PageSection eyebrow="Account" title="Sign in" description="Use your Rent Cars account.">
      <form
        className="grid max-w-md gap-4"
        onSubmit={(event) => {
          event.preventDefault()
          void form.handleSubmit()
        }}
      >
        <form.Field name="email">
          {(field) => (
            <label>
              Email
              <input
                className="control"
                type="email"
                autoComplete="username"
                required
                value={field.state.value}
                onChange={(e) => field.handleChange(e.target.value)}
              />
            </label>
          )}
        </form.Field>
        <form.Field name="password">
          {(field) => (
            <label>
              Password
              <input
                className="control"
                type="password"
                autoComplete="current-password"
                required
                value={field.state.value}
                onChange={(e) => field.handleChange(e.target.value)}
              />
            </label>
          )}
        </form.Field>
        {error && <p role="alert">{error}</p>}
        <form.Subscribe selector={(state) => state.isSubmitting}>
          {(busy) => (
            <button type="submit" className="btn-primary" disabled={busy}>
              Sign in
            </button>
          )}
        </form.Subscribe>
      </form>
    </PageSection>
  )
}

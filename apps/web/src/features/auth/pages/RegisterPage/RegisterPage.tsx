import { useState } from 'react'
import { useForm } from '@tanstack/react-form'
import { Link } from '@tanstack/react-router'
import { PageSection } from '../../../../components/PageSection/PageSection'
import { registerAccount } from '../../auth-api'

const passwordHint = 'Use at least 12 characters and at most 72 UTF-8 bytes.'

export function RegisterPage() {
  const [created, setCreated] = useState(false)
  return (
    <PageSection eyebrow="Account" title="Create account" description="Register as a driver.">
      {created ? (
        <p role="status">Your account is ready. Sign in to continue.</p>
      ) : (
        <RegisterForm onCreated={() => setCreated(true)} />
      )}
      <Link className="mt-4 inline-block underline" to="/login">
        Back to sign in
      </Link>
    </PageSection>
  )
}

function RegisterForm({ onCreated }: { onCreated: () => void }) {
  const [error, setError] = useState('')
  const form = useForm({
    defaultValues: { email: '', password: '' },
    onSubmit: async ({ value }) => {
      if (new TextEncoder().encode(value.password).length > 72 || !value.password.trim()) {
        setError('Choose a nonblank password. ' + passwordHint)
        return
      }
      setError('')
      try {
        await registerAccount(value.email, value.password)
        onCreated()
      } catch (failure) {
        setError(failure instanceof Error ? failure.message : 'Could not create your account.')
      }
    },
  })
  return (
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
              maxLength={254}
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
              autoComplete="new-password"
              required
              minLength={12}
              maxLength={72}
              aria-describedby="register-password-hint"
              value={field.state.value}
              onChange={(e) => field.handleChange(e.target.value)}
            />
          </label>
        )}
      </form.Field>
      <p id="register-password-hint">{passwordHint}</p>
      {error && <p role="alert">{error}</p>}
      <form.Subscribe selector={(state) => state.isSubmitting}>
        {(busy) => (
          <button className="btn-primary" type="submit" disabled={busy}>
            Create account
          </button>
        )}
      </form.Subscribe>
    </form>
  )
}

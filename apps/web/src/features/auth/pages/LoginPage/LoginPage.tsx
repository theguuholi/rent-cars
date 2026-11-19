import { SubmitButton } from '../../../../components/SubmitButton/SubmitButton'
import { TextField } from '../../../../components/TextField/TextField'
import { useCurrentAction } from '../../../../routes/use-current-action'
import { useState } from 'react'
import { useForm } from '@tanstack/react-form'
import { Link, useNavigate, useLocation } from '@tanstack/react-router'
import { PageSection } from '../../../../components/PageSection/PageSection'
import { signIn } from '../../auth-api'
import { session } from '../../session'

import { loginDestination } from '../../access'
export function LoginPage() {
  const afterLogin = useLocation({ select: (location) => location.state.afterLogin })
  const [error, setError] = useState('')
  const begin = useCurrentAction()
  const navigate = useNavigate()
  const form = useForm({
    defaultValues: { email: '', password: '' },
    onSubmit: async ({ value }) => {
      const current = begin()
      setError('')
      try {
        const identity = await signIn(value.email, value.password)
        if (!current()) return
        session.set(identity)
        await navigate({ to: loginDestination(afterLogin, identity.user.role), replace: true })
      } catch (failure) {
        if (current()) setError(failure instanceof Error ? failure.message : 'Could not sign in.')
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
        <TextField
          form={form}
          name="email"
          type="email"
          autoComplete="username"
          required
          maxLength={null}
        />
        <TextField
          form={form}
          name="password"
          type="password"
          autoComplete="current-password"
          required
          maxLength={null}
        />
        {error && <p role="alert">{error}</p>}
        <SubmitButton form={form}>Sign in</SubmitButton>
      </form>
      <p className="mt-4">
        <Link className="underline" to="/register">
          Create an account
        </Link>
      </p>
      <p className="mt-2">
        <Link className="underline" to="/forgot-password">
          Forgot password?
        </Link>
      </p>
    </PageSection>
  )
}

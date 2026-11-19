import { useState } from 'react'
import { useForm } from '@tanstack/react-form'
import { Link, useLocation } from '@tanstack/react-router'
import { SubmitButton } from '../../../../components/SubmitButton/SubmitButton'
import { TextField } from '../../../../components/TextField/TextField'
import { PageSection } from '../../../../components/PageSection/PageSection'
import { resetPassword } from '../../auth-api'
import { passwordHint } from '../../password-schema'
import { resetPasswordSchema } from './reset-password-schema'

export function ResetPasswordPage() {
  const token = useLocation({
    select: (location) =>
      new URL(location.href, 'http://localhost').searchParams.get('token') ?? '',
  })
  const [updated, setUpdated] = useState(false)
  const [error, setError] = useState('')
  const form = useForm({
    defaultValues: { password: '', passwordConfirmation: '' },
    validators: { onSubmit: resetPasswordSchema },
    onSubmit: async ({ value }) => {
      const data = resetPasswordSchema.parse(value)
      setError('')
      try {
        await resetPassword({ token, ...data })
        setUpdated(true)
      } catch (failure) {
        setError(failure instanceof Error ? failure.message : 'Could not reset your password.')
      }
    },
  })

  return (
    <PageSection
      eyebrow="Account"
      title="Reset password"
      description="Choose a new password for your account."
    >
      {!token ? (
        <p role="alert">Reset link is invalid or expired. Request a new one.</p>
      ) : updated ? (
        <p role="status">Password updated. You can sign in now.</p>
      ) : (
        <form
          className="grid max-w-md gap-4"
          noValidate
          onSubmit={(event) => {
            event.preventDefault()
            void form.handleSubmit()
          }}
        >
          <TextField
            form={form}
            name="password"
            label="New password"
            type="password"
            autoComplete="new-password"
            required
            minLength={12}
            maxLength={72}
            aria-describedby="reset-password-hint"
          />
          <p id="reset-password-hint">{passwordHint}</p>
          <TextField
            form={form}
            name="passwordConfirmation"
            label="Confirm new password"
            type="password"
            autoComplete="new-password"
            required
            maxLength={72}
          />
          {error && <p role="alert">{error}</p>}
          <SubmitButton form={form}>Reset password</SubmitButton>
        </form>
      )}
      <Link className="mt-4 inline-block underline" to={updated ? '/login' : '/forgot-password'}>
        {updated ? 'Sign in' : 'Request a new link'}
      </Link>
    </PageSection>
  )
}

import { useState } from 'react'
import { useForm } from '@tanstack/react-form'
import { Link, useLocation } from '@tanstack/react-router'
import { PageSection } from '../../../../components/PageSection/PageSection'
import { resetPassword } from '../../auth-api'
import { passwordHint } from '../../password-schema'
import { parseResetPassword, type ResetPasswordErrors } from './reset-password-schema'

export function ResetPasswordPage() {
  const token = useLocation({
    select: (location) =>
      new URL(location.href, 'http://localhost').searchParams.get('token') ?? '',
  })
  const [updated, setUpdated] = useState(false)
  const [error, setError] = useState('')
  const [fieldErrors, setFieldErrors] = useState<ResetPasswordErrors>({})
  const form = useForm({
    defaultValues: { password: '', passwordConfirmation: '' },
    onSubmit: async ({ value }) => {
      const { data, errors } = parseResetPassword(value)
      setFieldErrors(errors)
      if (!data) return
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
          <form.Field name="password">
            {(field) => (
              <label>
                New password
                <input
                  className="control"
                  type="password"
                  autoComplete="new-password"
                  required
                  minLength={12}
                  maxLength={72}
                  aria-invalid={Boolean(fieldErrors.password)}
                  aria-describedby={
                    fieldErrors.password
                      ? 'reset-password-hint reset-password-error'
                      : 'reset-password-hint'
                  }
                  value={field.state.value}
                  onChange={(event) => field.handleChange(event.target.value)}
                />
                {fieldErrors.password && (
                  <span id="reset-password-error" role="alert">
                    {fieldErrors.password}
                  </span>
                )}
              </label>
            )}
          </form.Field>
          <p id="reset-password-hint">{passwordHint}</p>
          <form.Field name="passwordConfirmation">
            {(field) => (
              <label>
                Confirm new password
                <input
                  className="control"
                  type="password"
                  autoComplete="new-password"
                  required
                  aria-invalid={Boolean(fieldErrors.passwordConfirmation)}
                  aria-describedby={
                    fieldErrors.passwordConfirmation ? 'reset-confirmation-error' : undefined
                  }
                  value={field.state.value}
                  onChange={(event) => field.handleChange(event.target.value)}
                />
                {fieldErrors.passwordConfirmation && (
                  <span id="reset-confirmation-error" role="alert">
                    {fieldErrors.passwordConfirmation}
                  </span>
                )}
              </label>
            )}
          </form.Field>
          {error && <p role="alert">{error}</p>}
          <form.Subscribe selector={(state) => state.isSubmitting}>
            {(busy) => (
              <button className="btn-primary" type="submit" disabled={busy}>
                Reset password
              </button>
            )}
          </form.Subscribe>
        </form>
      )}
      <Link className="mt-4 inline-block underline" to={updated ? '/login' : '/forgot-password'}>
        {updated ? 'Sign in' : 'Request a new link'}
      </Link>
    </PageSection>
  )
}

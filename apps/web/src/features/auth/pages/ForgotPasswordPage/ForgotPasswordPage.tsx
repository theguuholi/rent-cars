import { useState } from 'react'
import { useForm } from '@tanstack/react-form'
import { Link } from '@tanstack/react-router'
import { PageSection } from '../../../../components/PageSection/PageSection'
import { requestPasswordReset } from '../../auth-api'

export function ForgotPasswordPage() {
  const [sent, setSent] = useState(false)
  const [error, setError] = useState('')
  const form = useForm({
    defaultValues: { email: '' },
    onSubmit: async ({ value }) => {
      setError('')
      try {
        await requestPasswordReset(value.email.trim().toLowerCase())
        setSent(true)
      } catch (failure) {
        setError(failure instanceof Error ? failure.message : 'Could not request a reset link.')
      }
    },
  })

  return (
    <PageSection
      eyebrow="Account"
      title="Forgot password"
      description="We will email a link if the account exists."
    >
      {sent ? (
        <p role="status">If the account exists, a reset link is on its way.</p>
      ) : (
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
                  autoComplete="email"
                  required
                  maxLength={254}
                  value={field.state.value}
                  onChange={(event) => field.handleChange(event.target.value)}
                />
              </label>
            )}
          </form.Field>
          {error && <p role="alert">{error}</p>}
          <form.Subscribe selector={(state) => state.isSubmitting}>
            {(busy) => (
              <button className="btn-primary" type="submit" disabled={busy}>
                Send reset link
              </button>
            )}
          </form.Subscribe>
        </form>
      )}
      <Link className="mt-4 inline-block underline" to="/login">
        Back to sign in
      </Link>
    </PageSection>
  )
}

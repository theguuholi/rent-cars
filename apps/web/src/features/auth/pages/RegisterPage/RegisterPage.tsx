import { useState } from 'react'
import { useForm } from '@tanstack/react-form'
import { Link } from '@tanstack/react-router'
import { PageSection } from '../../../../components/PageSection/PageSection'
import { registerAccount, type RegistrationInput } from '../../auth-api'

const passwordHint = 'Use at least 12 characters and at most 72 UTF-8 bytes.'
type FieldErrors = Partial<Record<keyof RegistrationInput, string>>

function validate(input: RegistrationInput): FieldErrors {
  const errors: FieldErrors = {}
  if (!input.firstName.trim() || input.firstName.trim().length > 80)
    errors.firstName = 'Enter a first name of up to 80 characters.'
  if (!input.lastName.trim() || input.lastName.trim().length > 80)
    errors.lastName = 'Enter a last name of up to 80 characters.'
  if (!/^[a-z0-9._-]{3,40}$/.test(input.userName.trim().toLowerCase()))
    errors.userName = 'Use 3–40 letters, numbers, dots, underscores or hyphens.'
  if (!/^[0-9]{11}$/.test(input.driveLicense.trim()))
    errors.driveLicense = 'Enter the 11-digit CNH registration number.'
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(input.email.trim()) || input.email.trim().length > 254)
    errors.email = 'Enter a valid email address.'
  if (
    !input.password.trim() ||
    input.password.length < 12 ||
    input.password.length > 72 ||
    new TextEncoder().encode(input.password).length > 72
  )
    errors.password = 'Choose a nonblank password. ' + passwordHint
  if (!input.passwordConfirmation || input.passwordConfirmation !== input.password)
    errors.passwordConfirmation = 'Passwords do not match.'
  return errors
}

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
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({})
  const form = useForm({
    defaultValues: {
      firstName: '',
      lastName: '',
      userName: '',
      driveLicense: '',
      email: '',
      password: '',
      passwordConfirmation: '',
    },
    onSubmit: async ({ value }) => {
      const errors = validate(value)
      setFieldErrors(errors)
      if (Object.keys(errors).length) return
      setError('')
      try {
        await registerAccount(value)
        onCreated()
      } catch (failure) {
        setError(failure instanceof Error ? failure.message : 'Could not create your account.')
      }
    },
  })
  return (
    <form
      className="grid max-w-md gap-4"
      noValidate
      onSubmit={(event) => {
        event.preventDefault()
        void form.handleSubmit()
      }}
    >
      <form.Field name="firstName">
        {(field) => (
          <label>
            First name
            <input
              className="control"
              type="text"
              autoComplete="given-name"
              required
              maxLength={80}
              aria-invalid={Boolean(fieldErrors.firstName)}
              aria-describedby={fieldErrors.firstName ? 'register-first-name-error' : undefined}
              value={field.state.value}
              onChange={(e) => field.handleChange(e.target.value)}
            />
            {fieldErrors.firstName && (
              <span id="register-first-name-error" role="alert">
                {fieldErrors.firstName}
              </span>
            )}
          </label>
        )}
      </form.Field>
      <form.Field name="lastName">
        {(field) => (
          <label>
            Last name
            <input
              className="control"
              type="text"
              autoComplete="family-name"
              required
              maxLength={80}
              aria-invalid={Boolean(fieldErrors.lastName)}
              aria-describedby={fieldErrors.lastName ? 'register-last-name-error' : undefined}
              value={field.state.value}
              onChange={(e) => field.handleChange(e.target.value)}
            />
            {fieldErrors.lastName && (
              <span id="register-last-name-error" role="alert">
                {fieldErrors.lastName}
              </span>
            )}
          </label>
        )}
      </form.Field>
      <form.Field name="userName">
        {(field) => (
          <label>
            Username
            <input
              className="control"
              type="text"
              autoComplete="username"
              required
              minLength={3}
              maxLength={40}
              aria-invalid={Boolean(fieldErrors.userName)}
              aria-describedby={fieldErrors.userName ? 'register-username-error' : undefined}
              value={field.state.value}
              onChange={(e) => field.handleChange(e.target.value)}
            />
            {fieldErrors.userName && (
              <span id="register-username-error" role="alert">
                {fieldErrors.userName}
              </span>
            )}
          </label>
        )}
      </form.Field>
      <form.Field name="driveLicense">
        {(field) => (
          <label>
            CNH number
            <input
              className="control"
              type="text"
              inputMode="numeric"
              autoComplete="off"
              required
              minLength={11}
              maxLength={11}
              aria-invalid={Boolean(fieldErrors.driveLicense)}
              aria-describedby={fieldErrors.driveLicense ? 'register-cnh-error' : undefined}
              value={field.state.value}
              onChange={(e) => field.handleChange(e.target.value)}
            />
            {fieldErrors.driveLicense && (
              <span id="register-cnh-error" role="alert">
                {fieldErrors.driveLicense}
              </span>
            )}
          </label>
        )}
      </form.Field>
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
              aria-invalid={Boolean(fieldErrors.email)}
              aria-describedby={fieldErrors.email ? 'register-email-error' : undefined}
              value={field.state.value}
              onChange={(e) => field.handleChange(e.target.value)}
            />
            {fieldErrors.email && (
              <span id="register-email-error" role="alert">
                {fieldErrors.email}
              </span>
            )}
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
              aria-invalid={Boolean(fieldErrors.password)}
              aria-describedby={
                fieldErrors.password
                  ? 'register-password-hint register-password-error'
                  : 'register-password-hint'
              }
              value={field.state.value}
              onChange={(e) => field.handleChange(e.target.value)}
            />
            {fieldErrors.password && (
              <span id="register-password-error" role="alert">
                {fieldErrors.password}
              </span>
            )}
          </label>
        )}
      </form.Field>
      <p id="register-password-hint">{passwordHint}</p>
      <form.Field name="passwordConfirmation">
        {(field) => (
          <label>
            Confirm password
            <input
              className="control"
              type="password"
              autoComplete="new-password"
              required
              maxLength={72}
              aria-invalid={Boolean(fieldErrors.passwordConfirmation)}
              aria-describedby={
                fieldErrors.passwordConfirmation ? 'register-confirmation-error' : undefined
              }
              value={field.state.value}
              onChange={(e) => field.handleChange(e.target.value)}
            />
            {fieldErrors.passwordConfirmation && (
              <span id="register-confirmation-error" role="alert">
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
            Create account
          </button>
        )}
      </form.Subscribe>
    </form>
  )
}

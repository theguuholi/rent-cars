import { useState } from 'react'
import { useForm } from '@tanstack/react-form'
import { Link } from '@tanstack/react-router'
import { TextField } from '../../../../components/TextField/TextField'
import { PageSection } from '../../../../components/PageSection/PageSection'
import { registerAccount } from '../../auth-api'
import { passwordHint, parseRegistration } from './registration-schema'

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
      const { data, errors } = parseRegistration(value)
      form.setErrorMap({ onSubmit: { fields: errors } })
      if (!data) return
      setError('')
      try {
        await registerAccount(data)
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
      <TextField form={form} name="firstName" autoComplete="given-name" required />
      <TextField form={form} name="lastName" autoComplete="family-name" required />
      <TextField
        form={form}
        name="userName"
        label="Username"
        autoComplete="username"
        required
        minLength={3}
        maxLength={40}
      />
      <TextField
        form={form}
        name="driveLicense"
        label="CNH number"
        inputMode="numeric"
        autoComplete="off"
        required
        minLength={11}
        maxLength={11}
      />
      <TextField
        form={form}
        name="email"
        type="email"
        autoComplete="email"
        required
        maxLength={254}
      />
      <TextField
        form={form}
        name="password"
        type="password"
        autoComplete="new-password"
        required
        minLength={12}
        maxLength={72}
        aria-describedby="register-password-hint"
      />
      <p id="register-password-hint">{passwordHint}</p>
      <TextField
        form={form}
        name="passwordConfirmation"
        label="Confirm password"
        type="password"
        autoComplete="new-password"
        required
        maxLength={72}
      />
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

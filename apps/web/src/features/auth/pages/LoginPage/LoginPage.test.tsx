import { act, fireEvent, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, test as scenario, vi } from 'vitest'
import { openCatalogRoute } from '../../../../test/open-catalog-route'
import { signIn } from '../../auth-api'
import { session, type Session } from '../../session'
vi.mock('../../auth-api')
const identity: Session = {
  accessToken: 'signed',
  user: { id: '1', email: 'admin@example.test', role: 'ADMIN' },
}
describe('LoginPage', () => {
  afterEach(() => {
    vi.resetAllMocks()
    vi.unstubAllGlobals()
    act(() => session.set(null))
  })
  scenario(
    'Given invalid credentials, when the visitor corrects them, then the verified account opens the fleet and can sign out',
    async () => {
      let finish!: (s: Session) => void
      vi.mocked(signIn)
        .mockRejectedValueOnce(new Error('Invalid email or password.'))
        .mockImplementationOnce(
          () =>
            new Promise((resolve) => {
              finish = resolve
            }),
        )
      const history = openCatalogRoute('/')
      fireEvent.click(await screen.findByRole('link', { name: 'Sign in' }))
      const email = await screen.findByLabelText('Email'),
        password = screen.getByLabelText('Password')
      expect(email).toHaveValue('')
      expect(password).toHaveValue('')
      expect(email).toHaveAttribute('type', 'email')
      expect(password).toHaveAttribute('type', 'password')
      expect(email).toHaveAttribute('autocomplete', 'username')
      expect(password).toHaveAttribute('autocomplete', 'current-password')
      expect(email).toBeRequired()
      expect(password).toBeRequired()
      expect(screen.getByText('Account')).toBeVisible()
      expect(screen.getByText('Use your Rent Cars account.')).toBeVisible()
      expect(document.title).toBe('Sign in | Rent Cars')
      fireEvent.change(email, { target: { value: identity.user.email } })
      fireEvent.change(password, { target: { value: 'incorrect' } })
      const submit = screen.getByRole('button', { name: 'Sign in' })
      fireEvent.click(submit)
      expect(await screen.findByRole('alert')).toHaveTextContent('Invalid email or password.')
      expect(session.read()).toBeNull()
      fireEvent.change(password, { target: { value: 'correct' } })
      fireEvent.click(submit)
      await waitFor(() => expect(submit).toBeDisabled())
      expect(screen.queryByRole('alert')).not.toBeInTheDocument()
      expect(signIn).toHaveBeenLastCalledWith(identity.user.email, 'correct')
      await act(async () => finish(identity))
      const logout = await screen.findByRole('button', { name: 'Sign out ' + identity.user.email })
      await waitFor(() => expect(history.location.pathname).toBe('/cars'))
      expect(history.length).toBe(2)
      fireEvent.click(logout)
      expect(session.read()).toBeNull()
      expect(await screen.findByRole('link', { name: 'Sign in' })).toBeVisible()
    },
  )
  scenario(
    'Given a non-Error rejection, when submitting credentials, then the form shows the fallback',
    async () => {
      vi.mocked(signIn).mockRejectedValue('offline')
      openCatalogRoute('/login')
      const button = await screen.findByRole('button', { name: 'Sign in' })
      fireEvent.submit(button.closest('form')!)
      expect(await screen.findByRole('alert')).toHaveTextContent('Could not sign in.')
    },
  )
})

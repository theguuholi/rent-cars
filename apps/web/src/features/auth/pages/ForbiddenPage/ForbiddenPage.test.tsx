import { screen } from '@testing-library/react'
import { describe, expect, test as scenario } from 'vitest'
import { adminSession, openCatalogRoute } from '../../../../test/open-catalog-route'
import { session } from '../../session'
describe('ForbiddenPage', () => {
  scenario(
    'Given a signed-in driver, when opening an admin page, then keep the account signed in and offer the public fleet without a catalog request',
    async () => {
      const driver = { ...adminSession, user: { ...adminSession.user, role: 'USER' as const } }
      const history = openCatalogRoute('/categories', undefined, driver)
      expect(await screen.findByRole('heading', { name: 'Access denied' })).toBeVisible()
      expect(screen.getByText('403')).toBeVisible()
      expect(screen.getByText('This page requires an administrator account.')).toBeVisible()
      expect(screen.getByRole('link', { name: 'Browse cars' })).toHaveAttribute('href', '/cars')
      expect(document.title).toBe('Access denied | Rent Cars')
      expect(history.location.pathname).toBe('/forbidden')
      expect(history.length).toBe(1)
      expect(session.read()).toBe(driver)
      expect(fetch).not.toHaveBeenCalled()
    },
  )
})

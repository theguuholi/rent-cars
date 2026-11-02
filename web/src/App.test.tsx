import { fireEvent, screen } from '@testing-library/react'
import { afterEach, expect, it, vi } from 'vitest'
import App from './App'
import { renderWithQuery } from './test/render-with-query'

afterEach(() => {
  vi.unstubAllGlobals()
})

it('shows the Rent Cars heading and opens the catalog', async () => {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(Response.json([])))

  renderWithQuery(<App />)

  expect(screen.getByRole('heading', { level: 1, name: 'Rent Cars' })).toBeInTheDocument()

  expect(await screen.findByText('No vehicle categories available.')).toBeInTheDocument()
})

it('switches between specifications and categories using the catalog navigation', async () => {
  vi.stubGlobal(
    'fetch',
    vi.fn().mockImplementation(async () => Response.json([])),
  )
  renderWithQuery(<App />)
  await screen.findByText('No vehicle categories available.')
  fireEvent.click(screen.getByRole('button', { name: 'Specifications' }))
  expect(await screen.findByText('No specifications yet.')).toBeVisible()
  fireEvent.click(screen.getByRole('button', { name: 'Categories' }))
  expect(await screen.findByText('No vehicle categories available.')).toBeVisible()
})

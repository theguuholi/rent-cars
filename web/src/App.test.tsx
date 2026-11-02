import { screen } from '@testing-library/react'
import { afterEach, expect, it, vi } from 'vitest'
import App from './App'
import { renderWithQuery } from './test/render-with-query'

afterEach(() => {
    vi.unstubAllGlobals()
})

it('shows the Rent Cars heading and opens the catalog', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(Response.json([])))

    renderWithQuery(<App />)

    expect(
        screen.getByRole('heading', { level: 1, name: 'Rent Cars' }),
    ).toBeInTheDocument()

    expect(
        await screen.findByText('No vehicle categories available.'),
    ).toBeInTheDocument()
})
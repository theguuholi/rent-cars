import { render, screen } from '@testing-library/react'
import {
  createMemoryHistory,
  createRootRoute,
  createRouter,
  RouterProvider,
} from '@tanstack/react-router'
import { describe, expect, test as scenario } from 'vitest'
import { car } from '../../../../test/car-fixtures'
import { CarCard } from './CarCard'

describe('CarCard', () => {
  scenario.each([
    [true, 'Available'],
    [false, 'Unavailable'],
  ] as const)(
    'Given a car with availability %s, when its card renders, then the detail link, daily price and %s status identify that car',
    async (available, status) => {
      const routeTree = createRootRoute({
        component: () => <CarCard car={{ ...car, available }} />,
      })
      const router = createRouter({
        routeTree,
        history: createMemoryHistory({ initialEntries: ['/'] }),
      })
      render(<RouterProvider router={router} />)
      expect(await screen.findByRole('link', { name: car.name })).toHaveAttribute(
        'href',
        `/cars/${car.id}`,
      )
      const link = screen.getByRole('link', { name: car.name })
      expect(link).toContainElement(screen.getByText(car.brand))
      expect(link).toContainElement(screen.getByText('R$ 150,50'))
      expect(link.querySelector('img')).not.toBeNull()
      expect(link.querySelector('button, a')).toBeNull()
      expect(screen.getByText(car.brand)).toBeVisible()
      expect(screen.getByText(car.licensePlate)).toBeVisible()
      expect(screen.getByText('R$ 150,50')).toBeVisible()
      expect(screen.getByText(status)).toHaveAttribute('data-available', String(available))
      expect(screen.queryByRole('img')).not.toBeInTheDocument()
    },
  )
})

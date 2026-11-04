import { createRootRouteWithContext, createRoute } from '@tanstack/react-router'
import { SessionLayout } from '../features/auth/components/SessionLayout/SessionLayout'
import type { session } from '../features/auth/session'
import { NotFoundPage } from '../pages/NotFoundPage/NotFoundPage'

declare module '@tanstack/react-router' {
  interface StaticDataRouteOption {
    title?: string
  }
}

export const rootRoute = createRootRouteWithContext<{ session: typeof session }>()({
  component: SessionLayout,
  notFoundComponent: NotFoundPage,
})
export const homeRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/',
})

export const unknownRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '$',
  staticData: { title: 'Page not found' },
  component: NotFoundPage,
})

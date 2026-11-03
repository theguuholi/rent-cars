import { createRootRoute, createRoute } from '@tanstack/react-router'
import { Layout } from '../App'
import { NotFoundPage } from '../NotFoundPage'

declare module '@tanstack/react-router' {
  interface StaticDataRouteOption {
    title?: string
  }
}

export const rootRoute = createRootRoute({ component: Layout, notFoundComponent: NotFoundPage })
export const homeRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/',
  component: () => null,
})

export const unknownRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '$',
  staticData: { title: 'Page not found' },
  component: NotFoundPage,
})

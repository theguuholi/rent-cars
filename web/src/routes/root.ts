import { createRootRoute, createRoute } from '@tanstack/react-router'
import { Layout } from '../App'
import { NotFoundPage } from '../NotFoundPage'

export const rootRoute = createRootRoute({ component: Layout, notFoundComponent: NotFoundPage })
export const homeRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/',
  component: () => null,
})

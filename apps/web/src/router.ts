import { createRouter, type RouterHistory } from '@tanstack/react-router'
import { session } from './features/auth/session'
import { avatarRoute, loginRoute, registerRoute, forbiddenRoute } from './features/auth/routes'
import { carRoutes } from './features/cars/routes'
import { categoriesRoute } from './features/categories/routes'
import { specificationRoutes } from './features/specifications/routes'
import { rootRoute, homeRoute, unknownRoute } from './routes/root'

const tree = rootRoute.addChildren([
  homeRoute,
  avatarRoute,
  loginRoute,
  registerRoute,
  forbiddenRoute,
  categoriesRoute,
  ...carRoutes,
  ...specificationRoutes,
  unknownRoute,
])

export function createAppRouter(history?: RouterHistory) {
  return createRouter({ routeTree: tree, history, context: { session } })
}
declare module '@tanstack/react-router' {
  interface Register {
    router: ReturnType<typeof createAppRouter>
  }
}

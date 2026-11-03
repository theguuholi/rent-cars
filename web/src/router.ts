import { createRouter, type RouterHistory } from '@tanstack/react-router'
import { categoriesRoute } from './features/categories/routes'
import { specificationRoutes } from './features/specifications/routes'
import { rootRoute, homeRoute, unknownRoute } from './routes/root'

const tree = rootRoute.addChildren([
  homeRoute,
  categoriesRoute,
  ...specificationRoutes,
  unknownRoute,
])

export function createAppRouter(history?: RouterHistory) {
  return createRouter({ routeTree: tree, history })
}
declare module '@tanstack/react-router' {
  interface Register {
    router: ReturnType<typeof createAppRouter>
  }
}

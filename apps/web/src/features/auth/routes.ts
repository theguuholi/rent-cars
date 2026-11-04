import { createRoute } from '@tanstack/react-router'
import { rootRoute } from '../../routes/root'
import { LoginPage } from './pages/LoginPage/LoginPage'

export const loginRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/login',
  staticData: { title: 'Sign in' },
  component: LoginPage,
})

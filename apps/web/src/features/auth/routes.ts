import { AvatarPage } from './pages/AvatarPage/AvatarPage'
import { RegisterPage } from './pages/RegisterPage/RegisterPage'
import { ForbiddenPage } from './pages/ForbiddenPage/ForbiddenPage'
import { createRoute, redirect } from '@tanstack/react-router'
import { rootRoute } from '../../routes/root'
import { LoginPage } from './pages/LoginPage/LoginPage'

export const loginRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/login',
  staticData: { title: 'Sign in' },
  component: LoginPage,
})

export const forbiddenRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/forbidden',
  staticData: { title: 'Access denied' },
  component: ForbiddenPage,
})

export const registerRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/register',
  staticData: { title: 'Create account' },
  component: RegisterPage,
})

export const avatarRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/account/avatar',
  staticData: { title: 'My avatar' },
  beforeLoad: ({ context }) => {
    if (!context.session.read()) throw redirect({ to: '/login', replace: true })
  },
  component: AvatarPage,
})

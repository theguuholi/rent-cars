import { RegisterPage } from './pages/RegisterPage/RegisterPage'
import { ForbiddenPage } from './pages/ForbiddenPage/ForbiddenPage'
import { createRoute } from '@tanstack/react-router'
import { rootRoute } from '../../routes/root'
import { LoginPage } from './pages/LoginPage/LoginPage'
import { ForgotPasswordPage } from './pages/ForgotPasswordPage/ForgotPasswordPage'
import { ResetPasswordPage } from './pages/ResetPasswordPage/ResetPasswordPage'

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

export const forgotPasswordRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/forgot-password',
  staticData: { title: 'Forgot password' },
  component: ForgotPasswordPage,
})

export const resetPasswordRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/reset-password',
  staticData: { title: 'Reset password' },
  component: ResetPasswordPage,
})

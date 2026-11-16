import { createRoute } from '@tanstack/react-router'
import { requireDriver } from '../auth/access'
import { rootRoute } from '../../routes/root'
import { MyRentalsPage } from './pages/MyRentalsPage/MyRentalsPage'

export const rentalsRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/rentals',
  beforeLoad: requireDriver,
  staticData: { title: 'My rentals' },
  component: MyRentalsPage,
})

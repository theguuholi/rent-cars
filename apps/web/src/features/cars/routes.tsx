import { requireAdmin } from '../auth/access'
import { createRoute } from '@tanstack/react-router'
import { rootRoute } from '../../routes/root'
import { CarsPage } from './pages/CarsPage/CarsPage'
import { CarRecord } from './pages/CarRecord/CarRecord'
import { CarFormPage } from './pages/CarFormPage/CarFormPage'
import type { CarSearch } from './cars-api'

const list = createRoute({
  getParentRoute: () => rootRoute,
  path: '/cars',
  validateSearch: (search: Record<string, unknown>): Partial<CarSearch> => {
    const validated: Partial<CarSearch> = {}
    if (typeof search.name === 'string' && search.name) validated.name = search.name
    if (typeof search.brand === 'string' && search.brand) validated.brand = search.brand
    if (typeof search.category === 'string' && search.category) validated.category = search.category
    if (
      search.page !== undefined &&
      Number.isInteger(Number(search.page)) &&
      Number(search.page) >= 0
    )
      validated.page = Number(search.page)
    if (search.size !== undefined && [1, 6, 12, 24].includes(Number(search.size)))
      validated.size = Number(search.size)
    if (
      search.sort !== undefined &&
      ['name,asc', 'name,desc', 'brand,asc', 'brand,desc'].includes(String(search.sort))
    )
      validated.sort = String(search.sort)
    return validated
  },
  staticData: { title: 'Cars' },
  component: CarsPage,
})
const create = createRoute({
  getParentRoute: () => rootRoute,
  path: '/cars/new',
  beforeLoad: requireAdmin,
  staticData: { title: 'New car' },
  component: () => <CarFormPage />,
})
const detail = createRoute({
  getParentRoute: () => rootRoute,
  path: '/cars/$id',
  staticData: { title: 'Car details' },
  component: () => <CarRecord id={detail.useParams().id} mode="detail" />,
})
const edit = createRoute({
  getParentRoute: () => rootRoute,
  path: '/cars/$id/edit',
  beforeLoad: requireAdmin,
  staticData: { title: 'Edit car' },
  component: () => <CarRecord id={edit.useParams().id} mode="edit" />,
})
const remove = createRoute({
  getParentRoute: () => rootRoute,
  path: '/cars/$id/delete',
  beforeLoad: requireAdmin,
  staticData: { title: 'Delete car' },
  component: () => <CarRecord id={remove.useParams().id} mode="delete" />,
})
export const carRoutes = [list, create, detail, edit, remove] as const
